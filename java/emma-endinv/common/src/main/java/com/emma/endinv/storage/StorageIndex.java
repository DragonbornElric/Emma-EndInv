package com.emma.endinv.storage;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-wide database of tracked containers, persisted to {@code <world>/endinv_storage_index.dat}.
 *
 * <p>Kept in its own file rather than as SavedData so every loader persists it the same way: Folia never writes
 * SavedData during autosave, so each loader calls {@link #saveIfDirty} on a timer and {@link #unload} on shutdown.
 * Entries are immutable records in a concurrent map, so Folia region threads may update different containers
 * at the same time and the save thread can encode a consistent-enough view without locking.
 */
public final class StorageIndex {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String FILE_NAME = "endinv_storage_index.dat";
    private static final String LIST_KEY = "containers";

    public record Key(ResourceKey<Level> dimension, long pos) {}

    private static final Map<Key, TrackedContainer> ENTRIES = new ConcurrentHashMap<>();
    @Nullable private static volatile MinecraftServer server;
    private static volatile boolean dirty;

    private StorageIndex() {}

    // ── Lifecycle ───────────────────────────────────────────────────────────

    /** Load the index for this server; a no-op if it is already loaded for the same server. */
    public static synchronized void load(MinecraftServer mcServer) {
        if (server == mcServer) return;
        ENTRIES.clear();
        dirty = false;
        server = mcServer;
        Path file = file(mcServer);
        if (!Files.exists(file)) {
            LOGGER.info("Storage index: no {} yet, starting empty", FILE_NAME);
            return;
        }
        try {
            CompoundTag root = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
            var ops = mcServer.registryAccess().createSerializationContext(NbtOps.INSTANCE);
            Tag list = root.get(LIST_KEY);
            if (list != null) {
                TrackedContainer.CODEC.listOf().parse(ops, list)
                        .resultOrPartial(err -> LOGGER.warn("Storage index: skipped unreadable entries: {}", err))
                        .ifPresent(entries -> entries.forEach(c -> ENTRIES.put(c.key(), c)));
            }
            LOGGER.info("Storage index: loaded {} tracked containers", ENTRIES.size());
        } catch (Exception e) {
            LOGGER.error("Storage index: failed to read {}, starting empty (the file is left untouched until the next save)", file, e);
        }
    }

    /** Save (if dirty) and forget the loaded index. Call when the server stops. */
    public static synchronized void unload(MinecraftServer mcServer) {
        if (server != mcServer) return;
        saveIfDirty();
        ENTRIES.clear();
        server = null;
    }

    public static boolean isLoaded() {
        return server != null;
    }

    /**
     * Write the index if anything changed since the last save. Written to a temp file and moved into place so a
     * crash mid-write can't truncate it.
     */
    public static synchronized boolean saveIfDirty() {
        MinecraftServer mcServer = server;
        if (mcServer == null || !dirty) return false;
        dirty = false; // cleared before encoding so changes made during the write mark it dirty again
        try {
            var ops = mcServer.registryAccess().createSerializationContext(NbtOps.INSTANCE);
            CompoundTag root = new CompoundTag();
            root.put(LIST_KEY, TrackedContainer.CODEC.listOf().encodeStart(ops, java.util.List.copyOf(ENTRIES.values())).getOrThrow());
            Path file = file(mcServer);
            Path tmp = file.resolveSibling(FILE_NAME + ".tmp");
            NbtIo.writeCompressed(root, tmp);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return true;
        } catch (Exception e) {
            dirty = true;
            LOGGER.warn("Storage index: save failed, will retry: {}", e.toString());
            return false;
        }
    }

    private static Path file(MinecraftServer mcServer) {
        return mcServer.getWorldPath(LevelResource.ROOT).normalize().resolve(FILE_NAME);
    }

    // ── Entries ─────────────────────────────────────────────────────────────

    public static Collection<TrackedContainer> all() {
        return ENTRIES.values();
    }

    public static int size() {
        return ENTRIES.size();
    }

    @Nullable
    public static TrackedContainer get(Key key) {
        return ENTRIES.get(key);
    }

    public static void put(TrackedContainer container) {
        ENTRIES.put(container.key(), container);
        dirty = true;
    }

    @Nullable
    public static TrackedContainer remove(Key key) {
        TrackedContainer removed = ENTRIES.remove(key);
        if (removed != null) dirty = true;
        return removed;
    }
}
