package io.github.hynmc.hynconfig.config;

import io.github.hynmc.hynconfig.exception.ConfigLoadException;
import io.github.hynmc.hynconfig.exception.ConfigSaveException;
import io.github.hynmc.hynconfig.exception.ConfigTypeException;
import io.github.hynmc.hynconfig.internal.yaml.YamlEngine;
import io.github.hynmc.hynconfig.key.ConfigKey;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractList;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Reads, updates, and saves one YAML configuration file.
 *
 * <p>Paths use dots to address nested mappings (for example, {@code server.port}).
 * Saving patches supported changes into the original text to retain comments and layout.
 */
public class YamlConfig {

    private final Path path;
    private final boolean autoCreate;

    private final YamlEngine engine;
    private Map<String, Object> data = new LinkedHashMap<>();
    private String source = "";
    private final Set<String> changedPaths = new LinkedHashSet<>();

    /**
     * Creates a configuration from a builder.
     *
     * @param builder validated configuration builder
     */
    private YamlConfig(Builder builder) {
        this.path = builder.path;
        this.autoCreate = builder.autoCreate;
        this.engine = new YamlEngine(builder.indent);
    }

    /**
     * Creates a builder for a standalone YAML configuration.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /** Configures a standalone YAML file. */
    public static class Builder {

        private Path path;
        private boolean autoCreate = true;
        private int indent = 2;

        /** Creates a builder with automatic file creation and two-space indentation enabled. */
        public Builder() {}

        /**
         * Sets the file to read and write.
         *
         * @param path configuration file path
         * @return this builder
         */
        public Builder path(Path path) {
            this.path = path;
            return this;
        }

        /**
         * Controls whether missing files are created by {@link YamlConfig#load()} and {@link YamlConfig#save()}.
         * Defaults to {@code true}.
         *
         * @param autoCreate {@code true} to create missing files
         * @return this builder
         */
        public Builder autoCreate(boolean autoCreate) {
            this.autoCreate = autoCreate;
            return this;
        }

        /**
         * Sets the indentation width used for newly written YAML content. Defaults to two spaces.
         *
         * @param indent indentation width; must be positive
         * @return this builder
         */
        public Builder indent(int indent) {
            this.indent = indent;
            return this;
        }

        /**
         * Builds the configuration.
         *
         * @return a new configuration
         * @throws IllegalStateException if no path was supplied
         * @throws IllegalArgumentException if indentation is less than one
         */
        public YamlConfig build() {
            if (this.path == null) {
                throw new IllegalStateException("path is null");
            }

            if (indent < 1) {
                throw new IllegalArgumentException("indent must be greater than 0");
            }

            return new YamlConfig(this);
        }
    }

    /**
     * Gets a typed setting, returning its declared default when the path is missing.
     * A returned default is not added to the file; use {@link #applyDefaults(ConfigKey[])} to do that.
     *
     * @param key typed setting key
     * @param <T> setting value type
     * @return the stored value, its default, or {@code null} if absent and without a default
     * @throws ConfigTypeException if the stored value is null when non-nullable or has the wrong type
     */
    public <T> T get(ConfigKey<T> key) {
        if (!contains(key.path())) {
            return key.hasDefaultValue() ? key.defaultValue() : null;
        }

        Object value = get(key.path());

        if (value == null) {
            if (key.isNullable()) {
                return null;
            }

            throw new ConfigTypeException(
                    "Configuration value cannot be null: " + key.path()
            );
        }

        if (!key.type().isInstance(value)) {
            Object convertedValue = convertNumericValue(key.type(), value);
            if (convertedValue != null) {
                return key.type().cast(convertedValue);
            }

            throw new ConfigTypeException(
                    "Invalid type for configuration value: " + key.path() +
                            ", expected: " + key.type().getName() +
                            ", actual: " + value.getClass().getName()
            );
        }

        return key.type().cast(value);
    }

    /**
     * Sets a typed setting in memory.
     *
     * @param key typed setting key
     * @param value value to store
     * @param <T> setting value type
     * @throws ConfigTypeException if the value violates the key's nullability or type
     */
    public <T> void set(ConfigKey<T> key, T value) {
        if (value == null && !key.isNullable()) {
            throw new ConfigTypeException(
                    "Configuration value cannot be null: " + key.path()
            );
        }

        if (value != null && !key.type().isInstance(value)) {
            throw new ConfigTypeException(
                    "Invalid value type for configuration key: " + key.path()
            );
        }

        set(key.path(), value);
    }

