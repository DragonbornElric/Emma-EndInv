package com.emma.endinv.manage;

import com.emma.endinv.EndlessInventory;
import com.emma.endinv.ServerLevelEndInv;
import com.emma.endinv.util.ItemKey;
import com.emma.endinv.util.ItemState;
import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Per-inventory safety copies taken before destructive manager actions (take, move, clear, delete).
 * Unlike {@code /endinv backup}, which copies the last saved file, these capture the live in-memory
 * state at the moment of the action. Stored under {@code <world>/endinv_backup/snapshots/}.
 */
public final class EndInvSnapshots {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final String KEY = com.emma.endinv.ModInfo.LEGACY_ID; // matches the saved-data key

    private EndInvSnapshots() {}

    public static Path directory(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).normalize().resolve("endinv_backup").resolve("snapshots");
    }

    /** @return the snapshot file name, or null if writing failed. */
    public static String save(MinecraftServer server, EndlessInventory endInv, String reason) {
        try {
            var ops = server.registryAccess().createSerializationContext(NbtOps.INSTANCE);
            Tag tag = EndlessInventory.CODEC.encodeStart(ops, endInv).getOrThrow();
            CompoundTag root = new CompoundTag();
            root.put(KEY, tag);
            Path dir = directory(server);
            Files.createDirectories(dir);
            String name = LocalDateTime.now().format(STAMP) + "_" + reason + "_" + endInv.getUuid().toString().substring(0, 8) + ".dat";
            NbtIo.writeCompressed(root, dir.resolve(name));
            LOGGER.info("EndInv snapshot {} written for {} ({} item types)", name, endInv.getUuid(), endInv.getItemMap().size());
            return name;
        } catch (Exception e) {
            LOGGER.error("Failed to write EndInv snapshot for {}", endInv.getUuid(), e);
            return null;
        }
    }

    public static List<String> list(MinecraftServer server) {
        Path dir = directory(server);
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> files = Files.list(dir)) {
            return files.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".dat")).sorted().toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    /**
     * Adds the snapshot's items back. If the inventory still exists (same UUID) the items are added to it;
     * otherwise the whole inventory is re-created. Adding (not overwriting) means restoring after a move
     * duplicates the moved items — the caller is expected to know which snapshot they are restoring.
     */
    public static String restore(MinecraftServer server, String fileName) {
        if (ServerLevelEndInv.levelEndInvData == null) return "EndInv data is not loaded.";
        if (fileName.contains("/") || fileName.contains("\\") || fileName.contains("..")) return "Invalid snapshot name.";
        Path file = directory(server).resolve(fileName);
        if (!Files.isRegularFile(file)) return "No snapshot named " + fileName;
        try {
            CompoundTag root = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
            var ops = server.registryAccess().createSerializationContext(NbtOps.INSTANCE);
            Tag tag = root.get(KEY);
            if (tag == null) return "Snapshot " + fileName + " is empty.";
            EndlessInventory snapshot = EndlessInventory.CODEC.parse(ops, tag).getOrThrow();
            EndlessInventory existing = ServerLevelEndInv.levelEndInvData.fromUUID(snapshot.getUuid());
            if (existing == null) {
                ServerLevelEndInv.levelEndInvData.addEndInvToLevel(snapshot);
                return "Re-created EndInv " + snapshot.getUuid() + " from " + fileName;
            }
            long notRestored = 0;
            for (Map.Entry<ItemKey, ItemState> entry : snapshot.getItemMap().entrySet()) {
                notRestored += existing.addItem(entry.getKey(), entry.getValue().count()).getCount();
            }
            existing.setChanged();
            return "Restored items from " + fileName + " into " + existing.getUuid()
                    + (notRestored > 0 ? " (" + notRestored + " items did not fit)" : "");
        } catch (Exception e) {
            LOGGER.error("Failed to restore EndInv snapshot {}", fileName, e);
            return "Failed to restore " + fileName + ": " + e.getMessage();
        }
    }
}
