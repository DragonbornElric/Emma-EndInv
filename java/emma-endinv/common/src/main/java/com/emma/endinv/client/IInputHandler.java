package com.emma.endinv.client;

import com.emma.endinv.AbstractClientModInitializer;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;

public interface IInputHandler {

    default boolean isActiveAndMatches(KeyMappings.KeyParam keyParam, InputConstants.Key input){
        AbstractClientModInitializer modClient = AbstractClientModInitializer.ENDINV_CLIENT;
        if(modClient == null){
            throw new IllegalStateException("Client mod not initialized");
        }
        if(!keyParam.condition().isActive()) return false;
        if(keyParam.modifier() == KeyMappings.Modifier.CTRL && !Screen.hasControlDown()) return false;
        KeyMapping mapping = modClient.KEY_MAPPING_MAP.get(keyParam);
        if (mapping == null) return false;
        if (input.getType() == InputConstants.Type.MOUSE) {
            return mapping.matchesMouse(input.getValue());
        }
        return mapping.matches(input.getValue(), input.getValue());
    }

}
