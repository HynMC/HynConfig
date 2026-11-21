package io.github.hynmc.hynconfig.exception;

/** Thrown when a configuration change cannot be saved. */
public class ConfigSaveException extends ConfigException {
    /**
     * Creates a save exception with a message and underlying cause.
     * @param message detail message
     * @param cause underlying failure
     */
    public ConfigSaveException(String message, Throwable cause) {
        super(message, cause);
    }
}
