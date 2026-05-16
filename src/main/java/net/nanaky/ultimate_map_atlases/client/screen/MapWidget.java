package net.nanaky.ultimate_map_atlases.client.screen;

import net.nanaky.moonlight.api.platform.PlatHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ColumnPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix3x2fStack;
import net.nanaky.ultimate_map_atlases.client.AbstractAtlasWidget;
import net.nanaky.ultimate_map_atlases.client.MapAtlasesClient;
import net.nanaky.ultimate_map_atlases.client.ui.MapAtlasesHUD;
import net.nanaky.ultimate_map_atlases.config.MapAtlasesClientConfig;
import net.nanaky.ultimate_map_atlases.networking.C2STeleportPacket;
import net.nanaky.ultimate_map_atlases.networking.MapAtlasesNetworking;
import net.nanaky.ultimate_map_atlases.utils.MapDataHolder;
import net.nanaky.ultimate_map_atlases.utils.Slice;

import static net.nanaky.ultimate_map_atlases.client.MapAtlasesClient.ATLAS_BACKGROUND_TEXTURE;
public class MapWidget extends AbstractAtlasWidget implements Renderable, GuiEventListener, NarratableEntry {

    private static final int PAN_BUCKET = 25;
    private static final int ZOOM_BUCKET = 2;

    private final AtlasOverviewScreen mapScreen;

    private final int x;
    private final int y;
    private final int width;
    private final int height;

    private float cumulativeZoomValue;
    private float cumulativeMouseX = 0;
    private float cumulativeMouseY = 0;

    private double targetXCenter;
    private double targetZCenter;
    private float targetZoomLevel;

    private boolean isHovered;
    private float animationProgress = 0; //from zero to 1

    private float scaleAlpha = 0;

    public MapWidget(int x, int y, int width, int height, int atlasesCount,
                     AtlasOverviewScreen screen, MapDataHolder originalCenterMap) {
        super(atlasesCount);
        initialize(originalCenterMap);
        this.targetZoomLevel = zoomLevel;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;

        this.mapScreen = screen;
        this.drawBigPlayerMarker = false;
        this.drawMapDecorationsFallback = true;

        var player = Minecraft.getInstance().player;
        if (player != null) {
            this.currentXCenter = player.getX();
            this.currentZCenter = player.getZ();
            this.targetXCenter = currentXCenter;
            this.targetZCenter = currentZCenter;
        }
    }

    @Override
    protected void applyScissors(GuiGraphicsExtractor graphics, int x, int y, int x1, int y1) {
        super.applyScissors(graphics, x, y, x1, y1);
    }

