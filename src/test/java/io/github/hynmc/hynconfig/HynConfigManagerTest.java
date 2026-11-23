package io.github.hynmc.hynconfig;

import io.github.hynmc.hynconfig.config.YamlConfig;
import io.github.hynmc.hynconfig.exception.ConfigLoadException;
import io.github.hynmc.hynconfig.exception.ConfigSaveException;
import io.github.hynmc.hynconfig.exception.ConfigTypeException;
import io.github.hynmc.hynconfig.key.ConfigKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HynConfigManagerTest {

    @TempDir
    Path tempDir;

    private static final class MainConfigKeys {
        private static final ConfigKey<Integer> PORT = ConfigKey.integer("server.port")
                .defaultValue(25565).build();
    }

    private static final class MessagesConfigKeys {
        private static final ConfigKey<String> PREFIX = ConfigKey.string("prefix")
                .defaultValue("[Server]").build();
    }

    @Test
    void createsAndCachesMultipleYamlFiles() {
        HynConfig manager = HynConfig.create(tempDir);

        YamlConfig main = manager.yaml("config.yml");
        YamlConfig messages = manager.yaml("messages.yml");

        assertSame(main, manager.yaml("config.yml"));
        assertNotSame(main, messages);
        assertTrue(Files.exists(tempDir.resolve("config.yml")));
        assertTrue(Files.exists(tempDir.resolve("messages.yml")));
    }

    @Test
    void copiesClasspathTemplateOnlyWhenTargetDoesNotExist() throws IOException {
        String template = "# Header\n\nserver:\n  port: 25565\n";
        HynConfig manager = HynConfig.create(tempDir);
        YamlConfig config = manager.yaml("config.yml", () -> new ByteArrayInputStream(
                template.getBytes(StandardCharsets.UTF_8)));

        assertEquals(template, Files.readString(tempDir.resolve("config.yml")));
        assertEquals(25565, config.get(MainConfigKeys.PORT));
        config.save();
        assertEquals(template, Files.readString(tempDir.resolve("config.yml")));

        YamlConfig cached = manager.yaml("config.yml", () -> {
            throw new AssertionError("resource must not be read for an existing file");
        });
        assertSame(config, cached);
    }

    @Test
    void preservesCommentsBlankLinesAndLineEndingsWhenSavingScalarChanges() throws IOException {
        String template = "# Header\r\n\r\nserver:\r\n  # Port explanation\r\n  port: 25565 # keep inline\r\n\r\n# Footer\r\n";
        HynConfig manager = HynConfig.create(tempDir);
        YamlConfig config = manager.yaml("config.yml", () -> new ByteArrayInputStream(
                template.getBytes(StandardCharsets.UTF_8)));

        config.set(MainConfigKeys.PORT, 25566);
        config.save();

        assertEquals(template.replace("25565", "25566"),
                Files.readString(tempDir.resolve("config.yml")));
    }

    @Test
    void persistsCodeDefaultsForMissingNestedPathsWithoutReformattingExistingFile() throws IOException {
        String template = "# Header\n\nserver:\n  # Existing explanation\n  port: 25565\n\n# Footer\n";
        HynConfig manager = HynConfig.create(tempDir);
        YamlConfig config = manager.yaml("config.yml", () -> new ByteArrayInputStream(
                template.getBytes(StandardCharsets.UTF_8)));
        ConfigKey<String> language = ConfigKey.string("language").defaultValue("ko").build();
        ConfigKey<String> motd = ConfigKey.string("server.motd").defaultValue("Welcome!").build();

        config.applyDefaults(MainConfigKeys.PORT, language, motd);
        config.save();

        String saved = Files.readString(tempDir.resolve("config.yml"));
        assertTrue(saved.contains("  port: 25565\n  motd: Welcome!\nlanguage: ko\n\n# Footer"), saved);
        assertTrue(saved.startsWith("# Header\n\nserver:\n  # Existing explanation\n"));
        assertTrue(saved.contains("language: ko"));
        assertEquals("ko", config.get(language));

        YamlConfig reloaded = HynConfig.create(tempDir).yaml("config.yml");
        assertEquals("Welcome!", reloaded.get(motd));
    }

    @Test
    void preservesListLayoutAndInlineCommentsWhenListLengthIsUnchanged() throws IOException {
        String template = "# Items\nitems:\n  - first\n  - second # keep this\n\n# Footer\n";
        HynConfig manager = HynConfig.create(tempDir);
        YamlConfig config = manager.yaml("list.yml", () -> new ByteArrayInputStream(
                template.getBytes(StandardCharsets.UTF_8)));

        config.set("items", java.util.List.of("first", "updated"));
        config.save();

        assertEquals(template.replace("second # keep this", "updated # keep this"),
                Files.readString(tempDir.resolve("list.yml")));
    }

    @Test
    void savesAndReloadsAllConfigsWithNestedPaths() throws IOException {
        HynConfig manager = HynConfig.create(tempDir);
        YamlConfig main = manager.yaml("config.yml");
        YamlConfig messages = manager.yaml("messages.yml");

        main.set(MainConfigKeys.PORT, 25566);
        messages.set(MessagesConfigKeys.PREFIX, "[Hyn]");
        manager.saveAll();

        assertTrue(Files.readString(tempDir.resolve("config.yml")).contains("server:"));
        assertTrue(Files.readString(tempDir.resolve("config.yml")).contains("  port: 25566"));

        main.set(MainConfigKeys.PORT, 1);
        messages.set(MessagesConfigKeys.PREFIX, "changed");
        manager.loadAll();

        assertEquals(25566, main.get(MainConfigKeys.PORT));
        assertEquals("[Hyn]", messages.get(MessagesConfigKeys.PREFIX));
    }

    @Test
    void savesAndRemovesYamlFromCacheWhenUnloading() throws IOException {
        String template = "# Header\n\nvalue: 1 # keep comment\n";
        HynConfig manager = HynConfig.create(tempDir);
        YamlConfig config = manager.yaml("nested/settings.yml", () -> new ByteArrayInputStream(
                template.getBytes(StandardCharsets.UTF_8)));
        config.set("value", 2);

        assertTrue(manager.unload("nested/./settings.yml"));
        assertFalse(manager.unload("nested/settings.yml"));
        assertEquals(template.replace("value: 1", "value: 2"),
                Files.readString(tempDir.resolve("nested/settings.yml")));

        YamlConfig reloaded = manager.yaml("nested/settings.yml");
        assertNotSame(config, reloaded);
        assertEquals(2, reloaded.get("value"));
    }

    @Test
    void supportsDefaultsNullabilityAndTypeChecks() {
        YamlConfig config = HynConfig.create(tempDir).yaml("config.yml");
        ConfigKey<String> nullable = ConfigKey.string("optional")
                .nullable(true).build();

        assertEquals(25565, config.get(MainConfigKeys.PORT));
        assertNull(config.get(nullable));
        config.set(nullable, "value");
        assertEquals("value", config.get(nullable));

        config.set("server.port", "wrong");
        assertThrows(ConfigTypeException.class, () -> config.get(MainConfigKeys.PORT));
        assertThrows(ConfigTypeException.class, () -> config.set(MainConfigKeys.PORT, null));
    }

    @Test
    void convertsSnakeYamlNumericValuesToTypedNumericKeys() throws IOException {
        Path path = tempDir.resolve("numbers.yml");
        Files.writeString(path,
                "small: 10000\nfraction: 1.25\nwholeFraction: 1\n" +
                        "wholeFloat: 16777216\nimpreciseFloat: 16777217\n" +
                        "impreciseLong: 9007199254740993\ntooLargeFloat: 1.0e100\n");
        YamlConfig config = YamlConfig.builder().path(path).build();
        config.load();

        ConfigKey<Long> longKey = ConfigKey.longValue("small").build();
        ConfigKey<Float> floatKey = ConfigKey.floatValue("fraction").build();
        ConfigKey<Double> doubleKey = ConfigKey.doubleValue("wholeFraction").build();
        ConfigKey<Float> wholeFloatKey = ConfigKey.floatValue("wholeFloat").build();
        ConfigKey<Float> impreciseFloatKey = ConfigKey.floatValue("impreciseFloat").build();
        ConfigKey<Double> impreciseLongKey = ConfigKey.doubleValue("impreciseLong").build();
        ConfigKey<Float> tooLargeFloatKey = ConfigKey.floatValue("tooLargeFloat").build();

        assertEquals(10000L, config.get(longKey));
        assertEquals(1.25f, config.get(floatKey));
        assertEquals(1.0d, config.get(doubleKey));
        assertEquals(16777216.0f, config.get(wholeFloatKey));
        assertThrows(ConfigTypeException.class, () -> config.get(impreciseFloatKey));
        assertThrows(ConfigTypeException.class, () -> config.get(impreciseLongKey));
        assertThrows(ConfigTypeException.class, () -> config.get(tooLargeFloatKey));
    }

    @Test
    void tracksMutationsMadeThroughReturnedMapsAndLists() throws IOException {
        String template = "settings:\n  enabled: false\n  values:\n    - first\n    - second # keep comment\n";
        Path path = tempDir.resolve("tracked.yml");
        Files.writeString(path, template);
        YamlConfig config = YamlConfig.builder().path(path).build();
        config.load();

        @SuppressWarnings("unchecked")
        Map<String, Object> settings = (Map<String, Object>) config.get("settings");
        settings.put("enabled", true);
        @SuppressWarnings("unchecked")
        List<Object> values = (List<Object>) settings.get("values");
        values.set(1, "updated");

        config.save();

        String saved = Files.readString(path);
        assertTrue(saved.contains("enabled: true"), saved);
        assertTrue(saved.contains("- updated # keep comment"), saved);
    }

    @Test
    void rejectsUnsupportedCollectionStructureChangesWithoutOverwritingFile() throws IOException {
        String original = "items:\n  - first\n";
        Path path = tempDir.resolve("list-structure.yml");
        Files.writeString(path, original);
        YamlConfig config = YamlConfig.builder().path(path).build();
        config.load();

        @SuppressWarnings("unchecked")
        List<Object> items = (List<Object>) config.get("items");
        items.add("second");

        assertThrows(ConfigSaveException.class, config::save);
        assertEquals(original, Files.readString(path));
    }

    @Test
    void rejectsAddingEntriesToFlowMappingsWithoutCorruptingTheFile() throws IOException {
        String original = "settings: {foo: 1, bar: 2}\n";
        Path path = tempDir.resolve("flow.yml");
        Files.writeString(path, original);
        YamlConfig config = YamlConfig.builder().path(path).build();
        config.load();
        config.set("settings.baz", 3);

        assertThrows(ConfigSaveException.class, config::save);
        assertEquals(original, Files.readString(path));
        YamlConfig reloaded = YamlConfig.builder().path(path).build();
        reloaded.load();
        assertEquals(1, reloaded.get("settings.foo"));
    }

    @Test
    void appliesCodeDefaultsWithoutOverwritingExistingValues() {
        YamlConfig config = HynConfig.create(tempDir).yaml("config.yml");
        config.set("server.port", 12345);
        config.applyDefaults(MainConfigKeys.PORT, MessagesConfigKeys.PREFIX);

        assertEquals(12345, config.get(MainConfigKeys.PORT));
        assertEquals("[Server]", config.get(MessagesConfigKeys.PREFIX));
        config.save();

        YamlConfig reloaded = HynConfig.create(tempDir).yaml("config.yml");
        assertEquals("[Server]", reloaded.get(MessagesConfigKeys.PREFIX));
        assertEquals(12345, reloaded.get(MainConfigKeys.PORT));
    }

    @Test
    void rejectsPathsOutsideDataFolder() {
        HynConfig manager = HynConfig.create(tempDir);
        assertThrows(IllegalArgumentException.class, () -> manager.yaml("../outside.yml"));
        assertThrows(IllegalArgumentException.class, () -> manager.yaml(tempDir.resolve("outside.yml").toString()));
    }

    @Test
    void rejectsNonStringYamlMapKeysAtAnyDepth() throws IOException {
        Path path = tempDir.resolve("invalid.yml");
        Files.writeString(path, "root:\n  12: value\n");

        YamlConfig config = YamlConfig.builder().path(path).build();
        assertThrows(ConfigLoadException.class, config::load);
    }

    @Test
    void rejectsNonMappingYamlRoot() throws IOException {
        Path path = tempDir.resolve("invalid-root.yml");
        Files.writeString(path, "- one\n- two\n");

        YamlConfig config = YamlConfig.builder().path(path).build();
        assertThrows(ConfigLoadException.class, config::load);
    }

    @Test
    void doesNotCreateMissingFileWhenAutoCreateIsDisabled() {
        Path path = tempDir.resolve("manual.yml");
        YamlConfig config = YamlConfig.builder().path(path).autoCreate(false).build();

        assertThrows(ConfigLoadException.class, config::load);
        assertThrows(ConfigSaveException.class, config::save);
        assertFalse(Files.exists(path));
    }

    @Test
    void rejectsRecursiveYamlAliases() throws IOException {
        Path path = tempDir.resolve("recursive.yml");
        Files.writeString(path, "value: &loop [*loop]\n");

        YamlConfig config = YamlConfig.builder().path(path).build();
        assertThrows(ConfigLoadException.class, config::load);
    }

    @Test
    void rejectsNonStringKeysWhenSettingMaps() {
        YamlConfig config = HynConfig.create(tempDir).yaml("config.yml");
        assertThrows(IllegalArgumentException.class,
                () -> config.set("values", Map.of(1, "not a string key")));
    }
}
