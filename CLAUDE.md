# Emma-EndInv

Standalone repository for the Emma fork of Endless Inventory, packaged as a Fabric mod for Minecraft 26.1.

## Scope

- This repository contains only the Emma-EndInv mod.
- The previous multi-project workspace, sibling mods, Python tooling, and deployment scripts are intentionally not part of this repository.

## Layout

| Path | Purpose |
|------|---------|
| `java/emma-endinv/src/main/java` | Mod source code |
| `java/emma-endinv/src/main/resources` | Fabric metadata, mixins, assets, and lang files |
| `java/emma-endinv/gradle/`, `gradlew`, `gradlew.bat` | Gradle wrapper |
| `java/emma-endinv/build.gradle`, `settings.gradle`, `gradle.properties` | Build configuration |
| `java/build_and_deploy.sh` | Standalone build and optional jar copy helper |
| `java/emma-endinv/PORTING_NOTES.md` | Notes for the Fabric 26.1 port |

## Build

Use Java 25.

Windows:

```bash
cd java/emma-endinv
./gradlew.bat build
```

The output jar is written to `java/emma-endinv/build/libs/emma-endinv-1.2.0.jar`.

## Deploy Helper

`java/build_and_deploy.sh` builds the mod, copies the jar into `java/dist/`, deploys it to the default PrismLauncher Emma, Elric, and AltoClef instances, and can optionally copy it into one or more additional target mods directories.

Examples:

```bash
cd java
./build_and_deploy.sh
./build_and_deploy.sh --mods-dir "/path/to/mods"
./build_and_deploy.sh --mods-dir "/path/a" --mods-dir "/path/b"
```

## Conventions

- Minecraft 26.1.2
- Fabric Loader 0.18.4
- Fabric Loom 1.15.5
- Java 25
- Package namespace: `com.emma.endinv`

## Review Guidance

- Treat comments as claims, not proof. Verify behavior from the code path.
- Keep edits focused on the standalone mod and avoid reintroducing assumptions from the old multi-project workspace.
