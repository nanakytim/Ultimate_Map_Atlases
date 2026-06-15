package net.nanaky.ultimate_map_atlases.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.Path;

public class UltimateMapAtlasesServerConfigManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH =
            FabricLoader.getInstance().getConfigDir().resolve("ultimate_map_atlases-server.json");

    public static UltimateMapAtlasesServerConfig INSTANCE = new UltimateMapAtlasesServerConfig();

    public static void load() {
        if (!PATH.toFile().exists()) { save(); return; }
        try (Reader r = new FileReader(PATH.toFile())) {
            UltimateMapAtlasesServerConfig loaded = GSON.fromJson(r, UltimateMapAtlasesServerConfig.class);
            if (loaded != null) copyInto(loaded, INSTANCE);
        } catch (Exception e) {
            System.err.println("[UltimateMapAtlases] Failed to load server config: " + e);
        }
    }

    public static void save() {
        try (Writer w = new FileWriter(PATH.toFile())) {
            GSON.toJson(INSTANCE, w);
        } catch (Exception e) {
            System.err.println("[UltimateMapAtlases] Failed to save server config: " + e);
        }
    }

    private static void copyInto(UltimateMapAtlasesServerConfig src, UltimateMapAtlasesServerConfig dst) {
        dst.maxMapCount               = src.maxMapCount;
        dst.acceptPaperForEmptyMaps   = src.acceptPaperForEmptyMaps;
        dst.requireEmptyMapsToExpand  = src.requireEmptyMapsToExpand;
        dst.mapEntryValueMultiplier   = src.mapEntryValueMultiplier;
        dst.pityActivationMapCount    = src.pityActivationMapCount;
        dst.enableEmptyMapEntryAndFill = src.enableEmptyMapEntryAndFill;
        dst.activationLocation        = src.activationLocation;
        dst.creativeTeleport          = src.creativeTeleport;
        dst.pinMarkerId               = src.pinMarkerId;
        dst.lightMap                  = src.lightMap;
        dst.entityRadar               = src.entityRadar;
        dst.shearButton               = src.shearButton;
        dst.roundRobinUpdate          = src.roundRobinUpdate;
        dst.mapUpdatePerTick          = src.mapUpdatePerTick;
        dst.mapUpdateMultithreaded    = src.mapUpdateMultithreaded;
        dst.debugUpdate               = src.debugUpdate;
        dst.markersUpdatePeriod       = src.markersUpdatePeriod;
    }
}