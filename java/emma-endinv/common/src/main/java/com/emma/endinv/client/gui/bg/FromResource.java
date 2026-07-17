package com.emma.endinv.client.gui.bg;

import com.emma.endinv.ModInfo;
import com.emma.endinv.client.gui.ScreenFramework;
import com.emma.endinv.client.option.ClientConfigs;
import com.emma.endinv.client.option.TextureMode;
import com.emma.endinv.menu.EndlessInventoryMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public abstract class FromResource extends SFBgRendererImpl {


    public static final ResourceLocation CONTAINER_TEXTURE_RESOURCE = new ResourceLocation("textures/gui/container/generic_54.png");
    //1.20.1:public static final Identifier TABS_RESOURCE = Identifier.withDefaultNamespace("textures/gui/advancements/tabs.png");

    public static final ResourceLocation DEDICATED_CONTAINER_TEXTURE = new ResourceLocation(ModInfo.MOD_ID, "textures/gui/item_grid.png");
    //1.20.1:public static final Identifier DEDICATED_TABS = Identifier.fromNamespaceAndPath(ModInfo.MOD_ID, "textures/gui/tabs.png");/*_old*/
    public static final ResourceLocation ITEM_ENTRY_DISPLAY_RESOURCE = new ResourceLocation(ModInfo.MOD_ID, "textures/gui/item_entry.png");

    private static ResourceLocation getContainerTexture(){
        return ClientConfigs.ATTACHED_MENU_CONFIG.TextureMode.get() == TextureMode.DEDICATED_LOCATION ? DEDICATED_CONTAINER_TEXTURE : CONTAINER_TEXTURE_RESOURCE;
    }

    public FromResource(ScreenFramework frameWork){
        super(frameWork);
    }

    public static class MenuMode extends FromResource{

        public MenuMode(ScreenFramework frameWork, IRectangleParam pageSwitchTabParam) {
            super(frameWork);
            this.pageSwitchTabParam = pageSwitchTabParam;
        }

        @Override
        public void renderBg(@NotNull GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
            super.renderBg(guiGraphics, partialTick, mouseX, mouseY);

            int baseRows = frameWork.menu instanceof EndlessInventoryMenu endless ? endless.getBaseRows() : rows;

            int startY = menuTop + 17 + baseRows*18;
            renderPlayerInv(guiGraphics,partialTick,mouseX,mouseY,menuLeft,startY);
        }

        private void renderPlayerInv(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY, int startX, int startY){
            guiGraphics.blit(getContainerTexture(), startX, startY,
                    0.0F, 126.0F, imageWidth, 96, 256, 256);
        }
    }

    public static class LeftLayout extends FromResource{

        public LeftLayout(ScreenFramework frameWork, IRectangleParam pageSwitchTabParam){
            super(frameWork);
            this.pageSwitchTabParam = pageSwitchTabParam;
        }
    }

    public abstract class PagePainter implements PageBgRender {

        public abstract ResourceLocation texture();

        @Override
        public void renderBg(@NotNull GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
            int startX = frameWork.getPageX();
            int startY = frameWork.getPageY();
            int columns = frameWork.columns();
            int rows = frameWork.rows();

            if(columns!=9){
                renderSpecialBg(guiGraphics,partialTick,mouseX,mouseY,startX,startY);
            }else {
                guiGraphics.blit(texture(), startX, startY, 0.0F, 0.0F, imageWidth, 17, 256, 256);
                startY += 17;
                int rowsToRender = rows;
                while (rowsToRender > 0) {
                    int height = 18 * Math.min(rowsToRender, 6);
                    guiGraphics.blit(texture(), startX, startY,
                            0.0F, 17.0F, imageWidth, height, 256, 256);
                    rowsToRender -= 6;
                    startY += height;
                }
                guiGraphics.blit(texture(), startX, frameWork.getPageY() + 17 + 18 * rows, 0.0F, 124.0F, imageWidth, 12, 256, 256);
            }

        }

        private void renderSpecialBg(@NotNull GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY,
                                     int startX, int startY) {
            int initialX = startX;
            int columns = frameWork.columns();
            int rows = frameWork.rows();

            guiGraphics.blit(texture(), startX, startY, 0.0F, 0.0F,
                    7, 17, 256, 256);
            startX+=7;
            for (int columnsToRender = columns;columnsToRender>0;columnsToRender-=9) {
                int width = 18 * Math.min(9,columnsToRender);
                guiGraphics.blit(texture(), startX, startY, 7.0F, 0.0F,
                        width, 17, 256,256);

                startX+=width;
            }
            guiGraphics.blit(texture(), startX, startY, 168.0F, 0.0F,
                    8, 17, 256, 256);
            startX = initialX;

            startY+=17;
            for (int rowsToRender = rows;rowsToRender > 0;rowsToRender -= 6) {
                int height = 18*Math.min(rowsToRender,6);


                guiGraphics.blit(texture(), startX, startY, 0.0F, 17.0F,
                        7, height, 256, 256);
                startX+=7;
                for (int columnsToRender = columns;columnsToRender>0;columnsToRender-=9) {
                    int width = 18 * Math.min(9,columnsToRender);
                    guiGraphics.blit(texture(), startX, startY, 7.0F, 17.0F,
                            width, height, 256, 256);
                    startX+=width;
                }
                guiGraphics.blit(texture(), startX, startY, 168.0F, 17.0F,
                        8, height, 256, 256);
                startX = initialX;
                startY += height;
            }

            guiGraphics.blit(texture(), startX, startY, 0.0F, 124.0F,
                    7, 12, 256, 256);
            startX+=7;
            for (int columnsToRender = columns;columnsToRender>0;columnsToRender-=9) {
                int width = 18 * Math.min(9,columnsToRender);
                guiGraphics.blit(texture(), startX, startY, 7.0F, 124.0F,
                        width, 12, 256, 256);

                startX+=width;
            }
            guiGraphics.blit(texture(), startX, startY, 168.0F, 124.0F,
                    8, 12, 256, 256);
            startX = initialX;
        }
    }

    @Override
    public Optional<PageBgRender> getDefaultPageBgRenderer() {
        return Optional.of(new PagePainter(){

            @Override
            public ResourceLocation texture() {
                return getContainerTexture();
            }
        });
    }

    public PagePainter dedicatePageBgRender(ResourceLocation texture){
        return new PagePainter() {
            @Override
            public ResourceLocation texture() {
                return texture;
            }
        };
    }

    @Override
    public void renderBg(@NotNull GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
    }
}
