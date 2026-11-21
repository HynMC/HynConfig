package io.github.hynmc.hynconfig.key;

/**
 * Typed description of one setting, including its dotted YAML path and optional default.
 * Define keys in a class associated with the file that stores them.
 *
 * @param <T> value type
 */
public final class ConfigKey<T> {

    private final String path;
    private final Class<T> type;
    private final T defaultValue;
    private final boolean hasDefaultValue;
    private final boolean nullable;

    ConfigKey(
            String path,
            Class<T> type,
            T defaultValue,
            boolean hasDefaultValue,
            boolean nullable
    ) {
        this.path = path;
        this.type = type;
        this.defaultValue = defaultValue;
        this.hasDefaultValue = hasDefaultValue;
        this.nullable = nullable;
    }

    /**
     * Creates a builder for a string setting.
     * @param path dotted YAML path
     * @return a string key builder
     */
    public static ConfigKeyBuilder<String> string(String path) {
        return new ConfigKeyBuilder<>(path, String.class);
    }

    /**
     * Creates a builder for an integer setting.
     * @param path dotted YAML path
     * @return an integer key builder
     */
    public static ConfigKeyBuilder<Integer> integer(String path) {
        return new ConfigKeyBuilder<>(path, Integer.class);
    }

    /**
     * Creates a builder for a long integer setting.
     * @param path dotted YAML path
     * @return a long key builder
     */
    public static ConfigKeyBuilder<Long> longValue(String path) {
        return new ConfigKeyBuilder<>(path, Long.class);
    }

    /**
     * Creates a builder for a double precision setting.
     * @param path dotted YAML path
     * @return a double key builder
     */
    public static ConfigKeyBuilder<Double> doubleValue(String path) {
        return new ConfigKeyBuilder<>(path, Double.class);
    }

    /**
     * Creates a builder for a single precision setting.
     * @param path dotted YAML path
     * @return a float key builder
     */
    public static ConfigKeyBuilder<Float> floatValue(String path) {
        return new ConfigKeyBuilder<>(path, Float.class);
    }

    /**
     * Creates a builder for a boolean setting.
     * @param path dotted YAML path
     * @return a boolean key builder
     */
    public static ConfigKeyBuilder<Boolean> bool(String path) {
        return new ConfigKeyBuilder<>(path, Boolean.class);
    }

    /**
     * Returns this key's dotted YAML path.
     * @return the setting path
     */
    public String path() {
        return path;
    }

    /**
     * Returns the declared runtime value class.
     * @return the value class
     */
    public Class<T> type() {
        return type;
    }

    /**
     * Returns the configured default, or {@code null} when no default is configured.
     * @return the default value, or {@code null}
     */
    public T defaultValue() {
        return defaultValue;
    }

    /**
     * Returns whether this key has a default value, including a nullable null default.
     * @return {@code true} if a default was configured
     */
    public boolean hasDefaultValue() {
        return hasDefaultValue;
    }

    /**
     * Returns whether the key permits an explicit null value.
     * @return {@code true} if null is allowed
     */
    public boolean isNullable() {
        return nullable;
    }
}
