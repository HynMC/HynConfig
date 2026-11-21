package io.github.hynmc.hynconfig.key;

public final class ConfigKeyBuilder<T> {

    private final String path;
    private final Class<T> type;

    private T defaultValue;
    private boolean hasDefaultValue;
    private boolean nullable;

    ConfigKeyBuilder(String path, Class<T> type) {
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("Config key path cannot be null or empty");
        }

        this.path = path;
        this.type = type;
    }

    public ConfigKeyBuilder<T> defaultValue(T defaultValue) {
        this.defaultValue = defaultValue;
        this.hasDefaultValue = true;
        return this;
    }

    public ConfigKeyBuilder<T> nullable(boolean nullable) {
        this.nullable = nullable;
        return this;
    }

    public ConfigKey<T> build() {
        if (hasDefaultValue && defaultValue == null && !nullable) {
            throw new IllegalStateException("A null default value requires nullable() to be enabled.");
        }

        if (hasDefaultValue && defaultValue != null && !type.isInstance(defaultValue)) {
            throw new IllegalStateException("Default value does not match the declared type.");
        }

        return new ConfigKey<>(path, type, defaultValue, hasDefaultValue, nullable);
    }
}