    /**
     * Checks whether a typed setting path exists, including explicitly null values.
     *
     * @param key setting key
     * @return {@code true} if the path is present
     */
    public boolean contains(ConfigKey<?> key) {
        return contains(key.path());
    }

    /**
     * Adds declared defaults for missing keys to this in-memory configuration.
     * Existing values are left unchanged. Call {@link #save()} to persist inserted defaults.
     *
     * @param keys keys whose declared defaults should be applied
     * @throws IllegalArgumentException if the array or any key is {@code null}
     */
    public void applyDefaults(ConfigKey<?>... keys) {
        if (keys == null) {
            throw new IllegalArgumentException("keys cannot be null");
        }
        for (ConfigKey<?> key : keys) {
            if (key == null) {
                throw new IllegalArgumentException("keys cannot contain null");
            }
            if (!key.hasDefaultValue() || contains(key)) {
                continue;
            }
            set(key.path(), key.defaultValue());
        }
    }

    /**
     * Loads this file into memory. If automatic creation is enabled, a missing file is created empty.
     * This discards unsaved in-memory changes.
     *
     * @throws ConfigLoadException if the file is missing while automatic creation is disabled, or YAML is invalid
     */
    public void load() {
        try {
            if (autoCreate && Files.notExists(path)) {
                Path parent = path.getParent();

                if (parent != null) {
                    Files.createDirectories(parent);
                }

                Files.createFile(path);
            }

            if (Files.notExists(path)) {
                throw new IOException("Configuration file does not exist and autoCreate is disabled");
            }

            String content = Files.readString(path, StandardCharsets.UTF_8);
            this.data = engine.load(content);
            this.source = content;
            this.changedPaths.clear();

        } catch (IOException | RuntimeException exception) {
            throw new ConfigLoadException(
                    "Failed to load configuration: " + path,
                    exception
            );
        }
    }

    /**
     * Saves in-memory changes to this file. Supported edits retain existing comments, blank lines,
     * inline comments, and line endings. Unsupported structural edits fail without rewriting the file.
     *
     * @throws ConfigSaveException if the file cannot be written or the requested edit cannot preserve formatting
     */
    public void save() {
        try {
            if (!autoCreate && Files.notExists(path)) {
                throw new IOException("Configuration file does not exist and autoCreate is disabled");
            }

            Path parent = path.getParent();

            if (parent != null) {
                Files.createDirectories(parent);
            }

            if (changedPaths.isEmpty()) {
                if (Files.notExists(path)) {
                    Files.writeString(path, source, StandardCharsets.UTF_8);
                }
                return;
            }

            String content = engine.updatePreservingFormatting(source, data, List.copyOf(changedPaths));
            Files.writeString(path, content, StandardCharsets.UTF_8);
            source = content;
            changedPaths.clear();

        } catch (IOException | RuntimeException exception) {
            throw new ConfigSaveException(
                    "Failed to save configuration: " + path,
                    exception
            );
        }
    }

    /**
     * Gets a value by a dotted YAML path.
     * Maps and lists are live views; mutating them marks the containing path as changed.
     *
     * @param key dotted path such as {@code server.port}
     * @return the value, a tracked map/list view, or {@code null} if absent
     * @throws IllegalArgumentException if the path is null, blank, or contains an empty segment
     */
    public Object get(String key) {
        List<String> parts = splitPath(key);
        Map<String, Object> current = data;
        for (int i = 0; i < parts.size() - 1; i++) {
            Object child = current.get(parts.get(i));
            if (!(child instanceof Map<?, ?> childMap)) {
                return null;
            }
            current = castMap(childMap);
        }
        return trackedValue(current.get(parts.get(parts.size() - 1)), key);
    }