    public void extractRenderState(GuiGraphicsExtractor graphics, int pMouseX, int pMouseY, float pPartialTick) {

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;


        this.isHovered = isMouseOver(pMouseX, pMouseY);

        float baseScale = MapAtlasesClientConfig.worldMapDecorationScale.get().floatValue();
        float baseTextScale = MapAtlasesClientConfig.worldMapDecorationTextScale.get().floatValue();
        float widgetScale = (float) width / (atlasesCount * AbstractAtlasWidget.MAP_DIMENSION);
        float zoomScale = (float) atlasesCount / zoomLevel;
        float combinedTransform = widgetScale * zoomScale;

        float decoScale = Mth.clamp((float) Math.pow((float) atlasesCount / zoomLevel, 0.6f) * baseScale, 0.25f, 2f) / combinedTransform;
        float decoTextScale = Mth.clamp((float) Math.pow((float) atlasesCount / zoomLevel, 0.6f) * baseTextScale, 0.25f, 2f) / combinedTransform;
        MapAtlasesClient.setDecorationsScale(decoScale);
        MapAtlasesClient.setDecorationsTextScale(decoTextScale);
        
        MapItemSavedData hoveredData = null;
        if (mapScreen.isShearing()) {
            ColumnPos pos = getHoveredPos(pMouseX, pMouseY);
            var d = mapScreen.findMapContaining(pos.x(), pos.z());
            hoveredData = d != null ? d.data : null;
        }
        this.drawAtlas(graphics, x, y, width, height, player, zoomLevel,
                MapAtlasesClientConfig.worldMapBorder.get(), mapScreen.getSelectedSlice().type(),
                0x00F000F0, hoveredData);

        float cursorScale = (float) Math.pow((float) atlasesCount / zoomLevel, 0.6f) * MapAtlasesClientConfig.worldMapDecorationScale.get().floatValue();
        cursorScale = Mth.clamp(cursorScale, 0.25f, 1.5f);
        drawPlayerCursor(graphics, player, cursorScale);

        MapAtlasesClient.setDecorationsScale(1);
        MapAtlasesClient.setDecorationsTextScale(1);
        MapAtlasesClient.setDecorationRotation(0);


        mapScreen.updateVisibleDecoration((int) currentXCenter, (int) currentZCenter,
                (zoomLevel / 2) * mapBlocksSize, followingPlayer);

        if (isHovered && mapScreen.isPlacingPin()) {
            Matrix3x2fStack pose = graphics.pose();
            pose.pushMatrix();
            pose.translate(pMouseX - 2.5f, pMouseY - 2.5f);
            graphics.nextStratum();
            graphics.blit(RenderPipelines.GUI_TEXTURED, ATLAS_BACKGROUND_TEXTURE, 0, 0,
                    74, 172, 8, 8, 256, 256);
            pose.popMatrix();
        }
        if (isHovered && mapScreen.isShearing()) {
            Matrix3x2fStack pose = graphics.pose();
            pose.pushMatrix();
            pose.translate(pMouseX - 2.5f, pMouseY - 2.5f);
            graphics.nextStratum();
            graphics.blit(RenderPipelines.GUI_TEXTURED, ATLAS_BACKGROUND_TEXTURE, 0, 0,
                    63, 172, 8, 8, 256, 256);
            pose.popMatrix();
        }

        if (this.isHovered && !mapScreen.isEditingText()) {
            ColumnPos pos = getHoveredPos(pMouseX, pMouseY);
            var d = mapScreen.findMapContaining(pos.x(), pos.z());
            this.renderPositionText(graphics, mc.font, pMouseX, pMouseY, d);

            if (mapScreen.canTeleport()) {
                graphics.setTooltipForNextFrame(mc.font.split(Component.translatable("chat.coordinates.tooltip")
                        .withStyle(ChatFormatting.GREEN), 200), pMouseX, pMouseY);
            }
            if (!MapAtlasesClientConfig.drawWorldMapCoords.get()) return;
            if (d != null) {
                String label = Component.translatable("message.map_atlases.map_name", d.id).getString();
                int textWidth = mc.font.width(label);
                MapAtlasesHUD.drawScaledComponent(
                        graphics, mc.font, x - 63 + textWidth / 2, y + 1, label, 1, width, width);
                }
            }
        renderScaleText(graphics, mc);
    }

    private void renderScaleText(GuiGraphicsExtractor graphics, Minecraft mc) {
        boolean animation = zoomLevel != targetZoomLevel;
        if (animation || scaleAlpha != 0) {
            if (animation) scaleAlpha = 1;
            else {
                scaleAlpha = Math.max(0, scaleAlpha - 0.03f);
            }
            int a = (int) (scaleAlpha * 255);
            if (a > 10) {
                Matrix3x2fStack pose = graphics.pose();
                pose.pushMatrix();
                graphics.nextStratum();
                graphics.text(mc.font,
                        Component.translatable("message.map_atlases.map_scale", String.format("%.1f", targetZoomLevel)),
                        x + 1, y + height - 8, (a << 24) | 0x00FFFFFF, false);
                pose.popMatrix();
            }
        }
    }

    @Override
    public MapDataHolder getMapWithCenter(int centerX, int centerZ) {
        return mapScreen.findMapWithCenter(centerX, centerZ);
    }

    private void renderPositionText(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, MapDataHolder hoveredMap) {
        if (!MapAtlasesClientConfig.drawWorldMapCoords.get()) return;
        if (hoveredMap == null) return;
        ColumnPos pos = getHoveredPos(mouseX, mouseY);
        String coordsToDisplay = Component.translatable("message.map_atlases.coordinates", pos.x(), pos.z()).getString();
        int textWidth = font.width(coordsToDisplay);
        MapAtlasesHUD.drawScaledComponent(
                graphics, font, x + 62 - textWidth / 2, y + 1, coordsToDisplay, 1, width, width);
    }


