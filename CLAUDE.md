# Emma-EndInv

Multi-loader repository for the Emma fork of Endless Inventory — Fabric,
historical NeoForge/Forge, and Folia.

This branch is the full Minecraft 1.20.1 backport and uses Java 17. The
maintained Minecraft 26.1.2/Java 25 line remains on `main`.

## Scope

- This repository contains only the Emma-EndInv mod.
- The previous multi-project workspace, sibling mods, Python tooling, and deployment scripts are intentionally not part of this repository.

## Layout

```
java/emma-endinv/
├── buildSrc/            Convention plugins (multiloader-common, multiloader-loader, multiloader-folia)
├── common/              Loader-neutral code: all mixins, payloads, menus, GUI, service interfaces
├── fabric/              Fabric entry points, FabricNetworking, Fabric events, fabric.mod.json
├── neoforge/            Historical NeoForge/Forge entry point, networking, and event handlers
├── folia/               Folia server plugin (JavaPlugin, paperweight-userdev, paper-plugin.yml)
├── build.gradle         Root: plugin version declarations only
├── settings.gradle      Includes common, fabric, neoforge, folia
└── gradle.properties    All version pins (MC, Fabric Loader, NeoForge, Folia, Java)
```

| Path | Purpose |
|------|---------|
| `common/src/main/java` | All loader-neutral source (payloads, mixins, menus, GUI, shims) |
| `common/src/main/resources` | Access widener, AT, shared mixin JSON, shared assets |
| `fabric/src/main/java` | Fabric-specific code (entry points, networking, events) |
| `fabric/src/main/resources` | `fabric.mod.json`, Fabric mixin JSONs, service files |
| `neoforge/src/main/java` | NeoForge-specific code (entry points, networking, events) |
| `neoforge/src/main/resources` | `mods.toml`, NeoForge mixin JSON, AT, service files |
| `folia/src/main/java` | Folia plugin code (EndInvFoliaPlugin, adapters, scheduler helpers, payload overrides) |
| `folia/src/main/resources` | `paper-plugin.yml`, `META-INF/services/ILoaderProvider` |
| `java/emma-endinv/gradlew`, `gradlew.bat` | Gradle wrapper |
| `java/build_and_deploy.sh` | Build helper — produces jars and deploys |
| `java/emma-endinv/PORTING_NOTES.md` | 1.20.1 backport source anchors, API choices, and parity notes |

## Build

Use Java 17 on this branch.

**Fabric only:**
```bash
cd java/emma-endinv
./gradlew.bat :fabric:build
```

**NeoForge only:**
```bash
./gradlew.bat :neoforge:build
```

**Both client loaders:**
```bash
./gradlew.bat :fabric:build :neoforge:build
```

**Folia server plugin:**
```bash
./gradlew.bat :folia:build
```

Output jars:
- `fabric/build/libs/endless_inventory-fabric-<mc_version>*.jar`
- `neoforge/build/libs/endless_inventory-neoforge-<mc_version>*.jar`
- `folia/build/libs/endless_inventory-folia-<mc_version>*.jar`

## Deploy Helper

```bash
cd java

# Build all three jars and deploy Fabric jar to default Prism instances
./build_and_deploy.sh

# Deploy Folia jar to a server plugins/ folder
./build_and_deploy.sh --folia-mods-dir "/path/to/folia/plugins"

# Deploy NeoForge jar to a specific mods folder
./build_and_deploy.sh --neoforge-mods-dir "/path/to/neoforge/mods"

# Build Fabric only (skip NeoForge and Folia compile)
./build_and_deploy.sh --skip-neoforge --skip-folia

# Legacy flag: --mods-dir deploys Fabric jar to extra directory
./build_and_deploy.sh --mods-dir "/path/a"
```

The Fabric jar deploys only to 1.20.1-named PrismLauncher instances on this
branch. The helper also keeps the manual Fabric, NeoForge, and Folia staging
folders under version-specific `1.20.1` subdirectories.

## Conventions

- Minecraft 1.20.1
- Fabric Loader 0.19.3 / Fabric API 0.92.11+1.20.1
- Historical NeoForge/Forge 1.20.1-47.1.106
- Folia 1.20.1-R0.1-SNAPSHOT (paperweight-userdev, `foliaDevBundle`)
- Fabric Loom 1.15.5 / ModDevGradle 2.0.141
- Java 17
- Package namespace: `com.emma.endinv`
- Loader-neutral service abstraction: `ILoaderProvider` (ServiceLoader, one impl per module)
- Mixin compatibility: `JAVA_17`, `defaultRequire: 1`
- Folia threading: no `Bukkit.getScheduler()`; use `entity.getScheduler()`, `getGlobalRegionScheduler()`, `getRegionScheduler()`

## Loader Coupling Map

| Surface | Module |
|---------|--------|
| All 17 payloads | `common` — raw `ResourceLocation` channels and `FriendlyByteBuf` codecs |
| All 12 mixins | `common` — target vanilla classes only |
| Menus, inventory, GUI, screens | `common` |
| `ILoaderProvider` (isClient, isModLoaded, getConfigDir) | `common` interface, `fabric`/`neoforge`/`folia` service impl |
| `FabricNetworking`, `FabricServerNetworking`, `FabricClientNetworking` | `fabric` |
| Historical NeoForge networking | `neoforge` — raw custom-payload events, same wire schema as Fabric/Folia |
| Folia S2C networking | `folia/FoliaPacketDistributor` — raw 1.20.1 custom-payload packets |
| Folia C2S dispatch (plugin-message → region-thread hop) | `folia/FoliaIncomingPayloadBridge` + `scheduler/PayloadDispatch` |
| Fabric attachment registration | `fabric/ModInit.java` |
| NeoForge attachment registration | `neoforge/EndInvNeoForge.java` + persistent Forge capabilities |
| Folia attachment registration | `folia/FoliaMenuRegistry` + Bukkit persistent player data |
| Fabric events (PlayerBlockBreakEvents, ServerTickEvents, etc.) | `fabric/event/` |
| NeoForge events (@SubscribeEvent on NeoForge.EVENT_BUS) | `neoforge/event/NeoForgeEvents.java` |
| Folia events (Bukkit @EventHandler + global region scheduler tick) | `folia/FoliaEventListeners` |
| Fabric screen events (ScreenEvents, ScreenKeyboardEvents, etc.) | `fabric/client/events/ScreenAttachment.java` |
| NeoForge screen events (ScreenEvent.*) | `neoforge/client/events/NeoForgeClientEvents.java` |

## Review Guidance

- Treat comments as claims, not proof. Verify behavior from the code path.
- Keep edits focused on the standalone mod and avoid reintroducing assumptions from the old multi-project workspace.
- The `common` module compiles against vanilla Minecraft with official mappings
  and must not import any loader API.
- Keep the 17 payload channel IDs and FriendlyByteBuf layouts identical on all
  three loaders; do not replace the historical NeoForge implementation with a
  numeric-discriminator `SimpleChannel`.
- The `folia` module compiles against server-only NMS (paperweight-userdev `foliaDevBundle`). It pulls common sources via the `commonServerJava` filtered Zip artifact (excludes `client/`, `mixin/`, `item/`, toClient payloads, `PageType`). Folia-local overrides in `folia/src/main/java/.../network/payloads/` and `menu/page/` replace the excluded files with server-safe versions.
