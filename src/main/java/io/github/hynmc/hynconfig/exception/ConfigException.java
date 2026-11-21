package io.github.hynmc.hynconfig.exception;

/** Base unchecked exception for configuration operations. */
public class ConfigException extends RuntimeException {
    /**
     * Creates an exception with a detail message.
     * @param message detail message
     */
    public ConfigException(String message) {
        super(message);
    }

    /**
     * Creates an exception with a detail message and underlying cause.
     * @param message detail message
     * @param cause underlying failure
     */
    public ConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
