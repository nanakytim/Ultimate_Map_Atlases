package net.nanaky.ultimate_map_atlases.config;

import net.nanaky.ultimate_map_atlases.utils.ActivationLocation;

public class UltimateMapAtlasesServerConfig {
    public int              maxMapCount                 = 2048;
    public boolean          acceptPaperForEmptyMaps     = false;
    public boolean          requireEmptyMapsToExpand    = true;
    public int              mapEntryValueMultiplier     = 1;
    public int              pityActivationMapCount      = 0;
    public boolean          enableEmptyMapEntryAndFill  = false;
    public ActivationLocation activationLocation        = ActivationLocation.HOTBAR_AND_HANDS;
    public boolean          creativeTeleport            = true;
    public String           pinMarkerId                 = "";
    public boolean          lightMap                    = false;
    public boolean          entityRadar                 = false;
    public boolean          shearButton                 = true;
    public boolean          roundRobinUpdate            = false;
    public int              mapUpdatePerTick            = 1;
    public UpdateType       mapUpdateMultithreaded      = UpdateType.SINGLE_PLAYER_ONLY;
    public boolean          debugUpdate                 = false;
    public int              markersUpdatePeriod         = 10;

    public enum UpdateType {
        OFF, SINGLE_PLAYER_ONLY, ALWAYS_ON
    }
}