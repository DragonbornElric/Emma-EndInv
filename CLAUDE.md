# Emma-EndInv

Multi-loader repository for the Emma fork of Endless Inventory — Fabric, NeoForge, and Folia, targeting Minecraft 26.2.

**Branches (since 2026-10-02):** `main` is the Minecraft 26.2 line (owner: all mods proven on 26.2). 26.1.2 is frozen as branch `mc-26.1.2` / tag `archive/26.1.2-final` (no further updates). `mc-26.3` is the 26.3 port; changes flow one way, `main` → `mc-26.3`.

## Loader roles

- **Fabric** and **NeoForge** jars are one mod for both sides: install the same jar on the client and on a Fabric/NeoForge dedicated server.
- **Folia** jar is the server-only half: a Folia plugin that serves Fabric/NeoForge clients running the client jar.

## Scope

- This repository contains only the Emma-EndInv mod.
- The previous multi-project workspace, sibling mods, Python tooling, and deployment scripts are intentionally not part of this repository.

## Layout

```
java/emma-endinv/
├── buildSrc/            Convention plugins (multiloader-common, multiloader-loader, multiloader-folia)
├── common/              Loader-neutral code: all mixins, payloads, menus, GUI, service interfaces
├── fabric/              Fabric entry points, FabricNetworking, Fabric events, fabric.mod.json
├── neoforge/            NeoForge entry point, NeoForgeNetworking, @SubscribeEvent handlers, neoforge.mods.toml
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
| `neoforge/src/main/resources` | `neoforge.mods.toml`, NeoForge mixin JSON, AT, service files |
| `folia/src/main/java` | Folia plugin code (EndInvFoliaPlugin, adapters, scheduler helpers, payload overrides) |
| `folia/src/main/resources` | `paper-plugin.yml`, `META-INF/services/ILoaderProvider` |
| `java/emma-endinv/gradlew`, `gradlew.bat` | Gradle wrapper |
| `java/build_and_deploy.sh` | Build helper — produces jars and deploys |
| `java/emma-endinv/PORTING_NOTES.md` | Notes for the Fabric 26.1 port |

## Build

Use Java 25.

**Fabric only:**
```bash
cd java/emma-endinv
./gradlew.bat :fabric:build
```

On Linux use `./gradlew -Dorg.gradle.java.home=<any JDK 21+> ...`: `gradle.properties` pins
`org.gradle.java.home` to a Windows path (the Java 25 compile toolchain is provisioned by
Gradle).

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
- `fabric/build/libs/emma_endinv-fabric-<mc_version>*.jar`
- `neoforge/build/libs/emma_endinv-neoforge-<mc_version>*.jar`
- `folia/build/libs/emma_endinv-folia-<folia_version>*.jar`

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

The Fabric jar deploys automatically to the default PrismLauncher Emma, Elric, and CameraBot26.1 instances.
The Folia jar is staged in `dist/` but not auto-deployed (server install paths vary).

## Server targets

The Fabric jar is a client **and** server mod: it runs on a Fabric dedicated server as well
as on the client (verified on a Fabric 26.1.2 dedicated server, 2026-09-29, with Emma's bridge
reading and filling EndInv). Folia and Paper servers use the Folia plugin instead (the same jar runs on both; tested on Paper too). The NeoForge
jar is built as a client/server mod too but has not been tested on a dedicated server yet.
Don't describe the server side as "Folia only".

Fabric-server parity with the Folia plugin (death loot and XP, pickup filter, EndInv-first recipe
book, 60 s save, respawn re-sync) is listed in README "Fabric server behaves like the Folia
plugin". The death-loot mixins are Fabric-only (`fabric/.../mixin/fabric/`, registered in
`emma_endinv.fabric.mixins.json`). NeoForge buffers death drops in `captureDrops`
before they reach the level, so its port lives in `NeoForgeEvents` (`LivingDropsEvent`,
`LivingExperienceDropEvent`, `PlayerRespawnEvent`, 60 s save on `ServerTickEvent.Post`); untested
on a dedicated server.

## Conventions

- Minecraft 26.2
- Fabric Loader 0.19.5 / Fabric API 0.161.0+26.2
- NeoForge 26.2.0.88 (pin deliberately before upgrading)
- Folia 26.2.build.7-beta (paperweight-userdev 2.0.0-SNAPSHOT, `foliaDevBundle`)
- Fabric Loom 1.15.5 / ModDevGradle 2.0.141
- Java 25
- Package namespace: `com.emma.endinv`
- Mod id `emma_endinv` (since 1.4.0; `ModInfo.MOD_ID`). The upstream id `endless_inventory` (`ModInfo.LEGACY_ID`) is still used on purpose for saved data (world EndInv file, snapshots, Fabric/NeoForge player attachments) and the Storage Tag translation keys, so existing worlds load unchanged. Item ids are aliased from it; config files are migrated by `ConfigFiles`. Don't "clean up" those legacy uses.
- Loader-neutral service abstraction: `ILoaderProvider` (ServiceLoader, one impl per module)
- Mixin compatibility: `JAVA_25`, `defaultRequire: 1`
- Folia threading: no `Bukkit.getScheduler()`; use `entity.getScheduler()`, `getGlobalRegionScheduler()`, `getRegionScheduler()`

## Loader Coupling Map

| Surface | Module |
|---------|--------|
| All payloads | `common` — vanilla `CustomPacketPayload`/`StreamCodec` only |
| Shared mixins | `common` — target vanilla classes only |
| Death-loot capture (drops + XP to the killer's EndInv) | `fabric/mixin/fabric/` + `fabric/event/DeathLootCapture` |
| Menus, inventory, GUI, screens | `common` |
| `ILoaderProvider` (isClient, isModLoaded, getConfigDir) | `common` interface, `fabric`/`neoforge`/`folia` service impl |
| `FabricNetworking`, `FabricServerNetworking`, `FabricClientNetworking` | `fabric` |
| `NeoForgeNetworking` (RegisterPayloadHandlersEvent) | `neoforge` |
| Folia S2C codec injection (`GAMEPLAY_STREAM_CODEC` via Unsafe) | `folia/FoliaPayloadRegistry` |
| Folia C2S dispatch (plugin-message → region-thread hop) | `folia/FoliaIncomingPayloadBridge` + `scheduler/PayloadDispatch` |
| Fabric attachment registration | `fabric/ModInit.java` |
| NeoForge attachment registration | `neoforge/EndInvNeoForge.java` (DeferredRegister) |
| Folia attachment registration (in-memory per-UUID map) | `folia/FoliaMenuRegistry` |
| Fabric events (PlayerBlockBreakEvents, ServerTickEvents, etc.) | `fabric/event/` |
| NeoForge events (@SubscribeEvent on NeoForge.EVENT_BUS) | `neoforge/event/NeoForgeEvents.java` |
| Folia events (Bukkit @EventHandler + global region scheduler tick) | `folia/FoliaEventListeners` |
| Fabric screen events (ScreenEvents, ScreenKeyboardEvents, etc.) | `fabric/client/events/ScreenAttachment.java` |
| NeoForge screen events (ScreenEvent.*) | `neoforge/client/events/NeoForgeClientEvents.java` |
| Storage tracker logic, index, payloads, `/storage` command | `common/storage/` (loader-neutral; payloads live here so the Folia build includes them) |
| Storage tracker hooks (use-block, 1 s poll, load/save) | `fabric/event/StorageEvents.java`, `neoforge/event/NeoForgeStorageEvents.java`, `folia/FoliaStorageTracker.java` (per-chunk region polling + Bukkit recipe) |

## Public API

`com.emma.endinv.api` (`EmmaEndInvApi` client, `EmmaEndInvServerApi` server, incl. Folia) is the public API, documented in `docs/API.md`. Keep it source-compatible within minor versions; update the doc when it changes.

## Testing

In-game test results and methodology are in `TESTING.md` (Pelican test servers for every loader and MC version; last run 2026-09-29, 1.4.1). Update it and the README "Test results" section when re-testing. Harness scripts live outside the repo (author's home lab).

## Review Guidance

- Treat comments as claims, not proof. Verify behavior from the code path.
- Keep edits focused on the standalone mod and avoid reintroducing assumptions from the old multi-project workspace.
- Update the `neoforge_version` and `folia_version` pins in `gradle.properties` deliberately (Folia 26.2 is still a beta build).
- The `common` module compiles against vanilla MC only (NeoForm mode) — it must NOT import any loader API.
- The `folia` module compiles against server-only NMS (paperweight-userdev `foliaDevBundle`). It pulls common sources via the `commonServerJava` filtered Zip artifact (excludes `client/`, `mixin/`, `item/`, toClient payloads, `PageType`). Folia-local overrides in `folia/src/main/java/.../network/payloads/` and `menu/page/` replace the excluded files with server-safe versions.
