package com.emma.endinv.client.gui.recipebook;

import com.emma.endinv.menu.EndlessInventoryMenu;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;

/**
 * Marker subclass used by the shared recipe-book mixin.
 *
 * <p>Minecraft 1.20.1's {@link RecipeBookComponent} derives its tabs, filter
 * textures, craft-grid dimensions and ghost-recipe layout from the active
 * {@code RecipeBookMenu}.  Keeping this as a distinct subclass lets the EndInv
 * mixin add the remote inventory contents and pin the panel without replacing
 * any of vanilla's 1.20.1 recipe-book behaviour.</p>
 */
public class EndInvCraftingRecipeBookComponent extends RecipeBookComponent
        implements EndInvRecipeBookComponent {

    public EndInvCraftingRecipeBookComponent(EndlessInventoryMenu menu) {
        super();
    }
}
