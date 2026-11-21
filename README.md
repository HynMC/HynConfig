# HynConfig

HynConfig is a small YAML configuration library for Java 17 Minecraft plugins. It has no Bukkit or Paper dependency; pass the plugin data folder as a `Path`.

## Multiple files

```java
HynConfig configs = HynConfig.create(plugin.getDataFolder().toPath());
YamlConfig main = configs.yaml("config.yml");
YamlConfig messages = configs.yaml("messages.yml");

main.set(MainConfigKeys.PORT, 25565);
messages.set(MessagesConfigKeys.PREFIX, "[Server]");
configs.saveAll();
```

The manager creates and loads each file the first time it is requested. Repeated requests for the same relative path return the same `YamlConfig` instance. Absolute paths and paths that escape the plugin data folder are rejected.

## Copy a default file from the plugin JAR

Put `config.yml` in the plugin resources and provide a lazy stream supplier:

```java
YamlConfig config = configs.yaml(
        "config.yml",
        () -> MyPlugin.class.getResourceAsStream("/config.yml")
);
```

The resource is copied only when the target file does not exist. The stream is closed by HynConfig. If the resource is absent, an empty file is created instead.

## Define defaults in code

Keep keys in a class associated with the file they belong to:

```java
final class MainConfigKeys {
    static final ConfigKey<Integer> PORT =
            ConfigKey.integer("server.port").defaultValue(25565).build();
}

YamlConfig config = configs.yaml("config.yml");
config.applyDefaults(MainConfigKeys.PORT);
int port = config.get(MainConfigKeys.PORT);
config.set(MainConfigKeys.PORT, 25566);
config.save();
```

`get(key)` returns a key's default when the setting is missing, without writing it to disk. `applyDefaults(...)` inserts missing defaults into memory; `save()` or `saveAll()` persists them without overwriting values that are already present.

Use `load()` / `save()` for one file or `loadAll()` / `saveAll()` for all registered files. Dotted paths such as `server.port` address nested YAML keys.

## Formatting preservation

Saving without changes leaves the source file untouched. Scalar updates, same-length list updates, and added mapping keys are patched into the original text so existing comments, blank lines, inline comments, and line endings stay in place. HynConfig rejects saves that require removing mapping entries, changing list length, or replacing a multiline scalar because those edits cannot currently be applied while guaranteeing the original layout remains intact.
