package io.github.hynmc.hynconfig.exception;

/** Thrown when a configuration file cannot be created, read, or parsed. */
public class ConfigLoadException extends ConfigException {
    /**
     * Creates a load exception with a message and underlying cause.
     * @param message detail message
     * @param cause underlying failure
     */
    public ConfigLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
