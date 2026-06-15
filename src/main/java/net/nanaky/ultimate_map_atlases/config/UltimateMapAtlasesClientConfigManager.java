package net.nanaky.ultimate_map_atlases.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.Path;

public class UltimateMapAtlasesClientConfigManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH =
            FabricLoader.getInstance().getConfigDir().resolve("ultimate_map_atlases-client.json");

    public static UltimateMapAtlasesClientConfig INSTANCE = new UltimateMapAtlasesClientConfig();

    public static void load() {
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) return;
        if (!PATH.toFile().exists()) { save(); return; }
        try (Reader r = new FileReader(PATH.toFile())) {
            UltimateMapAtlasesClientConfig loaded = GSON.fromJson(r, UltimateMapAtlasesClientConfig.class);
            if (loaded != null) copyInto(loaded, INSTANCE);
        } catch (Exception e) {
            System.err.println("[UltimateMapAtlases] Failed to load client config: " + e);
        }
    }

    public static void save() {
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) return;
        try (Writer w = new FileWriter(PATH.toFile())) {
            GSON.toJson(INSTANCE, w);
        } catch (Exception e) {
            System.err.println("[UltimateMapAtlases] Failed to save client config: " + e);
        }
    }

    private static void copyInto(UltimateMapAtlasesClientConfig src, UltimateMapAtlasesClientConfig dst) {
        dst.hideWhenInventoryOpen         = src.hideWhenInventoryOpen;
        dst.showsMapBackground            = src.showsMapBackground;
        dst.hideWhenInHand                = src.hideWhenInHand;
        dst.yOnlyWithSlice                = src.yOnlyWithSlice;
        dst.miniMapScale                  = src.miniMapScale;
        dst.drawMiniMapHUD                = src.drawMiniMapHUD;
        dst.miniMapZoomMultiplier         = src.miniMapZoomMultiplier;
        dst.miniMapAnchoring              = src.miniMapAnchoring;
        dst.miniMapHorizontalOffset       = src.miniMapHorizontalOffset;
        dst.miniMapVerticalOffset         = src.miniMapVerticalOffset;
        dst.activePotionVerticalOffset    = src.activePotionVerticalOffset;
        dst.drawMinimapCoords             = src.drawMinimapCoords;
        dst.drawMinimapChunkCoords        = src.drawMinimapChunkCoords;
        dst.drawMinimapBiome              = src.drawMinimapBiome;
        dst.minimapCoordsAndBiomeScale    = src.minimapCoordsAndBiomeScale;
        dst.miniMapDecorationScale        = src.miniMapDecorationScale;
        dst.miniMapDecorationTextScale    = src.miniMapDecorationTextScale;
        dst.miniMapFollowPlayer           = src.miniMapFollowPlayer;
        dst.miniMapRotate                 = src.miniMapRotate;
        dst.drawMinimapCardinals          = src.drawMinimapCardinals;
        dst.miniMapCardinalsScale         = src.miniMapCardinalsScale;
        dst.miniMapOnlyNorth              = src.miniMapOnlyNorth;
        dst.miniMapBorder                 = src.miniMapBorder;
        dst.minimapSkyLight               = src.minimapSkyLight;
        dst.mapChangeSound                = src.mapChangeSound;
        dst.automaticSlice                = src.automaticSlice;
        dst.worldMapCrossair              = src.worldMapCrossair;
        dst.worldMapBigTexture            = src.worldMapBigTexture;
        dst.worldMapSmoothPanning         = src.worldMapSmoothPanning;
        dst.worldMapSmoothZooming         = src.worldMapSmoothZooming;
        dst.worldMapZoomScrollSpeed       = src.worldMapZoomScrollSpeed;
        dst.worldMapScale                 = src.worldMapScale;
        dst.lecternWorldMapScale          = src.lecternWorldMapScale;
        dst.worldMapBorder                = src.worldMapBorder;
        dst.drawWorldMapCoords            = src.drawWorldMapCoords;
        dst.drawPinMapCoords              = src.drawPinMapCoords;
        dst.worldMapCoordsScale           = src.worldMapCoordsScale;
        dst.worldMapDecorationScale       = src.worldMapDecorationScale;
        dst.worldMapDecorationTextScale   = src.worldMapDecorationTextScale;
        dst.worldMapCompactSliceIndicator = src.worldMapCompactSliceIndicator;
        dst.worldMapFollowPlayer          = src.worldMapFollowPlayer;
        dst.soundScalar                   = src.soundScalar;
        dst.inHandMode                    = src.inHandMode;
        dst.moonlightCompat               = src.moonlightCompat;
        dst.moonlightPinTracking          = src.moonlightPinTracking;
        dst.entityRadar                   = src.entityRadar;
        dst.radarRadius                   = src.radarRadius;
        dst.radarRotation                 = src.radarRotation;
        dst.radarColor                    = src.radarColor;
        dst.nightLightMap                 = src.nightLightMap;
        dst.convertXaero                  = src.convertXaero;
        dst.compassPositionIsLeft         = src.compassPositionIsLeft;
        dst.compassPositionIsCenter       = src.compassPositionIsCenter;
        dst.compassPositionIsRight        = src.compassPositionIsRight;
        dst.compassHeightOffset           = src.compassHeightOffset;
        dst.drawTextShadow                = src.drawTextShadow;
    }
}