    /**
     * Sets a value by a dotted YAML path in memory.
     *
     * @param key dotted path such as {@code server.port}
     * @param value YAML-compatible scalar, mapping, sequence, or null value
     * @throws IllegalArgumentException if the path is invalid, conflicts with a scalar, or contains non-string map keys
     */
    public void set(String key, Object value) {
        List<String> parts = splitPath(key);
        Map<String, Object> current = data;
        for (int i = 0; i < parts.size() - 1; i++) {
            String part = parts.get(i);
            Object child = current.get(part);
            if (child == null) {
                Map<String, Object> nested = new LinkedHashMap<>();
                current.put(part, nested);
                current = nested;
            } else if (child instanceof Map<?, ?> childMap) {
                current = castMap(childMap);
            } else {
                throw new IllegalArgumentException("Configuration path conflicts with a scalar value: " + key);
            }
        }
        current.put(parts.get(parts.size() - 1), normalizeValue(value, new IdentityHashMap<>()));
        trackChangedPath(String.join(".", parts));
    }

    /**
     * Checks whether a dotted YAML path exists, including explicitly null values.
     *
     * @param key dotted path such as {@code server.port}
     * @return {@code true} if the path is present
     * @throws IllegalArgumentException if the path is null, blank, or contains an empty segment
     */
    public boolean contains(String key) {
        List<String> parts = splitPath(key);
        Map<String, Object> current = data;
        for (int i = 0; i < parts.size() - 1; i++) {
            Object child = current.get(parts.get(i));
            if (!(child instanceof Map<?, ?> childMap)) {
                return false;
            }
            current = castMap(childMap);
        }
        return current.containsKey(parts.get(parts.size() - 1));
    }

    /**
     * Returns the file path used by this configuration.
     *
     * @return configuration file path
     */
    public Path getPath() {
        return path;
    }

