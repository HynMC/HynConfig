package io.github.hynmc.hynconfig.config;

import io.github.hynmc.hynconfig.exception.ConfigLoadException;
import io.github.hynmc.hynconfig.exception.ConfigSaveException;
import io.github.hynmc.hynconfig.exception.ConfigTypeException;
import io.github.hynmc.hynconfig.internal.yaml.YamlEngine;
import io.github.hynmc.hynconfig.key.ConfigKey;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;

public class YamlConfig {

    private final Path path;
    private final boolean autoCreate;
    private final int indent;

    private final YamlEngine engine;
    private Map<String, Object> data = new LinkedHashMap<>();
    private String source = "";
    private final Set<String> changedPaths = new LinkedHashSet<>();

    public YamlConfig(Builder builder) {
        this.path = builder.path;
        this.autoCreate = builder.autoCreate;
        this.indent = builder.indent;
        this.engine = new YamlEngine(indent);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private Path path;
        private boolean autoCreate = true;
        private int indent = 2;

        public Builder path(Path path) {
            this.path = path;
            return this;
        }

        public Builder autoCreate(boolean autoCreate) {
            this.autoCreate = autoCreate;
            return this;
        }

        public Builder indent(int indent) {
            this.indent = indent;
            return this;
        }

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
            throw new ConfigTypeException(
                    "Invalid type for configuration value: " + key.path() +
                            ", expected: " + key.type().getName() +
                            ", actual: " + value.getClass().getName()
            );
        }

        return key.type().cast(value);
    }

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

    public boolean contains(ConfigKey<?> key) {
        return contains(key.path());
    }

    /** Adds defaults for missing keys to this in-memory configuration. Call save() to persist them. */
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
        return current.get(parts.get(parts.size() - 1));
    }

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
