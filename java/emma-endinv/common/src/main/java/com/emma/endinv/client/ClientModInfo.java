package com.emma.endinv.client;

import com.emma.endinv.ModInfo;
import com.emma.endinv.client.gui.EndInvSettingScreen;
import com.emma.endinv.client.gui.EndlessInventoryScreen;
import com.emma.endinv.client.option.ClientConfigs;
import com.emma.endinv.network.payloads.toServer.OpenEndInvPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.function.Function;

public class ClientModInfo {

    public static IInputHandler inputHandler;

    public static IContainerScreenHelper containerScreenHelper;

    private static java.util.function.Function<Screen, Screen> configScreenFactory;

    public static void sendOpenMenu(){
        // Same resolution as EIMConfig.Param#adjust, so server menu, client menu and framework agree.
        int rows = fitMenuRows(ClientConfigs.EIM_CONFIG.Rows.get(), Minecraft.getInstance().getWindow().getGuiScaledHeight());
        ModInfo.getPacketDistributor().sendToServer(new OpenEndInvPayload(true, rows));
    }

    /**
     * Rows of the Endless Inventory menu that fit the scaled window, leaving room for the two
     * station-button rows drawn above the panel. {@code requested <= 0} means auto (as many as fit).
     */
    public static int fitMenuRows(int requested, int guiScaledHeight) {
        int maxRows = Math.max(1, Math.floorDiv(guiScaledHeight - EndlessInventoryScreen.BASE_IMAGE_HEIGHT
                - EndlessInventoryScreen.STATION_BAR_HEIGHT - 2, 18));
        return requested <= 0 ? maxRows : Math.max(1, Math.min(requested, maxRows));
    }

    public static void setConfigScreenFactory(Function<Screen, Screen> factory) {
        configScreenFactory = factory;
    }

    public static Screen createConfigScreen(Screen parent) {
        if (configScreenFactory != null) {
            Screen screen = configScreenFactory.apply(parent);
            if (screen != null) {
                return screen;
            }
        }
        return parent instanceof EndlessInventoryScreen ? new EndInvSettingScreen.Menu(parent) : new EndInvSettingScreen.Attachment(parent);
    }
}
