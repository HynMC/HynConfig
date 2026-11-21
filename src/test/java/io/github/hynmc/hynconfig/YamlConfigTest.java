package io.github.hynmc.hynconfig;

import io.github.hynmc.hynconfig.config.YamlConfig;
import io.github.hynmc.hynconfig.key.ConfigKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class YamlConfigTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldCreateAndSaveConfiguration() {
        Path configPath = tempDir.resolve("config.yml");

        YamlConfig config = HynConfig.yaml()
                .path(configPath)
                .autoCreate(true)
                .indent(2)
                .build();

        config.load();

        config.set("name", "HynConfig");
        config.set("version", "0.1.0");

        config.save();

        assertTrue(configPath.toFile().exists());
    }

    @Test
    void shouldLoadExistingConfiguration() {
        Path configPath = tempDir.resolve("config.yml");

        YamlConfig config = HynConfig.yaml()
                .path(configPath)
                .autoCreate(true)
                .indent(2)
                .build();

        config.load();
        config.set("name", "HynConfig");
        config.save();

        YamlConfig loaded = HynConfig.yaml()
                .path(configPath)
                .autoCreate(true)
                .indent(2)
                .build();
        loaded.load();

        assertEquals("HynConfig", loaded.get("name"));
    }

    @Test
    void shouldUseConfigKey() {
        Path configPath = tempDir.resolve("config.yml");

        ConfigKey<String> LANG = ConfigKey.string("global.lang")
                .defaultValue("en")
                .nullable(false)
                .build();

        YamlConfig config = HynConfig.yaml()
                .path(configPath)
                .autoCreate(true)
                .indent(2)
                .build();

        config.load();

        String lang = config.get(LANG);

        config.set(LANG, "kr");
        config.save();

        assertEquals("kr", config.get(LANG));
    }
}
