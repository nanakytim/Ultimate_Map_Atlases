package net.nanaky.ultimate_map_atlases.client.screen;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.nanaky.ultimate_map_atlases.MapAtlasesMod;
import net.nanaky.ultimate_map_atlases.client.CompoundTooltip;
import net.nanaky.ultimate_map_atlases.client.MapAtlasesClient;
import net.nanaky.ultimate_map_atlases.config.MapAtlasesClientConfig;
import net.nanaky.ultimate_map_atlases.integration.moonlight.ClientMarkers;
import net.nanaky.ultimate_map_atlases.integration.moonlight.CustomDecorationButton;
import net.nanaky.ultimate_map_atlases.integration.moonlight.MoonlightCompat;
import net.nanaky.ultimate_map_atlases.networking.C2SRemoveMarkerPacket;
import net.nanaky.ultimate_map_atlases.networking.MapAtlasesNetworking;
import net.nanaky.ultimate_map_atlases.utils.DecorationHolder;
import net.nanaky.ultimate_map_atlases.utils.MapDataHolder;

import java.util.Locale;
import java.util.Map;

import static net.nanaky.ultimate_map_atlases.client.AbstractAtlasWidget.MAP_DIMENSION;
import static net.nanaky.ultimate_map_atlases.client.MapAtlasesClient.ATLAS_BACKGROUND_TEXTURE;

public abstract class DecorationBookmarkButton extends BookmarkButton {

    private static final int BUTTON_H = 14;
    private static final int BUTTON_W = 24;
    protected final MapDataHolder mapData;
    protected final String decorationId;

    protected int index = 0;
    protected boolean shfting = false;
    protected boolean control = false;

    protected DecorationBookmarkButton(int pX, int pY, AtlasOverviewScreen parentScreen, MapDataHolder data, String id) {
        super(pX - BUTTON_W, pY, BUTTON_W, BUTTON_H, 0, 167 + 36, parentScreen);
        this.mapData = data;
        this.decorationId = id;
        this.shfting = AtlasOverviewScreen.isShiftDown();
        this.control = AtlasOverviewScreen.isControlDown();
    }

    public static DecorationBookmarkButton of(int px, int py, DecorationHolder holder, AtlasOverviewScreen screen) {
        if (holder.deco() instanceof MapDecoration md && !MoonlightCompat.isCustomDecoration(holder.id(), holder.deco()))
            return new Vanilla(px, py, screen, holder.data(), md, holder.id());
        else {
            return CustomDecorationButton.create(px, py, screen, holder.data(), holder.deco(), holder.id());
        }
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        this.shfting = AtlasOverviewScreen.isShiftDown();
        this.control = AtlasOverviewScreen.isControlDown();
        this.setTooltip(this.createTooltip());
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        this.shfting = AtlasOverviewScreen.isShiftDown();
        this.control = AtlasOverviewScreen.isControlDown();
        this.setTooltip(this.createTooltip());
        return false;
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        this.setSelected(true);
        if (control && canDeleteMarker()) {
            this.deleteMarker();
            parentScreen.recalculateDecorationWidgets();
        } else if (shfting) {
            parentScreen.togglePriority(getPriorityKey());
        } else {
            parentScreen.centerOnDecoration(this);
        }
    }

    protected abstract void deleteMarker();

    public abstract double getWorldX();

    public abstract double getWorldZ();

    public abstract Component getDecorationName();

    public String getPriorityKey() {
        return decorationId;
    }

    protected static double getDecorationPos(int decoX, MapItemSavedData data) {
        float s = (1 << data.scale) * (float) MAP_DIMENSION;
        return (s / 2.0d) - ((s / 2.0d) * ((decoX + MAP_DIMENSION) / (float) MAP_DIMENSION));
    }

