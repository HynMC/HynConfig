package io.github.hynmc.hynconfig;

import io.github.hynmc.hynconfig.config.YamlConfig;
import io.github.hynmc.hynconfig.exception.ConfigLoadException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/** Manages YAML files stored in one plugin data folder. */
public final class HynConfig {

    private final Path dataFolder;
    private final Map<Path, YamlConfig> configs = new LinkedHashMap<>();

    private HynConfig(Path dataFolder) {
        this.dataFolder = Objects.requireNonNull(dataFolder, "dataFolder").toAbsolutePath().normalize();
    }

    public static HynConfig create(Path dataFolder) {
        return new HynConfig(dataFolder);
    }

    /** Returns and loads a YAML file, creating it on first access when needed. */
    public synchronized YamlConfig yaml(String fileName) {
        return yaml(fileName, () -> null);
    }

    /**
     * Returns and loads a YAML file, copying a classpath template on first access.
     * The supplier is not invoked when the target file already exists.
     */
    public synchronized YamlConfig yaml(String fileName, Supplier<? extends InputStream> resourceSupplier) {
        Path relativePath = Path.of(Objects.requireNonNull(fileName, "fileName"));
        Objects.requireNonNull(resourceSupplier, "resourceSupplier");
        if (relativePath.isAbsolute() || relativePath.toString().isBlank()) {
            throw new IllegalArgumentException("YAML file path must be a non-empty relative path");
        }

        Path normalized = relativePath.normalize();
        if (normalized.startsWith("..")) {
            throw new IllegalArgumentException("YAML file path must stay inside the data folder");
        }

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

    public synchronized void loadAll() {
        configs.values().forEach(YamlConfig::load);
    }

    public synchronized void saveAll() {
        configs.values().forEach(YamlConfig::save);
    }

    public Path getDataFolder() {
        return dataFolder;
    }

    /** Legacy builder entry point for a standalone YAML file. */
    public static YamlConfig.Builder yaml() {
        return YamlConfig.builder();
    }
}
