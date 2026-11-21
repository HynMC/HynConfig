package io.github.hynmc.hynconfig.exception;

/** Thrown when a setting value violates its declared type or nullability. */
public class ConfigTypeException extends ConfigException {
    /**
     * Creates a type exception with a detail message.
     * @param message detail message
     */
    public ConfigTypeException(String message) {
        super(message);
    }
}
