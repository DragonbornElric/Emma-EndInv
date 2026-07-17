# Emma-EndInv

Emma-EndInv is the standalone, multi-loader repository for Emma's Endless
Inventory fork.

Maintained branches:

- `main`: Minecraft 26.1.2, Java 25
- `mc-1.20.1`: Minecraft 1.20.1, Java 17

## Repository Layout

- `java/emma-endinv/` - shared code plus Fabric, NeoForge, and Folia projects
- `java/build_and_deploy.sh` - version-aware build and deployment helper
- `README.md`, `CLAUDE.md` - repository-level documentation

## Requirements

- Java 17 for `mc-1.20.1`; Java 25 for `main`
- Windows: run Gradle with `gradlew.bat`
- Git Bash or another Bash-compatible shell if you want to use `java/build_and_deploy.sh`

## Build

From the repository root:

```bash
cd java/emma-endinv
./gradlew.bat :fabric:build :neoforge:build :folia:build
```

Output jars:

```text
java/emma-endinv/fabric/build/libs/endless_inventory-fabric-<mc-version>-<mod-version>.jar
java/emma-endinv/neoforge/build/libs/endless_inventory-neoforge-<mc-version>-<mod-version>.jar
java/emma-endinv/folia/build/libs/endless_inventory-folia-<mc-version>-<mod-version>.jar
```

## Deploy Helper

Build and deploy to version-matched local targets:

```bash
cd java
./build_and_deploy.sh
```

Build, deploy to the default PrismLauncher targets, and also copy the jar to one or more extra mods folders:

```bash
cd java
./build_and_deploy.sh --mods-dir "/path/to/instance/mods"
./build_and_deploy.sh --mods-dir "/path/to/instance-a/mods" --mods-dir "/path/to/instance-b/mods"
```

The helper also copies the built jars into `java/dist/`. On `mc-1.20.1`, it
only uses 1.20.1-named Prism instances and versioned manual staging folders;
it cannot overwrite the maintained 26.1.2 deployment targets.

## Notes

- This is the full Emma feature set, including the inventory UI, auto-pick,
  recipe-book integration, crafting, Endless Inventory persistence, and the
  shared packet protocol. The 1.20.1 branch is a backport, not a reduced port.
- It does not include the old multi-mod workspace, sibling mods, Python tooling, or deployment automation for other projects.
