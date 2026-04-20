# Emma-EndInv

Emma-EndInv is a standalone Fabric mod repository for the Emma fork of Endless Inventory on Minecraft 26.1.

## Repository Layout

- `java/emma-endinv/` - the standalone Gradle/Fabric mod project
- `java/build_and_deploy.sh` - optional helper to build and copy the jar to one or more mod folders
- `README.md`, `CLAUDE.md` - repository-level documentation

## Requirements

- Java 25
- Windows: run Gradle with `gradlew.bat`
- Git Bash or another Bash-compatible shell if you want to use `java/build_and_deploy.sh`

## Build

From the repository root:

```bash
cd java/emma-endinv
./gradlew.bat build
```

Output jar:

```text
java/emma-endinv/build/libs/emma-endinv-1.2.0.jar
```

## Deploy Helper

Build only:

```bash
cd java
./build_and_deploy.sh
```

Build and copy the jar to one or more mods folders:

```bash
cd java
./build_and_deploy.sh --mods-dir "/path/to/instance/mods"
./build_and_deploy.sh --mods-dir "/path/to/instance-a/mods" --mods-dir "/path/to/instance-b/mods"
```

The helper also copies the built jar into `java/dist/` for easy pickup.

## Notes

- This repository contains only the standalone Emma-EndInv mod.
- It does not include the old multi-mod workspace, sibling mods, Python tooling, or deployment automation for other projects.
