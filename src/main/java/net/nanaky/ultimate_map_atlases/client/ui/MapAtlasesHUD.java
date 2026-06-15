package net.nanaky.ultimate_map_atlases.client.ui;

import com.mojang.datafixers.util.Pair;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2fStack;
import net.nanaky.ultimate_map_atlases.MapAtlasesMod;
import net.nanaky.ultimate_map_atlases.PlatStuff;
import net.nanaky.ultimate_map_atlases.client.AbstractAtlasWidget;
import net.nanaky.ultimate_map_atlases.client.Anchoring;
import net.nanaky.ultimate_map_atlases.client.MapAtlasesClient;
import net.nanaky.ultimate_map_atlases.config.UltimateMapAtlasesClientConfigManager;
import net.nanaky.ultimate_map_atlases.integration.moonlight.ClientMarkersRenderer;
import net.nanaky.ultimate_map_atlases.item.MapAtlasItem;
import net.nanaky.ultimate_map_atlases.map_collection.IMapCollection;
import net.nanaky.ultimate_map_atlases.map_collection.MapKey;
import net.nanaky.ultimate_map_atlases.utils.MapDataHolder;
import net.nanaky.ultimate_map_atlases.utils.Slice;

import java.util.List;
import java.util.Objects;

import static net.nanaky.ultimate_map_atlases.client.MapAtlasesClient.MAP_HUD_BACKGROUND_TEXTURE;
import static net.nanaky.ultimate_map_atlases.client.MapAtlasesClient.MAP_HUD_FOREGROUND_TEXTURE;
import static net.nanaky.ultimate_map_atlases.client.MapAtlasesClient.MAP_PLAYER_DECORATION_TEXTURE;

public class MapAtlasesHUD extends AbstractAtlasWidget implements HudElement {

    private static final int BG_SIZE = 64;
    /** Pixels the minimap is pushed down to make room for the biome label above it. */
    private static final int BIOME_ABOVE_OFFSET = 12;

    private static final List<String> DIRECTIONS = List.of("S", "SW", "W", "NW", "N", "NE", "E", "SE", "S");

    private final Minecraft mc;

    private boolean needsInit = true;
    private boolean lastHadCompass = false;
    private ItemStack currentAtlas = ItemStack.EMPTY;
    private MapKey currentMapKey;
    private MapKey lastMapKey;
    private IMapCollection currentMaps;

    private float globalScale = 1;

    public MapAtlasesHUD() {
        super(1);
        this.mc = Minecraft.getInstance();
        this.rotatesWithPlayer = false;
        this.zoomLevel = 1;
    }

    @Nullable
    @Override
    public MapDataHolder getMapWithCenter(int centerX, int centerZ) {
        Slice slice = currentMapKey.slice();
        MapDataHolder exact = currentMaps.select(centerX, centerZ, slice);
        if (exact != null) {
            return exact;
        }
        MapDataHolder closest = currentMaps.getClosest(centerX, centerZ, slice);
        if (closest == null) {
            return null;
        }
        int maxOffset = mapBlocksSize / 2;
        if (Math.abs(closest.data.centerX - centerX) <= maxOffset &&
                Math.abs(closest.data.centerZ - centerZ) <= maxOffset) {
            return closest;
        }
        return null;
    }

    @Override
    protected boolean shouldClipDecorations() {
        return true;
    }

    @Override
    protected void initialize(MapDataHolder originalCenterMap) {
        super.initialize(originalCenterMap);
        this.followingPlayer = UltimateMapAtlasesClientConfigManager.INSTANCE.miniMapFollowPlayer;
        this.rotatesWithPlayer = hasCompass(mc.player);
        this.globalScale = (float) (double) UltimateMapAtlasesClientConfigManager.INSTANCE.miniMapScale;
        this.currentMaps = MapAtlasItem.getMaps(currentAtlas, mc.level);
        this.drawBigPlayerMarker = followingPlayer;
        this.drawMapDecorationsFallback = true;
    }

    @Override
    protected void applyScissors(GuiGraphicsExtractor graphics, int x, int y, int x1, int y1) {
        super.applyScissors(graphics, x, y, x1, y1);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        this.render(graphics, screenWidth, screenHeight);
    }

