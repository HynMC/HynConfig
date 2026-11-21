package io.github.hynmc.hynconfig.key;

/** Builds a {@link ConfigKey} with a declared path, type, optional default, and nullability. */
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

    /**
     * Sets the fallback value and marks a default as present.
     *
     * @param defaultValue fallback value; a null default requires {@link #nullable(boolean) nullable(true)}
     * @return this builder
     */
    public ConfigKeyBuilder<T> defaultValue(T defaultValue) {
        this.defaultValue = defaultValue;
        this.hasDefaultValue = true;
        return this;
    }

    /**
     * Sets whether an explicit null value is permitted. Defaults to {@code false}.
     *
     * @param nullable {@code true} to allow null values
     * @return this builder
     */
    public ConfigKeyBuilder<T> nullable(boolean nullable) {
        this.nullable = nullable;
        return this;
    }

    /**
     * Creates the immutable key.
     *
     * @return a configured key
     * @throws IllegalStateException if the default is null while nullability is disabled, or has the wrong type
     */
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
