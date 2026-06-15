package net.nanaky.ultimate_map_atlases.config;

import net.nanaky.ultimate_map_atlases.client.Anchoring;
import net.nanaky.ultimate_map_atlases.client.InHandMode;

public class UltimateMapAtlasesClientConfig {

    public boolean hideWhenInventoryOpen            = false;
    public boolean showsMapBackground               = false;
    public boolean hideWhenInHand                   = true;
    public boolean yOnlyWithSlice                   = true;
    public double  miniMapScale                     = 1.0;
    public boolean drawMiniMapHUD                   = true;
    public double  miniMapZoomMultiplier            = 1.0;
    public Anchoring  miniMapAnchoring              = Anchoring.UPPER_LEFT;
    public int     miniMapHorizontalOffset          = 6;
    public int     miniMapVerticalOffset            = 16;
    public int     activePotionVerticalOffset       = 26;
    public boolean drawMinimapCoords                = true;
    public boolean drawMinimapChunkCoords           = false;
    public boolean drawMinimapBiome                 = true;
    public double  minimapCoordsAndBiomeScale       = 0.8;
    public double  miniMapDecorationScale           = 1.0;
    public double  miniMapDecorationTextScale       = 1.0;
    public boolean miniMapFollowPlayer              = true;
    public boolean miniMapRotate                    = true;
    public boolean drawMinimapCardinals             = true;
    public double  miniMapCardinalsScale            = 0.8;
    public boolean miniMapOnlyNorth                 = false;
    public boolean miniMapBorder                    = false;
    public boolean minimapSkyLight                  = true;
    public boolean mapChangeSound                   = false;
    public boolean automaticSlice                   = false;

    public boolean worldMapCrossair                 = false;
    public boolean worldMapBigTexture               = false;
    public boolean worldMapSmoothPanning            = true;
    public boolean worldMapSmoothZooming            = true;
    public double  worldMapZoomScrollSpeed          = 1.0;
    public double  worldMapScale                    = 1.25;
    public double  lecternWorldMapScale             = 1.0;
    public boolean worldMapBorder                   = true;
    public boolean drawWorldMapCoords               = true;
    public boolean drawPinMapCoords                 = true;
    public double  worldMapCoordsScale              = 1.0;
    public double  worldMapDecorationScale          = 1.0;
    public double  worldMapDecorationTextScale      = 1.0;
    public boolean worldMapCompactSliceIndicator    = false;
    public boolean worldMapFollowPlayer             = true;

    public double  soundScalar                      = 1.0;
    public InHandMode inHandMode                    = InHandMode.NOT_LOCKED;

    public boolean moonlightCompat                  = true;
    public boolean moonlightPinTracking             = true;
    public boolean entityRadar                      = false;
    public int     radarRadius                      = 64;
    public boolean radarRotation                    = false;
    public boolean radarColor                       = false;
    public boolean nightLightMap                    = true;
    public boolean convertXaero                     = false;

    public boolean compassPositionIsLeft            = true;
    public boolean compassPositionIsCenter          = false;
    public boolean compassPositionIsRight           = false;
    public int     compassHeightOffset              = 5;
    public boolean drawTextShadow                   = true;
}