    public void render(GuiGraphicsExtractor graphics, int screenWidth, int screenHeight) {
        if (mc.level == null || mc.player == null || mc.getDebugOverlay().showDebugScreen()) {
            return;
        }
        if (!UltimateMapAtlasesClientConfigManager.INSTANCE.drawMiniMapHUD) {
            return;
        }
        if (UltimateMapAtlasesClientConfigManager.INSTANCE.hideWhenInventoryOpen && mc.screen != null) {
            return;
        }

        ItemStack atlas = MapAtlasesClient.getCurrentActiveAtlas();
        if (atlas.isEmpty()) {
            return;
        }

        MapDataHolder activeMap = MapAtlasesClient.getActiveMap();
        currentMapKey = MapAtlasesClient.getActiveMapKey();
        if (activeMap == null || currentMapKey == null) {
            return;
        }

        if (UltimateMapAtlasesClientConfigManager.INSTANCE.hideWhenInHand &&
                (mc.player.getMainHandItem().is(MapAtlasesMod.MAP_ATLAS.get()) ||
                        mc.player.getOffhandItem().is(MapAtlasesMod.MAP_ATLAS.get()))) {
            return;
        }

        if (currentAtlas != atlas) {
            needsInit = true;
        }
        currentAtlas = atlas;

        if (MapAtlasItem.isLocked(currentAtlas)) {
           return;
        }

        if (needsInit) {
            needsInit = false;
            initialize(activeMap);
        }
        mapWherePlayerIs = activeMap;

        if (!Objects.equals(lastMapKey, currentMapKey)) {
            lastMapKey = currentMapKey;
            if (mc.screen == null && UltimateMapAtlasesClientConfigManager.INSTANCE.mapChangeSound) {
                mc.player.playSound(MapAtlasesMod.ATLAS_PAGE_TURN_SOUND_EVENT.get(),
                        (float) (double) UltimateMapAtlasesClientConfigManager.INSTANCE.soundScalar, 1.0F);
            }
        }

        int mapWidgetSize = (int) (BG_SIZE * (116 / 128f));
        Anchoring anchorLocation = UltimateMapAtlasesClientConfigManager.INSTANCE.miniMapAnchoring;
        int off = 5;
        int x = anchorLocation.isLeft ? off : (int) (screenWidth / globalScale) - (BG_SIZE + off);
        int y = anchorLocation.isUp ? off : (int) (screenHeight / globalScale) - (BG_SIZE + off);
        x += (int) (UltimateMapAtlasesClientConfigManager.INSTANCE.miniMapHorizontalOffset / globalScale);
        y += (int) (UltimateMapAtlasesClientConfigManager.INSTANCE.miniMapVerticalOffset / globalScale);
        y -= 4;
        if (!hasCompass(mc.player)) {
        y -= 12;
        }

        if (UltimateMapAtlasesClientConfigManager.INSTANCE.drawMinimapBiome) {
            if (anchorLocation.isUp) {
                y += BIOME_ABOVE_OFFSET;
            } else {
                y += 8;
            }
        }

        if (anchorLocation == Anchoring.UPPER_RIGHT) {
            y = Math.max(y, getPotionOffsetY(mc.player, y));
        }

        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.scale(globalScale, globalScale);

        if (!followingPlayer) {
            currentXCenter = Math.floor((mc.player.getX() + 64) / mapBlocksSize) * mapBlocksSize + (mapBlocksSize / 2f - 64);
            currentZCenter = Math.floor((mc.player.getZ() + 64) / mapBlocksSize) * mapBlocksSize + (mapBlocksSize / 2f - 64);
        } else {
            currentXCenter = mc.player.getX();
            currentZCenter = mc.player.getZ();
        }

        MapAtlasesClient.setDecorationsScale((float) (2 * zoomLevel * UltimateMapAtlasesClientConfigManager.INSTANCE.miniMapDecorationScale));
        MapAtlasesClient.setDecorationsTextScale((float) (4 * zoomLevel * UltimateMapAtlasesClientConfigManager.INSTANCE.miniMapDecorationTextScale));
        float yRot = mc.player.getYRot();
        MapAtlasesClient.setDecorationRotation(yRot - 180);
        int light = !UltimateMapAtlasesClientConfigManager.INSTANCE.minimapSkyLight ? 0x00F000F0 :
                packSkyLight(mc.level.getBrightness(LightLayer.SKY, mc.player.getOnPos().above()));
        int borderSize = (BG_SIZE - mapWidgetSize) / 2;

        graphics.blit(RenderPipelines.GUI_TEXTURED, MAP_HUD_BACKGROUND_TEXTURE, x, y,
                0, 0, BG_SIZE, BG_SIZE, BG_SIZE, BG_SIZE);

        boolean hasCompass = hasCompass(mc.player);
        this.rotatesWithPlayer = hasCompass;
        if (hasCompass != lastHadCompass) {
            lastHadCompass = hasCompass;
            needsInit = true;
        }

        drawAtlas(graphics, x + borderSize, y + borderSize, mapWidgetSize, mapWidgetSize, mc.player,
                zoomLevel * (float) (double) UltimateMapAtlasesClientConfigManager.INSTANCE.miniMapZoomMultiplier,
                UltimateMapAtlasesClientConfigManager.INSTANCE.miniMapBorder, currentMapKey.slice().type(), light, null);

        graphics.nextStratum();
        graphics.blit(RenderPipelines.GUI_TEXTURED, MAP_HUD_FOREGROUND_TEXTURE, x, y,
                0, 0, BG_SIZE, BG_SIZE, BG_SIZE, BG_SIZE);

        MapAtlasesClient.setDecorationsScale(1);
        MapAtlasesClient.setDecorationsTextScale(1);
        MapAtlasesClient.setDecorationRotation(0);

        pose.pushMatrix();
        pose.translate(x + mapWidgetSize / 2f + 3f, y + mapWidgetSize / 2f + 3f);
        if (!rotatesWithPlayer) {
            pose.rotate((float) Math.toRadians(yRot - 180));
        }
        if (drawBigPlayerMarker) {
            pose.translate(-4.5f, -4f);
            graphics.nextStratum();
            graphics.blit(RenderPipelines.GUI_TEXTURED, MAP_PLAYER_DECORATION_TEXTURE, 0, 0,
                    0, 0, 8, 8, 8, 8);
        }
        pose.popMatrix();

        graphics.nextStratum();
        drawHudText(graphics, x, y, screenWidth, mapWidgetSize, anchorLocation);

        graphics.nextStratum();
        drawCardinals(graphics, x, y, yRot);

        graphics.nextStratum();
        renderPriorityMarkers(graphics, mc.font, mc.player, x + borderSize, y + borderSize, mapWidgetSize, mapWidgetSize,
                zoomLevel * (float) (double) UltimateMapAtlasesClientConfigManager.INSTANCE.miniMapZoomMultiplier,
                BG_SIZE / 2f, BG_SIZE / 2f, currentMaps, currentMapKey.slice(), 0.5f / globalScale);

        if (UltimateMapAtlasesClientConfigManager.INSTANCE.moonlightCompat && UltimateMapAtlasesClientConfigManager.INSTANCE.moonlightPinTracking) {
            graphics.nextStratum();
            pose.pushMatrix();
            pose.translate(x + BG_SIZE / 2f, y + BG_SIZE / 2f);
            ClientMarkersRenderer.drawSmallPins(graphics, mc.font, currentXCenter + mapBlocksSize / 2f,
                    currentZCenter + mapBlocksSize / 2f, currentMapKey.slice(), mapBlocksSize * zoomLevel,
                    mc.player, rotatesWithPlayer, currentMaps);
            pose.popMatrix();
        }

        pose.popMatrix();
    }

