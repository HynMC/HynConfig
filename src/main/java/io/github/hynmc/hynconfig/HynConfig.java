package io.github.hynmc.hynconfig;

import io.github.hynmc.hynconfig.config.YamlConfig;
import io.github.hynmc.hynconfig.exception.ConfigLoadException;
import io.github.hynmc.hynconfig.exception.ConfigSaveException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Manages YAML files stored in one plugin data folder.
 *
 * <p>Each relative file path is loaded once and cached by this manager. Files are created
 * automatically when first requested. The class has no Bukkit or Paper dependency; pass the
 * plugin's data folder as a {@link Path}.
 *
 * <pre>{@code
 * HynConfig configs = HynConfig.create(plugin.getDataFolder().toPath());
 * YamlConfig config = configs.yaml("config.yml");
 * }</pre>
 */
public final class HynConfig {

    private final Path dataFolder;
    private final Map<Path, YamlConfig> configs = new LinkedHashMap<>();

    private HynConfig(Path dataFolder) {
        this.dataFolder = Objects.requireNonNull(dataFolder, "dataFolder").toAbsolutePath().normalize();
    }

    /**
     * Creates a manager rooted at the given data folder.
     *
     * @param dataFolder root directory in which configuration files are stored
     * @return a new configuration manager
     * @throws NullPointerException if {@code dataFolder} is {@code null}
     */
    public static HynConfig create(Path dataFolder) {
        return new HynConfig(dataFolder);
    }

    /**
     * Returns and loads a YAML file, creating an empty file on first access when needed.
     *
     * @param fileName relative path under this manager's data folder
     * @return the cached configuration for the path
     * @throws IllegalArgumentException if the path is absolute, blank, or escapes the data folder
     * @throws ConfigLoadException if the file cannot be created or parsed
     */
    public synchronized YamlConfig yaml(String fileName) {
        return yaml(fileName, () -> null);
    }

    /**
     * Returns and loads a YAML file, copying a classpath template on first access.
     * The supplier is not invoked when the target file already exists.
     * The returned stream is closed after it has been copied. If the supplier returns
     * {@code null}, an empty file is created instead.
     *
     * @param fileName relative path under this manager's data folder
     * @param resourceSupplier lazily supplies the template stream, or {@code null} when absent
     * @return the cached configuration for the path
     * @throws NullPointerException if either argument is {@code null}
     * @throws IllegalArgumentException if the path is absolute, blank, or escapes the data folder
     * @throws ConfigLoadException if the file cannot be created, copied, or parsed
     */
    public synchronized YamlConfig yaml(String fileName, Supplier<? extends InputStream> resourceSupplier) {
        Objects.requireNonNull(resourceSupplier, "resourceSupplier");
        Path normalized = normalizeRelativePath(fileName);

        return configs.computeIfAbsent(normalized, path -> {
            Path target = dataFolder.resolve(path);
            try {
                Path parent = target.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                if (Files.notExists(target)) {
                    try (InputStream resource = resourceSupplier.get()) {
                        if (resource == null) {
                            Files.createFile(target);
                        } else {
                            Files.copy(resource, target);
                        }
                    }
                }
            } catch (IOException | RuntimeException exception) {
                throw new ConfigLoadException("Failed to create configuration file: " + target, exception);
            }

            YamlConfig config = YamlConfig.builder().path(target).autoCreate(true).build();
            config.load();
            return config;
        });
    }

    /**
     * Saves and removes a previously loaded YAML file from this manager's cache.
     * The file remains on disk. If saving fails, the configuration stays cached.
     * Any references to the returned {@link YamlConfig} held by the caller remain valid;
     * discard those references as well when unloading large numbers of files.
     *
     * @param fileName relative path used to load the YAML file
     * @return {@code true} if a cached configuration was saved and removed, or {@code false} if it was not cached
     * @throws IllegalArgumentException if the path is absolute, blank, or escapes the data folder
     * @throws NullPointerException if {@code fileName} is {@code null}
     * @throws ConfigSaveException if saving the configuration fails
     */
    public synchronized boolean unload(String fileName) {
        Path normalized = normalizeRelativePath(fileName);
        YamlConfig config = configs.get(normalized);
        if (config == null) {
            return false;
        }

        config.save();
        configs.remove(normalized);
        return true;
    }

    private static Path normalizeRelativePath(String fileName) {
        Path relativePath = Path.of(Objects.requireNonNull(fileName, "fileName"));
        if (relativePath.isAbsolute() || relativePath.toString().isBlank()) {
            throw new IllegalArgumentException("YAML file path must be a non-empty relative path");
        }

        Path normalized = relativePath.normalize();
        if (normalized.startsWith("..")) {
            throw new IllegalArgumentException("YAML file path must stay inside the data folder");
        }
        return normalized;
    }

    /** Reloads every YAML file already registered with this manager. Unsaved in-memory changes are discarded. */
    public synchronized void loadAll() {
        configs.values().forEach(YamlConfig::load);
    }

    /** Saves every YAML file already registered with this manager. */
    public synchronized void saveAll() {
        configs.values().forEach(YamlConfig::save);
    }

    /**
     * Returns the normalized absolute root directory used by this manager.
     *
     * @return the data folder
     */
    public Path getDataFolder() {
        return dataFolder;
    }

    /**
     * Creates a builder for a standalone YAML file without a manager.
     *
     * @return a standalone YAML configuration builder
     * @deprecated Prefer {@link #create(Path)} and {@link #yaml(String)} to manage multiple files.
     */
    @Deprecated
    public static YamlConfig.Builder yaml() {
        return YamlConfig.builder();
    }
}
