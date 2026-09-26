package com.emma.endinv.data;

import com.emma.endinv.EndlessInventory;
import com.emma.endinv.ModInfo;
import com.emma.endinv.ServerLevelEndInv;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.NonNullList;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;

import org.jetbrains.annotations.Nullable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.emma.endinv.data.EndInvCodecStrategy.END_INV_LIST_KEY;

public class EndlessInventoryData extends SavedData {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String PLAYER_SELECTIONS_KEY = "player_selections";
    private static final String KNOWN_NAMES_KEY = "known_player_names";
    private static final Codec<Map<UUID, UUID>> UUID_MAP_CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC, UUIDUtil.CODEC);
    private static final Codec<Map<UUID, String>> NAME_MAP_CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.STRING);

    public static final SavedDataType<EndlessInventoryData> DATA_TYPE = new SavedDataType<>(
            // The identifier of the saved data
            // Used as the path within the level's `data` folder
            Identifier.fromNamespaceAndPath("endless_inventory", END_INV_LIST_KEY),
            // The initial constructor
            EndlessInventoryData::new,
            // The codec used to serialize the data
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.list(EndlessInventory.CODEC).fieldOf(END_INV_LIST_KEY).forGetter(EID -> EID.levelEndInvs),
                    UUID_MAP_CODEC.optionalFieldOf(PLAYER_SELECTIONS_KEY, Map.of()).forGetter(EID -> EID.playerSelections),
                    NAME_MAP_CODEC.optionalFieldOf(KNOWN_NAMES_KEY, Map.of()).forGetter(EID -> EID.knownNames)
            ).apply(instance, (lst, selections, names) -> {
                var EID = new EndlessInventoryData();
                for(var endinv : lst){
                    // DEFAULT_UUID means "no EndInv" everywhere else (fromUUID, payloads), so an inventory saved
                    // with it can never be looked up by id. Re-key it; owners still find it via the owner fallback.
                    if(endinv.getUuid() == null || Objects.equals(endinv.getUuid(), ModInfo.DEFAULT_UUID)){
                        LOGGER.warn("EndInv owned by {} had the null UUID; assigned {}", endinv.getOwnerUUID(), endinv.giveNewUuid());
                    }
                    EID.addEndInvToLevel(endinv);
                }
                EID.playerSelections.putAll(selections);
                EID.knownNames.putAll(names);
                return EID;
            })),
            DataFixTypes.SAVED_DATA_MAP_DATA
    );

    public final NonNullList<EndlessInventory> levelEndInvs;

    /** Player UUID → EndInv UUID the player chose to use. Persisted so the choice survives restarts on every loader. */
    private final Map<UUID, UUID> playerSelections = new ConcurrentHashMap<>();

    /** Player UUID → last seen name, so owners and whitelists can be shown and edited while players are offline. */
    private final Map<UUID, String> knownNames = new ConcurrentHashMap<>();

    public record BackupResult(boolean success, @Nullable String message) {}

    private EndlessInventoryData(){
        this.levelEndInvs = NonNullList.create();
    }

    public static void init(ServerLevel level){
        if (!level.dimension().equals(Level.OVERWORLD)) {
            //LOGGER.warn("Skipped EndlessInventoryData initialization in dimension: {}", level.dimension().location());
            return; // 仅在主世界执行
        }

        ServerLevelEndInv.levelEndInvData = level.getDataStorage().computeIfAbsent(DATA_TYPE);

        LOGGER.info("Initialized EndlessInventoryData in {} with {} inventories", String.valueOf(level.dimension()), ServerLevelEndInv.levelEndInvData.levelEndInvs.size());
    }

    public static BackupResult backup(ServerLevel level) {
        try {
            Path worldDir = level.getServer().getWorldPath(LevelResource.ROOT).normalize();
            Path dataFile = findDataFile(level, worldDir);

            // 创建备份文件夹
            Path backupDir = worldDir.resolve("endinv_backup");
            Files.createDirectories(backupDir);

            // 添加时间戳到备份文件名
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            Path backupFile = backupDir.resolve("endless_inventories_" + timestamp + ".dat");

            // 复制文件
            Files.copy(dataFile, backupFile, StandardCopyOption.REPLACE_EXISTING);

            return new BackupResult(true, backupFile.toString());
        } catch (IOException e) {
            return new BackupResult(false, e.getMessage());
        } catch (Exception e) {
            return new BackupResult(false, "Unexpected exception");
        }
    }

    /**
     * Locate the saved file. 26.1 stores overworld SavedData under {@code dimensions/minecraft/overworld/data/<namespace>/};
     * the storage's own folder is read reflectively first so server forks with a different layout also work.
     */
    private static Path findDataFile(ServerLevel level, Path worldDir) throws FileNotFoundException {
        String relative = DATA_TYPE.id().getNamespace() + "/" + DATA_TYPE.id().getPath() + ".dat";
        List<Path> candidates = new ArrayList<>();
        try {
            Object storage = level.getDataStorage();
            for (java.lang.reflect.Field field : storage.getClass().getDeclaredFields()) {
                if (Path.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    candidates.add(((Path) field.get(storage)).resolve(relative));
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // fall back to the known layouts below
        }
        candidates.add(DimensionType.getStorageFolder(Level.OVERWORLD, worldDir).resolve("data").resolve(relative));
        candidates.add(worldDir.resolve("data").resolve(relative));
        candidates.add(worldDir.resolve("data/endless_inventories.dat"));
        for (Path candidate : candidates) {
            if (Files.exists(candidate)) return candidate;
        }
        throw new FileNotFoundException("Cannot find data file, looked in: " + candidates);
    }

    public static EndlessInventoryData create(){
        return new EndlessInventoryData();
    }

    public void addEndInvToLevel(EndlessInventory endlessInventory){
        levelEndInvs.add(endlessInventory);
        setDirty();
    }

    public void byIndexRemove(int index){
        if(index<0 || index>= levelEndInvs.size()) return;
        EndlessInventory removed = levelEndInvs.remove(index);
        playerSelections.values().removeIf(uuid -> Objects.equals(uuid, removed.getUuid()));
        setDirty();
    }

    @Nullable
    public UUID getSelection(UUID player){
        return playerSelections.get(player);
    }

    public void setSelection(UUID player, @Nullable UUID endInv){
        if(endInv == null) playerSelections.remove(player);
        else playerSelections.put(player, endInv);
        setDirty();
    }

    public void rememberName(UUID player, String name){
        if(!name.equals(knownNames.put(player, name))) setDirty();
    }

    @Nullable
    public String getKnownName(@Nullable UUID player){
        return player == null ? null : knownNames.get(player);
    }

    @Nullable
    public UUID findKnownPlayer(String name){
        for(var entry : knownNames.entrySet()){
            if(entry.getValue().equalsIgnoreCase(name)) return entry.getKey();
        }
        return null;
    }

    /** Players (by UUID) whose saved selection points at the given EndInv. */
    public List<UUID> playersUsing(UUID endInv){
        List<UUID> result = new ArrayList<>();
        playerSelections.forEach((player, selected) -> {
            if(Objects.equals(selected, endInv)) result.add(player);
        });
        return result;
    }
    @Nullable
    public EndlessInventory fromUUID(@Nullable UUID uuid){
        if(uuid == null || Objects.equals(uuid, ModInfo.DEFAULT_UUID)) return null;
        for(EndlessInventory endlessInventory : levelEndInvs){
            if (Objects.equals(endlessInventory.getUuid(),uuid)) return endlessInventory;
        }
        return null;
    }

    @Nullable
    public EndlessInventory fromIndex(int index){
        if(index<0 || index>= levelEndInvs.size()) return null;
        return levelEndInvs.get(index);
    }

    public int getIndex(EndlessInventory endlessInventory) {
        int index = 0;
        for(EndlessInventory endinv : levelEndInvs){
            if(Objects.equals(endlessInventory,endinv)) return index;
            index++;
        }
        return -1;
    }
}
