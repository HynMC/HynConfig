package io.github.hynmc.hynconfig.internal.yaml;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.DumperOptions.FlowStyle;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;
import org.yaml.snakeyaml.representer.Representer;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class YamlEngine {

    private final Yaml yaml;

    public YamlEngine(int indent) {
        LoaderOptions loaderOptions = new LoaderOptions();
        loaderOptions.setAllowDuplicateKeys(false);
        loaderOptions.setMaxAliasesForCollections(50);
        loaderOptions.setNestingDepthLimit(50);
        loaderOptions.setCodePointLimit(3 * 1024 * 1024);
        loaderOptions.setProcessComments(true);

        DumperOptions dumperOptions = new DumperOptions();
        dumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        dumperOptions.setPrettyFlow(true);
        dumperOptions.setIndent(indent);
        dumperOptions.setIndicatorIndent(0);
        dumperOptions.setProcessComments(true);

        this.yaml = new Yaml(
                new SafeConstructor(loaderOptions),
                new Representer(dumperOptions),
                dumperOptions,
                loaderOptions
        );
    }

    public Map<String, Object> load(String content) {
        Object loaded = yaml.load(content);

        if (loaded == null) {
            return new LinkedHashMap<>();
        }

        if (!(loaded instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("YAML document root must be a mapping");
        }

        return normalizeMap(map, new IdentityHashMap<>());
    }

    public String dump(Map<String, Object> data) {
        return yaml.dump(data);
    }

    /** Updates existing scalar values in place so comments, blank lines, and line endings survive. */
    public String updatePreservingFormatting(String source, Map<String, Object> data, List<String> changedPaths) {
        Node root = yaml.compose(new StringReader(source));
        if (root == null) {
            if (source.isEmpty()) {
                return yaml.dump(data);
            }
            String lineBreak = source.contains("\r\n") ? "\r\n" : "\n";
            String prefix = source.endsWith("\n") || source.endsWith("\r") ? source : source + lineBreak;
            String serialized = yaml.dump(data).replace("\r\n", "\n").replace("\n", lineBreak);
            return prefix + serialized;
        }

        List<Replacement> replacements = new ArrayList<>();
        Map<MappingNode, Map<String, Object>> insertions = new IdentityHashMap<>();
        for (String path : changedPaths) {
            String[] parts = path.split("\\.");
            Node node = findNode(root, parts);
            Object value = valueAt(data, parts);
            if (node == null) {
                addInsertion(root, parts, value, insertions);
            } else {
                collectUpdates(node, value, path, replacements, insertions);
            }
        }

        List<Map.Entry<MappingNode, Map<String, Object>>> insertionEntries = new ArrayList<>(insertions.entrySet());
        insertionEntries.sort(Comparator.comparingInt(entry -> entry.getKey().getStartMark().getColumn()));
        for (Map.Entry<MappingNode, Map<String, Object>> entry : insertionEntries) {
            MappingNode mapping = entry.getKey();
            String lineBreak = source.contains("\r\n") ? "\r\n" : "\n";
            String fragment = yaml.dump(entry.getValue()).stripTrailing()
                    .replace("\r\n", "\n").replace("\n", lineBreak);
            String indentation = " ".repeat(mapping.getStartMark().getColumn());
            fragment = indentation + fragment.replace(lineBreak, lineBreak + indentation);
            int offset = mapping.getEndMark().getIndex();
            if (!mapping.getValue().isEmpty()) {
                offset = lastContentNode(mapping).getEndMark().getIndex();
                while (offset < source.length() && source.charAt(offset) != '\n' && source.charAt(offset) != '\r') {
                    offset++;
                }
            }
            boolean startsAtLineBoundary = offset == 0 || source.charAt(offset - 1) == '\n' || source.charAt(offset - 1) == '\r';
            boolean endsAtLineBoundary = offset == source.length() || source.charAt(offset) == '\n' || source.charAt(offset) == '\r';
            String insertion = (startsAtLineBoundary ? "" : lineBreak) + fragment + (endsAtLineBoundary ? "" : lineBreak);
            replacements.add(new Replacement(offset, offset, insertion));
        }

        replacements.sort(Comparator.comparingInt(Replacement::start).reversed());
        StringBuilder updated = new StringBuilder(source);
        for (Replacement replacement : replacements) {
            updated.replace(replacement.start(), replacement.end(), replacement.value());
        }
        return updated.toString();
    }

    private Node findNode(Node root, String[] path) {
        Node current = root;
        for (String part : path) {
            if (!(current instanceof MappingNode mapping)) {
                return null;
            }
            Node next = null;
            for (NodeTuple tuple : mapping.getValue()) {
                if (tuple.getKeyNode() instanceof ScalarNode key && part.equals(key.getValue())) {
                    next = tuple.getValueNode();
                    break;
                }
            }
            current = next;
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    private Object valueAt(Map<String, Object> data, String[] path) {
        Object current = data;
        for (String part : path) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(part);
        }
        return current;
    }

    private void addInsertion(Node root, String[] path, Object value,
                              Map<MappingNode, Map<String, Object>> insertions) {
        int parentLength = path.length - 1;
        MappingNode parent = null;
        int existingPrefixLength = parentLength;
        while (existingPrefixLength >= 0) {
            Node candidate = findNode(root, java.util.Arrays.copyOf(path, existingPrefixLength));
            if (candidate instanceof MappingNode mapping) {
                parent = mapping;
                break;
            }
            existingPrefixLength--;
        }
        if (parent == null) {
            throw new IllegalArgumentException("Cannot add configuration path: " + String.join(".", path));
        }

        addToInsertion(parent, path, existingPrefixLength, value, insertions);
    }

    private void addToInsertion(MappingNode parent, String[] path, int prefixLength, Object value,
                                Map<MappingNode, Map<String, Object>> insertions) {
        if (parent.getFlowStyle() == FlowStyle.FLOW) {
            throw new IllegalArgumentException(
                    "Adding entries to flow-style YAML mappings cannot preserve the original formatting"
            );
        }

        Map<String, Object> fragment = insertions.computeIfAbsent(parent, ignored -> new LinkedHashMap<>());
        Map<String, Object> current = fragment;
        for (int i = prefixLength; i < path.length - 1; i++) {
            Object child = current.get(path[i]);
            if (!(child instanceof Map<?, ?>)) {
                Map<String, Object> nested = new LinkedHashMap<>();
                current.put(path[i], nested);
                current = nested;
            } else {
                @SuppressWarnings("unchecked")
                Map<String, Object> nested = (Map<String, Object>) child;
                current = nested;
            }
        }
        current.put(path[path.length - 1], value);
    }

    private void collectUpdates(Node node, Object value, String path, List<Replacement> replacements,
                                Map<MappingNode, Map<String, Object>> insertions) {
        if (node instanceof ScalarNode) {
            String serialized = yaml.dump(value);
            if (serialized.endsWith("\r\n")) {
                serialized = serialized.substring(0, serialized.length() - 2);
            } else if (serialized.endsWith("\n")) {
                serialized = serialized.substring(0, serialized.length() - 1);
            }
            if (serialized.contains("\n") || serialized.contains("\r")) {
                throw new IllegalArgumentException("Multiline scalar updates cannot preserve the original layout: " + path);
            }
            replacements.add(new Replacement(node.getStartMark().getIndex(), node.getEndMark().getIndex(), serialized));
            return;
        }

        if (node instanceof MappingNode mapping && value instanceof Map<?, ?> updated) {
            Map<String, Node> existing = new LinkedHashMap<>();
            for (NodeTuple tuple : mapping.getValue()) {
                if (!(tuple.getKeyNode() instanceof ScalarNode key)) {
                    throw new IllegalArgumentException("YAML map keys must be strings");
                }
                existing.put(key.getValue(), tuple.getValueNode());
            }
            for (Map.Entry<String, Node> entry : existing.entrySet()) {
                if (!updated.containsKey(entry.getKey())) {
                    throw new IllegalArgumentException("Removing mapping entries cannot preserve their comments: " + path);
                }
                collectUpdates(entry.getValue(), updated.get(entry.getKey()), path + "." + entry.getKey(),
                        replacements, insertions);
            }
            for (Map.Entry<?, ?> entry : updated.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    throw new IllegalArgumentException("YAML map keys must be strings");
                }
                if (!existing.containsKey(key)) {
                    addToInsertion(mapping, new String[]{key}, 0, entry.getValue(), insertions);
                }
            }
            return;
        }

        if (node instanceof SequenceNode sequence && value instanceof List<?> updated) {
            if (sequence.getValue().size() != updated.size()) {
                throw new IllegalArgumentException("Changing list size cannot preserve its original layout: " + path);
            }
            for (int i = 0; i < updated.size(); i++) {
                collectUpdates(sequence.getValue().get(i), updated.get(i), path + "[" + i + "]",
                        replacements, insertions);
            }
            return;
        }

        throw new IllegalArgumentException("Cannot preserve YAML formatting for this value change: " + path);
    }

    private Node lastContentNode(Node node) {
        if (node instanceof MappingNode mapping && !mapping.getValue().isEmpty()) {
            return lastContentNode(mapping.getValue().get(mapping.getValue().size() - 1).getValueNode());
        }
        if (node instanceof SequenceNode sequence && !sequence.getValue().isEmpty()) {
            return lastContentNode(sequence.getValue().get(sequence.getValue().size() - 1));
        }
        return node;
    }

    private record Replacement(int start, int end, String value) {}

    private Map<String, Object> normalizeMap(Map<?, ?> source, IdentityHashMap<Object, Boolean> ancestors) {
        if (ancestors.put(source, Boolean.TRUE) != null) {
            throw new IllegalArgumentException("Recursive YAML aliases are not supported");
        }

        try {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : source.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    throw new IllegalArgumentException("YAML map keys must be strings");
                }
                result.put(key, normalizeValue(entry.getValue(), ancestors));
            }
            return result;
        } finally {
            ancestors.remove(source);
        }
    }

    private Object normalizeValue(Object value, IdentityHashMap<Object, Boolean> ancestors) {
        if (value instanceof Map<?, ?> map) {
            return normalizeMap(map, ancestors);
        }
        if (value instanceof Iterable<?> iterable) {
            if (ancestors.put(value, Boolean.TRUE) != null) {
                throw new IllegalArgumentException("Recursive YAML aliases are not supported");
            }
            try {
                java.util.List<Object> result = new java.util.ArrayList<>();
                for (Object item : iterable) {
                    result.add(normalizeValue(item, ancestors));
                }
                return result;
            } finally {
                ancestors.remove(value);
            }
        }
        return value;
    }
}
