package com.emma.endinv.client.gui.recipebook;

import com.emma.endinv.menu.EndlessInventoryMenu;
import com.emma.endinv.menu.Station;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;

/**
 * 1.20.1 recipe-book component for the three EndInv cooking stations.
 *
 * <p>The vanilla component reads the active {@code RecipeBookType} and slot
 * geometry from {@link EndlessInventoryMenu}, so the station switch only needs
 * a fresh marked component after the menu changes its active station.</p>
 */
public class EndInvCookingRecipeBookComponent extends RecipeBookComponent
        implements EndInvRecipeBookComponent {

    private final Station station;

    private EndInvCookingRecipeBookComponent(EndlessInventoryMenu menu, Station station) {
        super();
        this.station = station;
    }

    public static EndInvCookingRecipeBookComponent forStation(EndlessInventoryMenu menu, Station station) {
        if (!station.isCooking()) {
            throw new IllegalArgumentException("Not a cooking station: " + station);
        }
        return new EndInvCookingRecipeBookComponent(menu, station);
    }

    public Station station() {
        return station;
    }
}