    private static int packSkyLight(int skyLight) {
        return skyLight << 20 | skyLight << 4;
    }

    private int getPotionOffsetY(net.minecraft.client.player.LocalPlayer player, int y) {
        boolean hasBeneficial = false;
        boolean hasNegative = false;
        for (MobEffectInstance effectInstance : player.getActiveEffects()) {
            if (effectInstance.getEffect().value().isBeneficial()) {
                hasBeneficial = true;
            } else {
                hasNegative = true;
            }
        }
        int offset = UltimateMapAtlasesClientConfigManager.INSTANCE.activePotionVerticalOffset;
        if (hasNegative && y < 2 * offset) {
            return 2 * offset;
        }
        if (hasBeneficial && y < offset) {
            return offset;
        }
        return y;
    }

    private void drawHudText(GuiGraphicsExtractor graphics, int x, int y, int screenWidth, int mapWidgetSize, Anchoring anchorLocation) {
        Matrix3x2fStack pose = graphics.pose();
        float textScaling = (float) (double) UltimateMapAtlasesClientConfigManager.INSTANCE.minimapCoordsAndBiomeScale;
        int textHeightOffset = 2;
        int actualBgSize = (int) (BG_SIZE * globalScale);

        if (UltimateMapAtlasesClientConfigManager.INSTANCE.drawMinimapBiome) {
            pose.pushMatrix();
            if (!anchorLocation.isUp) {
                pose.translate(0, BG_SIZE + BIOME_ABOVE_OFFSET + 2);
            }

            int biomeMaxWidth = actualBgSize * 3;
            drawMapComponentBiome(graphics, mc.font, x, (int) (y - BIOME_ABOVE_OFFSET + 2),
                    actualBgSize, biomeMaxWidth, textScaling, mc.player.blockPosition(), mc.level);
            pose.popMatrix();
        }

        pose.pushMatrix();
        if (!anchorLocation.isUp) {
            pose.translate(0, -BG_SIZE - 20 * textScaling - 2);
        }

        Font font = mc.font;
        boolean global = UltimateMapAtlasesClientConfigManager.INSTANCE.drawMinimapCoords;
        boolean local = UltimateMapAtlasesClientConfigManager.INSTANCE.drawMinimapChunkCoords;
        if (global || local) {
            if (hasCompass(mc.player)) {
                BlockPos pos = new BlockPos(new Vec3i(
                        towardsZero(mc.player.position().x),
                        towardsZero(mc.player.position().y),
                        towardsZero(mc.player.position().z)));
                if (global) {
                    drawMapComponentCoords(graphics, font, screenWidth, x, textScaling, pos, false);
                    textHeightOffset += 10;
                }
                if (local) {
                    drawMapComponentCoords(graphics, font, screenWidth, x, textScaling, pos, true);
                    textHeightOffset += 10;
                }
            }
        }

        pose.popMatrix();
    }

