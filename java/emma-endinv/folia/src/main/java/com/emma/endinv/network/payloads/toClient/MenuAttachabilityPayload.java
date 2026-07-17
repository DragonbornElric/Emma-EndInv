package com.emma.endinv.network.payloads.toClient;

import com.emma.endinv.network.payloads.ModPacketContext;
import com.emma.endinv.network.payloads.ModPacketPayload;
import com.emma.endinv.options.SpecifiedMenuAttachingConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;

import java.util.HashMap;
import java.util.Map;

public record MenuAttachabilityPayload(
        boolean defaultAttach,
        boolean inventoryAttach,
        Map<MenuType<?>, Boolean> perMenu
) implements ModPacketPayload {

    public static MenuAttachabilityPayload of(boolean defaultAttach, SpecifiedMenuAttachingConfig config) {
        return new MenuAttachabilityPayload(defaultAttach, config.isInventoryAttachable(), new HashMap<>(config.getConfigs()));
    }

    public static void encode(MenuAttachabilityPayload payload, FriendlyByteBuf buf) {
        buf.writeBoolean(payload.defaultAttach);
        buf.writeBoolean(payload.inventoryAttach);
        int encodableEntries = 0;
        for (MenuType<?> menuType : payload.perMenu.keySet()) {
            if (BuiltInRegistries.MENU.getKey(menuType) != null) {
                encodableEntries++;
            }
        }
        buf.writeVarInt(encodableEntries);
        for (var e : payload.perMenu.entrySet()) {
            ResourceLocation id = BuiltInRegistries.MENU.getKey(e.getKey());
            if (id == null) continue;
            buf.writeResourceLocation(id);
            buf.writeBoolean(Boolean.TRUE.equals(e.getValue()));
        }
    }

    public static MenuAttachabilityPayload decode(FriendlyByteBuf buf) {
        boolean def = buf.readBoolean();
        boolean inv = buf.readBoolean();
        int size = buf.readVarInt();
        Map<MenuType<?>, Boolean> map = new HashMap<>();
        for (int i = 0; i < size; i++) {
            ResourceLocation id = buf.readResourceLocation();
            boolean val = buf.readBoolean();
            MenuType<?> type = BuiltInRegistries.MENU.get(id);
            if (type != null) {
                map.put(type, val);
            }
        }
        return new MenuAttachabilityPayload(def, inv, map);
    }

    @Override
    public String id() { return "menu_attachability"; }

    @Override
    public void write(FriendlyByteBuf buffer) { encode(this, buffer); }

    @Override
    public void handle(ModPacketContext context) {}
}