    @Override
    public boolean isMouseOver(double pMouseX, double pMouseY) {
        return pMouseX >= this.x && pMouseY >= this.y && pMouseX < (this.x + this.width) && pMouseY < (this.y + this.height);
    }

    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (event.button() == 0) {
            cumulativeMouseX += deltaX;
            cumulativeMouseY += deltaY;
            double newXCenter;
            double newZCenter;
            boolean discrete = !MapAtlasesClientConfig.worldMapSmoothPanning.get();
            if (discrete) {
                newXCenter = (int) (currentXCenter - (round((int) cumulativeMouseX, PAN_BUCKET) / PAN_BUCKET * mapBlocksSize));
                newZCenter = (int) (currentZCenter - (round((int) cumulativeMouseY, PAN_BUCKET) / PAN_BUCKET * mapBlocksSize));
            } else {
                newXCenter = (currentXCenter - cumulativeMouseX * zoomLevel * ((float) mapBlocksSize / (width * mapScreen.globalScale)));
                newZCenter = (currentZCenter - cumulativeMouseY * zoomLevel * ((float) mapBlocksSize / (width * mapScreen.globalScale)));
            }
            if (newXCenter != currentXCenter) {
                targetXCenter = newXCenter;
                if (!discrete) {
                    currentXCenter = targetXCenter;
                }
                cumulativeMouseX = 0;
            }
            if (newZCenter != currentZCenter) {
                targetZCenter = newZCenter;
                if (!discrete) {
                    currentZCenter = targetZCenter;
                }
                cumulativeMouseY = 0;
            }
            followingPlayer = false;
            return true;
        }
        return GuiEventListener.super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double pMouseX, double pMouseY, double scrollX, double pDelta) {
        float minZoom = 0.5f;
        float maxZoom = 20;
        if ((pDelta < 0 && targetZoomLevel >= maxZoom) || (pDelta > 0 && targetZoomLevel <= minZoom)) {
            cumulativeZoomValue = 0;
            return false;
        }

        float zl;
        if (MapAtlasesClientConfig.worldMapSmoothZooming.get()) {
            float c = (float) (pDelta);
            double v = -c / 25d * MapAtlasesClientConfig.worldMapZoomScrollSpeed.get();
            if (AtlasOverviewScreen.isShiftDown() || AtlasOverviewScreen.isControlDown()) v *= 3;
            targetZoomLevel = Mth.clamp(targetZoomLevel + targetZoomLevel * (float) v, minZoom, maxZoom);
            zoomLevel = targetZoomLevel - 0.001f;
        } else {
            cumulativeZoomValue -= (float) pDelta;
            cumulativeZoomValue = Math.max(cumulativeZoomValue, 0);
            zl = round((int) cumulativeZoomValue, ZOOM_BUCKET) / ZOOM_BUCKET;
            zl = Math.max(zl, 0);
            float startZoom = 1;
            targetZoomLevel = Mth.clamp(startZoom + (2 * zl) + 1f, minZoom, maxZoom);
        }

        return true;
    }

    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (isHovered) {
            double mouseX = event.x();
            double mouseY = event.y();
            if (mapScreen.isPlacingPin()) {
                ColumnPos pos = getHoveredPos(mouseX, mouseY);
                mapScreen.placePinAt(pos);
                mapScreen.getMinecraft().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ITEM_FRAME_ADD_ITEM, 1.7F, 2f));
            } else if (mapScreen.isShearing()) {
                ColumnPos pos = getHoveredPos(mouseX, mouseY);
                mapScreen.shearMapAt(pos);
                mapScreen.getMinecraft().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.SHEEP_SHEAR, 1.7F, 2f));
            } else if (mapScreen.canTeleport()) {
                ColumnPos pos = getHoveredPos(mouseX, mouseY);
                Slice slice = mapScreen.getSelectedSlice();
                MapAtlasesNetworking.CHANNEL.sendToServer(new C2STeleportPacket(pos.x(), pos.z(), slice.height(), slice.dimension()));
                if (!PlatHelper.isDev()) mapScreen.onClose();
                return true;
            }
            return !mapScreen.isEditingText();
        }
        return false;
    }

    @NotNull
    private ColumnPos getHoveredPos(double mouseX, double mouseY) {
        double wSize = zoomLevel;
        double hSize = zoomLevel * height / width;
        double atlasMapsRelativeMouseX = Mth.map(
                mouseX, x, x + width, -wSize, wSize);
        double atlasMapsRelativeMouseZ = Mth.map(
                mouseY, y, y + height, -hSize, hSize);
        int hackOffset = +3;
        return new ColumnPos(
                (int) (Math.floor(atlasMapsRelativeMouseX * (mapBlocksSize / 2.0)) + currentXCenter) + hackOffset,
                (int) (Math.floor(atlasMapsRelativeMouseZ * (mapBlocksSize / 2.0)) + currentZCenter) + hackOffset);
    }

    @Override
    public void setFocused(boolean pFocused) {

    }

    @Override
    public boolean isFocused() {
        return true;
    }

    @Override
    public NarrationPriority narrationPriority() {
        return NarrationPriority.NONE;
    }

    @Override
    public void updateNarration(NarrationElementOutput pNarrationElementOutput) {

    }

    public void resetAndCenter(int centerX, int centerZ, boolean followPlayer, boolean animation, boolean resetZoom) {
        if (followPlayer) {
            centerX = Minecraft.getInstance().player.getBlockX();
            centerZ = Minecraft.getInstance().player.getBlockZ();
        }
        this.targetXCenter = centerX;
        this.targetZCenter = centerZ;
        if (!animation) {
            this.currentXCenter = centerX;
            this.currentZCenter = centerZ;
        }
        this.cumulativeMouseX = 0;
        this.cumulativeMouseY = 0;
        this.cumulativeZoomValue = 0;
        this.followingPlayer = followPlayer;
        if (resetZoom) resetZoom();
    }

    public void resetZoom() {
        this.targetZoomLevel = atlasesCount * mapScreen.getSelectedSlice().type().getDefaultZoomFactor();
    }

    public void tick() {
        float animationSpeed = 0.4f;
        if (animationProgress != 0) {
            animationProgress -= animationProgress * animationSpeed - 0.01;
            animationProgress = Math.max(0, animationProgress);
        }
        if (this.zoomLevel != targetZoomLevel) {
            zoomLevel = (float) interpolate(targetZoomLevel, zoomLevel, animationSpeed);
        }
        if (this.currentXCenter != targetXCenter) {
            currentXCenter = interpolate(targetXCenter, currentXCenter, animationSpeed);
        }
        if (this.currentZCenter != targetZCenter) {
            currentZCenter = interpolate(targetZCenter, currentZCenter, animationSpeed);
        }

        if (followingPlayer) {

            var player = Minecraft.getInstance().player;
            targetXCenter = (int) player.getX();
            targetZCenter = (int) player.getZ();
        }
    }

    private void drawPlayerCursor(GuiGraphicsExtractor graphics, Player player, float scale) {
        if (player == null) return;
        if (!player.level().dimension().equals(mapScreen.getSelectedSlice().dimension())) return;

        float widgetScale = width / (float) (atlasesCount * AbstractAtlasWidget.MAP_DIMENSION);
        float zoomScale = atlasesCount / zoomLevel;
        float finalScale = widgetScale * zoomScale;

        float screenX = x + width / 2f + (float) ((player.getX() - currentXCenter) / (mapBlocksSize / AbstractAtlasWidget.MAP_DIMENSION)) * finalScale;
        float screenZ = y + height / 2f + (float) ((player.getZ() - currentZCenter) / (mapBlocksSize / AbstractAtlasWidget.MAP_DIMENSION)) * finalScale;

        if (screenX < x || screenX > x + width || screenZ < y || screenZ > y + height) return;

        float rot = (float) Math.toRadians(player.getYRot() + 180f);
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(screenX, screenZ);
        pose.rotate(rot);
        pose.scale(scale, scale);
        pose.translate(-4f, -4f);
        graphics.nextStratum();
        graphics.blit(RenderPipelines.GUI_TEXTURED, MapAtlasesClient.MAP_PLAYER_DECORATION_TEXTURE,
                0, 0, 0, 0, 8, 8, 8, 8);
        pose.popMatrix();
    }

    private double interpolate(double targetZCenter, double currentZCenter, double animationSpeed) {
        double diff = targetZCenter - currentZCenter;
        if (diff < 0) {
            return Math.max(targetZCenter, currentZCenter + (diff * animationSpeed) - 0.001);
        } else {
            return Math.min(targetZCenter, currentZCenter + (diff * animationSpeed) + 0.001);
        }
    }
}
