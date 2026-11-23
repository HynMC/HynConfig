# HynConfig
![GitHub License](https://img.shields.io/github/license/hynmc/hynconfig)
![Maven Central Version](https://img.shields.io/maven-central/v/io.github.hynmc/hynconfig)


HynConfig is a lightweight YAML configuration library for Java 17 Minecraft plugins. It makes it straightforward to create and manage multiple files, define typed keys with defaults, and save settings while retaining the comments and spacing in your YAML files.

HynConfig uses `Path` and has no Bukkit or Paper dependency. Use a plugin's data folder as its root.

## Features

- Manage multiple YAML files under one plugin data folder.
- Create and load each file on first access; save one file or all registered files.
- Copy a default YAML file from your plugin JAR when the server-side file is missing.
- Define typed `ConfigKey` values with defaults and nullability.
- Use dotted paths such as `server.port` for nested YAML mappings.
- Preserve existing comments, blank lines, inline comments, and line endings for supported edits.

## Installation

The artifact coordinates are `io.github.hynmc:hynconfig:0.1.2`. Maven Central search may take some time to index a newly published release. The dependency can be declared as follows once the release is available from Central.

### Gradle (Kotlin DSL)

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("io.github.hynmc:hynconfig:0.1.2")
}
```

### Gradle (Groovy)

```groovy
repositories {
    mavenCentral()
}

dependencies {
    implementation 'io.github.hynmc:hynconfig:0.1.2'
}
```

### Maven

```xml
<dependency>
    <groupId>io.github.hynmc</groupId>
    <artifactId>hynconfig</artifactId>
    <version>0.1.2</version>
</dependency>
```

HynConfig uses SnakeYAML at runtime; Gradle and Maven will resolve it as a transitive dependency.

## Quick start

Define keys in a class associated with their configuration file:

```java
final class MainConfigKeys {
    static final ConfigKey<Integer> PORT =
            ConfigKey.integer("server.port").defaultValue(25565).build();
    static final ConfigKey<Boolean> DEBUG =
            ConfigKey.bool("debug").defaultValue(false).build();
}

final class MessageConfigKeys {
    static final ConfigKey<String> PREFIX =
            ConfigKey.string("prefix").defaultValue("[Server]").build();
}
```

Create the manager from your plugin's data folder and request each file by its relative path:

```java
HynConfig configs = HynConfig.create(getDataFolder().toPath());
YamlConfig main = configs.yaml("config.yml");
YamlConfig messages = configs.yaml("messages.yml");

main.applyDefaults(MainConfigKeys.PORT, MainConfigKeys.DEBUG);
messages.applyDefaults(MessageConfigKeys.PREFIX);

int port = main.get(MainConfigKeys.PORT);
main.set(MainConfigKeys.PORT, port + 1);
messages.set(MessageConfigKeys.PREFIX, "[HynMC]");

configs.saveAll();
```

The first request creates and loads a missing file. Repeated requests for the same path return the same configuration object. File paths must be relative and remain inside the configured data folder.

`get(key)` returns a key's default when the value is missing, without writing it to disk. `applyDefaults(...)` inserts missing key defaults into memory while leaving existing values untouched; call `save()` or `saveAll()` to persist them.

## Copy a default file from your plugin JAR

Place a template at `src/main/resources/config.yml` and pass a lazy resource supplier:

```java
YamlConfig config = configs.yaml(
        "config.yml",
        () -> getResource("config.yml")
);
```

The resource is copied only if the server-side file does not exist. HynConfig closes the stream after copying. If the resource is missing, it creates an empty file instead. Existing files are never overwritten by the template.

Use `applyDefaults(...)` for defaults that you define in Java. You can also combine both approaches: copy a commented template for first-time setup, then apply code defaults for missing keys.

## Load and save

```java
config.load();       // Reload one file; discards unsaved in-memory changes
config.save();       // Save one file
configs.loadAll();   // Reload every registered file
configs.saveAll();   // Save every registered file
```

Use `config.get("server.port")`, `config.set("server.port", 25565)`, and `config.contains("server.port")` when you do not need a typed key.

## Unloading cached files

When a file is no longer needed, unload it to save pending changes and remove it from the manager's cache:

```java
YamlConfig temporaryConfig = configs.yaml("sessions/current.yml");
temporaryConfig.set("last-user", "example");

configs.unload("sessions/current.yml");
```

`unload(path)` saves the cached file before removing it from the manager; the YAML file remains on disk. It returns `false` if the file is not currently cached. If your code holds a reference to the unloaded `YamlConfig`, discard it too and request the file again when needed.

## YAML formatting

Saving without changes leaves the source file untouched. Scalar updates, same-length list updates, and added mapping keys are patched into the original text to preserve existing comments, blank lines, inline comments, and line endings. Maps and lists returned by `get(path)` are live tracked views, so edits through those collections are included in the next save. HynConfig rejects edits that remove mapping entries, change list length, add entries to flow-style mappings, or replace a multiline scalar because those changes cannot currently be made while guaranteeing the original formatting remains intact. A rejected save leaves the source file unchanged.

## Build and documentation

```shell
./gradlew test
./gradlew javadoc
./gradlew javadocJar
```

On Windows, use `gradlew.bat` instead. HTML Javadoc is generated under `build/docs/javadoc`; the Javadoc JAR is written to `build/libs`.

## License

HynConfig is distributed under the [MIT License](LICENSE).
