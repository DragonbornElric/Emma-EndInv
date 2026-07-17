# Emma-EndInv 1.20.1 Backport Notes

`mc-1.20.1` is a complete backport of Emma-EndInv. It is maintained beside
the Minecraft 26.1.2 `main` branch; it is not a reduced compatibility mod.

## Source anchors

- Emma 26.1.2 feature baseline: commit
  `8749696f2186e8c33cec337b89bdad1aae4393d8`
- Historical upstream 1.20.1 API reference:
  `kwwsyk/Endless-Inventory` commit
  `96bd1efd25ce44dd5e91226ebbdcf6eba0ab7503`

The upstream checkout was used only to confirm historical Minecraft APIs.
Emma's current behavior remains authoritative, including the expanded station
UI, auto-pick behavior, server persistence, packet surface, and recipe-book
integration.

## Runtime targets

| Target | Minecraft/API | Java |
| --- | --- | --- |
| Fabric | Minecraft 1.20.1, Fabric API 0.92.11 | 17 |
| NeoForge compatibility build | Forge 47 API (`1.20.1-47.1.106`) | 17 |
| Folia | Folia 1.20.1 | 17 |

NeoForge's historical 1.20.1 releases used the Forge 47 package/API boundary,
so the module intentionally imports `net.minecraftforge.*` and packages
`META-INF/mods.toml`.

## Backported architecture

- Vanilla Gradle multiloader layout: `common`, `fabric`, `neoforge`, `folia`
- Official Mojang mappings
- Raw `ResourceLocation`/`FriendlyByteBuf` custom-payload protocol
- 10 client-to-server directions and 7 server-to-client directions
- 16 unique channel IDs; `endinv_settings` is bidirectional
- Fabric persistent player NBT through a player-data mixin
- Forge 47 persistent player capabilities
- Folia persistent player data plus region-thread scheduling
- Shared SavedData storage for Endless Inventory contents

Minecraft 1.20.1 predates the typed `CustomPacketPayload`/`StreamCodec`
networking used on 26.1.2. Each loader adapts the same common raw field layout
without adding loader-specific discriminators.

## Feature parity

The branch retains:

- inventory pages, search, sorting, starred items, and screen attachment
- crafting and recipe-book placement using normal inventory plus EndInv
- furnace, smoker, blast furnace, stonecutter, grindstone, smithing, and
  brewing stations
- auto-pick for blocks, entities, experience, and item pickup
- per-player ownership, access settings, synced settings, and persistence
- Fabric, NeoForge/Forge 47, and Folia networking and lifecycle integration

## Build and verification

```powershell
cd java\emma-endinv
.\gradlew.bat :common:test :fabric:build :neoforge:build :folia:build
```

Production artifacts are created under each loader's `build/libs` directory.
For Folia, deploy the reobfuscated production jar, not the `-dev.jar`.

The repository-level `java/build_and_deploy.sh` reads `minecraft_version` and
refuses to place 1.20.1 artifacts into paths marked for 26.1. It stages the
backport only in versioned 1.20.1 target directories.