    private boolean hasCompass(Player player) {
        if (isVanillaCompass(player.getMainHandItem()) || isVanillaCompass(player.getOffhandItem())) {
            return true;
        }

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (isVanillaCompass(stack)) {
                return true;
            }
        }

        return false;
    }

    private boolean isVanillaCompass(ItemStack stack) {
        return stack.is(Items.COMPASS) 
            && !stack.has(net.minecraft.core.component.DataComponents.LODESTONE_TRACKER);
    }

    private void drawCardinals(GuiGraphicsExtractor graphics, int x, int y, float yRot) {
        if (mc.player == null || !hasCompass(mc.player) || mc.level.dimension() == Level.NETHER || mc.level.dimension() == Level.END) {
            return;
        }
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(x + BG_SIZE / 2f, y + BG_SIZE / 2f);

        var p = getDirectionPos(BG_SIZE / 2f - 3, rotatesWithPlayer ? yRot : 180);
        float a = p.getFirst();
        float b = p.getSecond();
        drawNorthLetter(graphics, mc.font, a, b, "N");
        if (!UltimateMapAtlasesClientConfigManager.INSTANCE.miniMapOnlyNorth) {
            drawLetter(graphics, mc.font, -a, -b, "S");
            drawLetter(graphics, mc.font, -b, a, "E");
            drawLetter(graphics, mc.font, b, -a, "W");
        }

        pose.popMatrix();
    }

    private void drawLetter(GuiGraphicsExtractor graphics, Font font, float a, float b, String letter) {
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        float scale = (float) (double) UltimateMapAtlasesClientConfigManager.INSTANCE.miniMapCardinalsScale / globalScale;
        pose.scale(scale, scale);
        PlatStuff.drawString(graphics, font, letter,
            a / scale - font.width(letter) / 2f + 1,
            b / scale - font.lineHeight / 2f + 1,
            0xFF323232, false);
        PlatStuff.drawString(graphics, font, letter,
            a / scale - font.width(letter) / 2f,
            b / scale - font.lineHeight / 2f,
            0xFFE0E0E0, false);
        pose.popMatrix();
    }

    private void drawStringOutlined(GuiGraphicsExtractor graphics, Font font, String text, float x, float y, int innerColor, boolean shadow) {
        PlatStuff.drawString(graphics, font, text, x + 1, y,     0xFF000000, false);
        PlatStuff.drawString(graphics, font, text, x - 1, y,     0xFF000000, false);
        PlatStuff.drawString(graphics, font, text, x,     y + 1, 0xFF000000, false);
        PlatStuff.drawString(graphics, font, text, x,     y - 1, 0xFF000000, false);
        PlatStuff.drawString(graphics, font, text, x + 1, y + 1, 0xFF000000, false);
        PlatStuff.drawString(graphics, font, text, x - 1, y - 1, 0xFF000000, false);
        PlatStuff.drawString(graphics, font, text, x + 1, y - 1, 0xFF000000, false);
        PlatStuff.drawString(graphics, font, text, x - 1, y + 1, 0xFF000000, false);
        PlatStuff.drawString(graphics, font, text, x, y, innerColor, shadow);
    }

    private void drawNorthLetter(GuiGraphicsExtractor graphics, Font font, float a, float b, String letter) {
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        float scale = (float) (double) UltimateMapAtlasesClientConfigManager.INSTANCE.miniMapCardinalsScale / globalScale;
        pose.scale(scale, scale);
        drawStringOutlined(graphics, font, letter,
            a / scale - font.width(letter) / 2f,
            b / scale - font.lineHeight / 2f,
            0xFFF21D25, false);
        pose.popMatrix();
    }

    private static int towardsZero(double d) {
        if (d < 0.0) {
            return -1 * (int) Math.floor(-1 * d);
        }
        return (int) Math.floor(d);
    }

    public void drawMapComponentCoords(
        GuiGraphicsExtractor context,
        Font font,
        int screenWidth,
        int minimapX,
        float textScaling,
        BlockPos pos,
        boolean chunk
) {
    String coordsToDisplay;
    String prefix = "";

    if (chunk) {
        coordsToDisplay = Component.translatable("message.map_atlases.chunk_coordinates",
                Component.literal(String.valueOf(pos.getX() / 16)),
                Component.literal(String.valueOf(pos.getZ() / 16)),
                Component.literal(String.valueOf(pos.getX() % 16)),
                Component.literal(String.valueOf(pos.getZ() % 16))).getString();
    } else {
        float degrees = Mth.wrapDegrees(mc.player.getYRot());
        if (degrees < 0) degrees += 360;
        int facing = Math.round(degrees / 45);
        String dir = DIRECTIONS.get(facing);
        boolean compassValid = mc.level.dimension() != Level.NETHER
                && mc.level.dimension() != Level.END;
        prefix = compassValid ? dir + ": " : "";
        coordsToDisplay = Component.translatable("message.map_atlases.coordinates_full",
                Component.literal(String.valueOf(pos.getX())),
                Component.literal(String.valueOf(pos.getY())),
                Component.literal(String.valueOf(pos.getZ()))).getString();
    }

    int xcoord;
    String fullText = prefix + coordsToDisplay;
    int stringWidth = font.width(fullText);
    if (UltimateMapAtlasesClientConfigManager.INSTANCE.compassPositionIsLeft) {
        xcoord = 5;
    } else if (UltimateMapAtlasesClientConfigManager.INSTANCE.compassPositionIsCenter) {
        xcoord = (screenWidth / 2) - (stringWidth / 2);
    } else {
        xcoord = screenWidth - stringWidth - 5;
    }

    
    if (mc.level.dimension() == Level.NETHER || mc.level.dimension() == Level.END) {
        float minimapCenterX = minimapX + BG_SIZE / 2f;
        float scaledHalfWidth = stringWidth / 2f;
        float leftEdge = minimapCenterX - scaledHalfWidth;
        float shiftX = leftEdge < 2 ? 2 - leftEdge : 0;
        xcoord = (int) (minimapCenterX - scaledHalfWidth + shiftX);
    }

    int ycoord = UltimateMapAtlasesClientConfigManager.INSTANCE.compassHeightOffset + 3;

    Matrix3x2fStack pose = context.pose();
    pose.pushMatrix();

    if (!prefix.isEmpty()) {
        float cursorX = xcoord;
        for (int i = 0; i < prefix.length(); i++) {
            String ch = String.valueOf(prefix.charAt(i));
            if (ch.equals("N")) {
                drawStringOutlined(context, font, ch, cursorX, ycoord, 0xFFF21D25, false);
            } else {
                PlatStuff.drawString(context, font, ch, cursorX, ycoord, 0xFFE0E0E0,
                        UltimateMapAtlasesClientConfigManager.INSTANCE.drawTextShadow);
            }
            cursorX += font.width(ch);
        }
        PlatStuff.drawString(context, font, coordsToDisplay,
                cursorX, ycoord, 0xFFE0E0E0, UltimateMapAtlasesClientConfigManager.INSTANCE.drawTextShadow);
    } else {
        PlatStuff.drawString(context, font, coordsToDisplay,
                xcoord, ycoord, 0xFFE0E0E0, UltimateMapAtlasesClientConfigManager.INSTANCE.drawTextShadow);
    }

    pose.popMatrix();
}

    public void drawMapComponentBiome(
            GuiGraphicsExtractor context,
            Font font,
            int x, int y,
            int targetWidth,
            int maxWidth,
            float textScaling,
            BlockPos blockPos,
            Level level
    ) {
        String biomeToDisplay = "";
        var key = level.getBiome(blockPos).unwrapKey();
        if (key.isPresent()) {
            ResourceKey<Biome> biomeKey = key.get();
            var id = biomeKey.identifier();
            biomeToDisplay = Component.translatable("biome." + id.getNamespace() + "." +
                    id.getPath().replace('/', '.')).getString();
        }
        drawCenteredTextSafe(context, font, x, y, biomeToDisplay,
                textScaling / globalScale, maxWidth, (int) (targetWidth / globalScale));
    }

    public static void drawScaledComponent(
            GuiGraphicsExtractor context,
            Font font,
            int x, int y,
            String text,
            float textScaling,
            int maxWidth,
            int targetWidth
    ) {
        Matrix3x2fStack pose = context.pose();
        float textWidth = font.width(text);
        float scale = Math.min(1, maxWidth * textScaling / Math.max(textWidth, 1));
        scale *= textScaling;
        float centerX = x + targetWidth / 2f;

        pose.pushMatrix();
        pose.translate(centerX, y + 4);
        pose.scale(scale, scale);
        pose.translate(-(textWidth) / 2f, -4);
        drawStringWithLighterShadow(context, font, text, 0, 0);
        pose.popMatrix();
    }

    private void drawCenteredTextSafe(
            GuiGraphicsExtractor context,
            Font font,
            int x, int y,
            String text,
            float textScaling,
            int maxWidth,
            int targetWidth
    ) {
        Matrix3x2fStack pose = context.pose();
        float textWidth = font.width(text);
        float scale = Math.min(1, maxWidth * textScaling / Math.max(textWidth, 1));
        scale *= textScaling;
        float centerX = x + targetWidth / 2f;

        float scaledHalfWidth = (textWidth * scale) / 2f;
        float leftEdge = centerX - scaledHalfWidth;
        float shiftX = leftEdge < 2 ? 2 - leftEdge : 0;

        pose.pushMatrix();
        pose.translate(centerX + shiftX, y + 4);
        pose.scale(scale, scale);
        pose.translate(-(textWidth) / 2f, -4);
        drawStringWithLighterShadow(context, font, text, 0, 0);
        pose.popMatrix();
    }

    private static void drawStringWithLighterShadow(GuiGraphicsExtractor context, Font font, String text, float x, float y) {
        PlatStuff.drawString(context, font, text, x + 1, y + 1, 0xFF323232, false);
        PlatStuff.drawString(context, font, text, x, y, 0xFFE0E0E0, false);
    }

    public static Pair<Float, Float> getDirectionPos(float radius, float angleDegrees) {
        angleDegrees = Mth.wrapDegrees(90 - angleDegrees);
        float angleRadians = (float) Math.toRadians(angleDegrees);

        float x;
        float y;
        if (angleDegrees >= -45 && angleDegrees < 45) {
            x = radius;
            y = radius * (float) Math.tan(angleRadians);
        } else if (angleDegrees >= 45 && angleDegrees < 135) {
            x = radius / (float) Math.tan(angleRadians);
            y = radius;
        } else if (angleDegrees >= 135 || angleDegrees < -135) {
            x = -radius;
            y = -radius * (float) Math.tan(angleRadians);
        } else {
            x = -radius / (float) Math.tan(angleRadians);
            y = -radius;
        }
        return Pair.of(x, y);
    }

    public void increaseZoom() {
        zoomLevel = Math.max(1, zoomLevel - 0.5f);
    }

    public void decreaseZoom() {
        zoomLevel = Math.min(10, zoomLevel + 0.5f);
    }
}