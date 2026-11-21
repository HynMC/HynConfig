package io.github.hynmc.hynconfig.key;

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

    public static ConfigKeyBuilder<String> string(String path) {
        return new ConfigKeyBuilder<>(path, String.class);
    }

    public static ConfigKeyBuilder<Integer> integer(String path) {
        return new ConfigKeyBuilder<>(path, Integer.class);
    }

    public static ConfigKeyBuilder<Long> longValue(String path) {
        return new ConfigKeyBuilder<>(path, Long.class);
    }

    public static ConfigKeyBuilder<Double> doubleValue(String path) {
        return new ConfigKeyBuilder<>(path, Double.class);
    }

    public static ConfigKeyBuilder<Float> floatValue(String path) {
        return new ConfigKeyBuilder<>(path, Float.class);
    }

    public static ConfigKeyBuilder<Boolean> bool(String path) {
        return new ConfigKeyBuilder<>(path, Boolean.class);
    }

    public String path() {
        return path;
    }

    public Class<T> type() {
        return type;
    }

    public T defaultValue() {
        return defaultValue;
    }

    public boolean hasDefaultValue() {
        return hasDefaultValue;
    }

    public boolean isNullable() {
        return nullable;
    }
}