    public int getBatchGroup() {
        return 0;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public String getDecorationId() {
        return decorationId;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (this.index != 0) {
            graphics.nextStratum();
        }
        super.extractWidgetRenderState(graphics, mouseX, mouseY, partialTick);

        if (!parentScreen.isPlacingPin() && !parentScreen.isEditingText()) {
            if (parentScreen.isPriority(getPriorityKey())) {
                graphics.blit(RenderPipelines.GUI_TEXTURED, ATLAS_BACKGROUND_TEXTURE,
                        getX(), getY(),
                        27, 167 + 36 + (this.selected ? BUTTON_H : 0),
                        BUTTON_W, BUTTON_H,
                        256, 256);
            }
            if (this.control && canDeleteMarker()) {
                graphics.blit(RenderPipelines.GUI_TEXTURED, ATLAS_BACKGROUND_TEXTURE, getX(), getY(),
                        24, 167, 5, 5, 256, 256);
            }
            if (this.shfting) {
                if (parentScreen.isPriority(getPriorityKey())) {
                    graphics.blit(RenderPipelines.GUI_TEXTURED, ATLAS_BACKGROUND_TEXTURE, getX() + 18, getY() - 4,
                            74, 172, 7, 7, 256, 256);
                } else {
                    graphics.blit(RenderPipelines.GUI_TEXTURED, ATLAS_BACKGROUND_TEXTURE, getX() + 22, getY() - 6,
                            74, 183, 7, 7, 256, 256);
                }
            }
        }

        renderDecoration(graphics, mouseX, mouseY);
        setSelected(false);
    }

    protected abstract void renderDecoration(GuiGraphicsExtractor graphics, int mouseX, int mouseY);

    @Override
    public Tooltip createTooltip() {
        if (control && canDeleteMarker()) {
            return Tooltip.create(Component.translatable("tooltip.map_atlases.delete_marker"));
        }
        if (shfting) {
            if (parentScreen.isPriority(getPriorityKey())) {
                return Tooltip.create(Component.translatable("tooltip.map_atlases.remove_priority_marker"));
            } else {
                return Tooltip.create(Component.translatable("tooltip.map_atlases.set_priority_marker"));
            }
        }
        Component mapIconComponent = getDecorationName();
        if (!MapAtlasesClientConfig.drawPinMapCoords.get()) {
            return Tooltip.create(mapIconComponent);
        }
        Component coordsComponent = Component.literal("X: " + (int) getWorldX() + ", Z: " + (int) getWorldZ())
                .withStyle(ChatFormatting.GRAY);
        return CompoundTooltip.create(mapIconComponent, coordsComponent);
    }

    protected boolean canFocusMarker() {
        return false;
    }

    protected boolean canDeleteMarker() {
        return true;
    }


    public static class Vanilla extends DecorationBookmarkButton {

        private final MapDecoration decoration;
        private final boolean isBanner;
        private final boolean isBlockMarker;

        public Vanilla(int px, int py, AtlasOverviewScreen screen, MapDataHolder data, MapDecoration mapDecoration, String decoId) {
            super(px, py, screen, data, decoId);
            this.decoration = mapDecoration;
            this.isBanner = decoration.type().isBound() &&
                    decoration.type().value().assetId().getPath().startsWith("banner_");
            this.isBlockMarker = decoId.startsWith(MoonlightCompat.BLOCK_MARKER_PREFIX);
            this.setTooltip(createTooltip());
        }

        @Override
        public double getWorldX() {
            return mapData.data.centerX - getDecorationPos(decoration.x(), mapData.data);
        }

        @Override
        public double getWorldZ() {
            return mapData.data.centerZ - getDecorationPos(decoration.y(), mapData.data);
        }

        @Override
        public String getPriorityKey() {
            if (isBlockMarker) {
                return decorationId;
            }
            return "banner@" + Math.round(getWorldX()) + "," + Math.round(getWorldZ());
        }

        @Override
        public Component getDecorationName() {
            var name = decoration.name();
            if (!name.isEmpty()) return name.get();

            if (isBlockMarker) {
                Component stored = ClientMarkers.getBlockMarkerName(mapData.id, decorationId);
                if (stored != null) return stored;
                String path = decoration.type().value().assetId().getPath().toLowerCase(Locale.ROOT);
                if (path.startsWith("bed_")) {
                    return Component.translatable("block.minecraft." + path.substring(4) + "_bed");
                }
                return Component.translatable("block.minecraft." + path);
            }

            if (isBanner) {
                String path = decoration.type().value().assetId().getPath().toLowerCase(Locale.ROOT);
                if (path.startsWith("banner_")) {
                    return Component.translatable("block.minecraft." + path.substring(7) + "_banner");
                }
            }

            String path = decoration.type().value().assetId().getPath().toLowerCase(Locale.ROOT);
            return Component.literal(AtlasOverviewScreen.getReadableName(path));
        }

        @Override
        protected void renderDecoration(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
            graphics.nextStratum();
            graphics.blit(RenderPipelines.GUI_TEXTURED, MapAtlasesClient.getDecorationTexture(decoration),
                    getX() + width / 2 - 2, getY() + height / 2 - 4,
                    0, 0, 8, 8, 8, 8);
        }

        @Override
        protected boolean canDeleteMarker() {
            return !parentScreen.isPriority(getPriorityKey());
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            if (control && !canDeleteMarker()) {
                return false;
            }
            return super.mouseClicked(event, doubleClick);
        }

        @Override
        protected void deleteMarker() {
            Map<String, MapDecoration> decorations = MapAtlasesClient.getMutableDecorations(mapData.data);

            if (isBlockMarker) {
                MapAtlasesNetworking.CHANNEL.sendToServer(
                        C2SRemoveMarkerPacket.forBlockMarker(mapData.stringId, decorationId));
                ClientMarkers.removeBlockMarker(mapData.id, decorationId);
                decorations.remove(decorationId);
                MapAtlasesClient.markDecorationsDirty(mapData.data);

            } else {
                int worldX = (int) Math.round(getWorldX());
                int worldZ = (int) Math.round(getWorldZ());
                MapAtlasesNetworking.CHANNEL.sendToServer(
                        C2SRemoveMarkerPacket.forBanner(mapData.stringId, worldX, worldZ));
                decorations.remove(decorationId);
                MapAtlasesClient.markDecorationsDirty(mapData.data);
            }
        }
    }
}