    private static List<String> splitPath(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("Configuration key path cannot be null or blank");
        }
        String[] parts = path.split("\\.", -1);
        for (String part : parts) {
            if (part.isBlank()) {
                throw new IllegalArgumentException("Configuration key path contains an empty segment: " + path);
            }
        }
        return List.of(parts);
    }

    private static Map<String, Object> castMap(Map<?, ?> map) {
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) map;
        return result;
    }

    private static List<Object> castList(List<?> list) {
        @SuppressWarnings("unchecked")
        List<Object> result = (List<Object>) list;
        return result;
    }

    private static Object convertNumericValue(Class<?> targetType, Object value) {
        if (!(value instanceof Number number)) {
            return null;
        }

        if (targetType == Long.class && value instanceof Integer) {
            return number.longValue();
        }

        if (targetType == Double.class && value instanceof Integer) {
            return number.doubleValue();
        }

        if (targetType == Double.class && value instanceof Long longValue) {
            double converted = number.doubleValue();
            if (new BigDecimal(converted).compareTo(BigDecimal.valueOf(longValue)) == 0) {
                return converted;
            }
        }

        if (targetType == Double.class && value instanceof Float) {
            return number.doubleValue();
        }

        if (targetType == Float.class && value instanceof Integer integerValue) {
            float converted = integerValue.floatValue();
            if (new BigDecimal((double) converted).compareTo(BigDecimal.valueOf(integerValue)) == 0) {
                return converted;
            }
        }

        if (targetType == Float.class && value instanceof Long longValue) {
            float converted = longValue.floatValue();
            if (Float.isFinite(converted)
                    && new BigDecimal((double) converted).compareTo(BigDecimal.valueOf(longValue)) == 0) {
                return converted;
            }
        }

        if (targetType == Float.class && value instanceof Double doubleValue) {
            float converted = doubleValue.floatValue();
            if (!Double.isFinite(doubleValue)
                    || (Float.isFinite(converted) && (doubleValue == 0.0d || converted != 0.0f))) {
                return converted;
            }
        }

        return null;
    }

    private Object trackedValue(Object value, String changedPath) {
        if (value instanceof Map<?, ?> map) {
            return new TrackedMap(castMap(map), changedPath);
        }
        if (value instanceof List<?> list) {
            return new TrackedList(castList(list), changedPath);
        }
        return value;
    }

    private final class TrackedMap extends AbstractMap<String, Object> {
        private final Map<String, Object> delegate;
        private final String changedPath;

        private TrackedMap(Map<String, Object> delegate, String changedPath) {
            this.delegate = delegate;
            this.changedPath = changedPath;
        }

        @Override
        public Object get(Object key) {
            return trackedValue(delegate.get(key), changedPath);
        }

        @Override
        public boolean containsKey(Object key) {
            return delegate.containsKey(key);
        }

        @Override
        public Object put(String key, Object value) {
            if (key == null) {
                throw new IllegalArgumentException("YAML map keys must be strings");
            }
            Object previous = delegate.put(key, normalizeValue(value, new IdentityHashMap<>()));
            trackChangedPath(changedPath);
            return trackedValue(previous, changedPath);
        }

        @Override
        public Object remove(Object key) {
            if (!delegate.containsKey(key)) {
                return null;
            }
            Object previous = delegate.remove(key);
            trackChangedPath(changedPath);
            return trackedValue(previous, changedPath);
        }

        @Override
        public void clear() {
            if (!delegate.isEmpty()) {
                delegate.clear();
                trackChangedPath(changedPath);
            }
        }

        @Override
        public Set<Entry<String, Object>> entrySet() {
            return new AbstractSet<>() {
                @Override
                public Iterator<Entry<String, Object>> iterator() {
                    Iterator<String> keys = new ArrayList<>(delegate.keySet()).iterator();
                    return new Iterator<>() {
                        private String currentKey;
                        private boolean removable;

                        @Override
                        public boolean hasNext() {
                            return keys.hasNext();
                        }

                        @Override
                        public Entry<String, Object> next() {
                            currentKey = keys.next();
                            removable = true;
                            String entryKey = currentKey;
                            return new SimpleEntry<>(entryKey, trackedValue(delegate.get(entryKey), changedPath)) {
                                @Override
                                public Object setValue(Object value) {
                                    Object normalized = normalizeValue(value, new IdentityHashMap<>());
                                    Object previous = delegate.put(entryKey, normalized);
                                    super.setValue(trackedValue(normalized, changedPath));
                                    trackChangedPath(changedPath);
                                    return trackedValue(previous, changedPath);
                                }
                            };
                        }

                        @Override
                        public void remove() {
                            if (!removable) {
                                throw new IllegalStateException();
                            }
                            delegate.remove(currentKey);
                            removable = false;
                            trackChangedPath(changedPath);
                        }
                    };
                }

                @Override
                public int size() {
                    return delegate.size();
                }
            };
        }
    }

    private final class TrackedList extends AbstractList<Object> {
        private final List<Object> delegate;
        private final String changedPath;

        private TrackedList(List<Object> delegate, String changedPath) {
            this.delegate = delegate;
            this.changedPath = changedPath;
        }

        @Override
        public Object get(int index) {
            return trackedValue(delegate.get(index), changedPath);
        }

        @Override
        public int size() {
            return delegate.size();
        }

        @Override
        public Object set(int index, Object value) {
            Object normalized = normalizeValue(value, new IdentityHashMap<>());
            Object previous = delegate.set(index, normalized);
            trackChangedPath(changedPath);
            return trackedValue(previous, changedPath);
        }

        @Override
        public void add(int index, Object value) {
            delegate.add(index, normalizeValue(value, new IdentityHashMap<>()));
            trackChangedPath(changedPath);
        }

        @Override
        public Object remove(int index) {
            Object previous = delegate.remove(index);
            trackChangedPath(changedPath);
            return trackedValue(previous, changedPath);
        }
    }

    private static Object normalizeValue(Object value, IdentityHashMap<Object, Boolean> ancestors) {
        if (value instanceof Map<?, ?> map) {
            enterContainer(value, ancestors);
            try {
                Map<String, Object> normalized = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (!(entry.getKey() instanceof String key)) {
                        throw new IllegalArgumentException("YAML map keys must be strings");
                    }
                    normalized.put(key, normalizeValue(entry.getValue(), ancestors));
                }
                return normalized;
            } finally {
                ancestors.remove(value);
            }
        }
        if (value instanceof Iterable<?> iterable) {
            enterContainer(value, ancestors);
            try {
                java.util.List<Object> normalized = new java.util.ArrayList<>();
                for (Object item : iterable) {
                    normalized.add(normalizeValue(item, ancestors));
                }
                return normalized;
            } finally {
                ancestors.remove(value);
            }
        }
        return value;
    }

    private static void enterContainer(Object value, IdentityHashMap<Object, Boolean> ancestors) {
        if (ancestors.put(value, Boolean.TRUE) != null) {
            throw new IllegalArgumentException("Recursive configuration values are not supported");
        }
    }

    private void trackChangedPath(String path) {
        for (String existing : changedPaths) {
            if (path.equals(existing) || path.startsWith(existing + ".")) {
                return;
            }
        }
        changedPaths.removeIf(existing -> existing.startsWith(path + "."));
        changedPaths.add(path);
    }
}
