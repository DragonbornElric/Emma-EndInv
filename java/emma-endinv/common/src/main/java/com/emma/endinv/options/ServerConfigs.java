package com.emma.endinv.options;

import com.emma.endinv.options.config.ConfigEntryImpl;
import com.emma.endinv.options.config.IConfigValue;

import java.util.ArrayList;
import java.util.List;

public final class ServerConfigs {

    /// Implementations:
    /// @see com.emma.endinv.neoforge.options.ServerConfig
    /// @see com.emma.endinv.forge.ServerConfig
    /// @see com.emma.endinv.ModInit

    private ServerConfigs(){}

    private static final String[] CMT_CREATION_MODE = {
            "The default behavior of creating an end inventory when a player without endinv opens a container. Values: ",
            "CREATE_PER_PLAYER : create for every individual player",
            "USE_GLOBAL_SHARED : create only once and all players use that endinv",
            "NONE : don't create an endinv. You can customize endinv obtain condition to improve challenge." //such as with commands
    };

    private static final List<ConfigEntryImpl<?>> forgeConfigEntries = new ArrayList<>();

    private static <T extends IConfigValue<?>> T register(T configEntry){
        if(configEntry instanceof ConfigEntryImpl<?> entry){
            forgeConfigEntries.add(entry);
        }
        return configEntry;
    }

    public static List<ConfigEntryImpl<?>> getConfigs(){
        return forgeConfigEntries;
    }

    public static final ConfigEntryImpl<CreationEndinvStrategy> CREATION_MODE = register(new ConfigEntryImpl.EnumEntry<>("EndinvCreationMode", CMT_CREATION_MODE, CreationEndinvStrategy.CREATE_PER_PLAYER));
    public static final ConfigEntryImpl<Boolean> DEFAULT_ATTACH = register(new ConfigEntryImpl.BooleanEntry("DefaultAttach", new String[]{"Whether the endless inventory's display pages view is attached by default"}, true));
    public static final DefaultEndinvBehavior ENDINV_BEHAVIOR = register(DefaultEndinvBehavior.INSTANCE);
    public static final SpecifiedMenuAttachingConfig.Entry SPECIFIED_ATTACHABILITY = register(SpecifiedMenuAttachingConfig.Entry.INSTANCE);
    public static final ConfigEntryImpl<Boolean> ENABLE_AUTOPICK = register(new ConfigEntryImpl.BooleanEntry("EnableAutoPick", new String[]{"Whether the endless inventory will auto pick up items when they are dropped"}, true));
    public static final ConfigEntryImpl<Boolean> AUTOPICK_MENDING_FIRST = register(new ConfigEntryImpl.BooleanEntry("AutoPickMendingFirst", new String[]{"Auto-picked XP (mining, kills) repairs Mending gear first, like a vanilla orb; the rest goes to levels. Off: all of it goes to levels"}, true));
    public static final ConfigEntryImpl<Boolean> CRAFTING_STATIONS = register(new ConfigEntryImpl.BooleanEntry("CraftingStations", new String[]{
            "Whether the EndInv screen has crafting stations (crafting devices) at all.",
            "false: the station buttons are hidden and an icon in their place tells players to ask an admin to enable them; nothing a player put in is lost, it comes back when this is turned on again."}, true));
    public static final ConfigEntryImpl<Boolean> FREE_CRAFTING_STATIONS = register(new ConfigEntryImpl.BooleanEntry("FreeCraftingStations", new String[]{
            "Whether every Endless Inventory has all crafting stations (crafting table, furnace, smoker, blast furnace, stonecutter, grindstone, smithing table, brewing stand) for free.",
            "false: each station's button on the EndInv screen is locked until a player puts that block (crafted or found) into it; the block is used up and the station stays unlocked for that EndInv."}, true));
    public static final ConfigEntryImpl<Boolean> DROP_STATIONS_ON_DEATH = register(new ConfigEntryImpl.BooleanEntry("DropStationsOnDeath", new String[]{
            "Whether a player who dies drops the station blocks put into their EndInv (when FreeCraftingStations is false) and the enchanting station's bookshelves, which then have to be picked up again or rebuilt.",
            "false: they are kept on death. The keepInventory game rule keeps them too."}, false));
    public static final ConfigEntryImpl.ListEntry<String> ADMINS = register(new ConfigEntryImpl.ListEntry<>("Admins", new String[]{
            "Player names or UUIDs allowed to manage every Endless Inventory (view, share, select, take, move, clear, delete).",
            "If empty, operators with permission level 4 (server owners) are admins."}, new ArrayList<>()));
    public static final ConfigEntryImpl<Boolean> ENABLE_CRAFT_DEBUG_LOG = register(new ConfigEntryImpl.BooleanEntry("EnableCraftDebugLog", new String[]{"Whether to emit [CRAFT-DBG] crafting/inventory trace logs on the server (Folia)."}, false));
}
