package net.nanaky.ultimate_map_atlases.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.nanaky.ultimate_map_atlases.client.MapAtlasesClient;
import net.nanaky.ultimate_map_atlases.client.MapAtlasesClientImpl;
import net.nanaky.ultimate_map_atlases.lifecycle.MapAtlasesClientEvents;

public class UltimateMapAtlasesClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        clientInit();
    }

    public static void clientInit() {
        registerKeyMappings();
        MapAtlasesClientImpl.init();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player != null) MapAtlasesClient.cachePlayerState(client.player);
            if (client.level != null) MapAtlasesClientEvents.onClientTick(client, client.level);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> MapAtlasesClientEvents.onLoggedOut());
    }

    private static void registerKeyMappings() {
        KeyMappingHelper.registerKeyMapping(MapAtlasesClient.OPEN_ATLAS_KEYBIND);
        KeyMappingHelper.registerKeyMapping(MapAtlasesClient.PLACE_PIN_KEYBIND);
        KeyMappingHelper.registerKeyMapping(MapAtlasesClient.DECREASE_MINIMAP_ZOOM);
        KeyMappingHelper.registerKeyMapping(MapAtlasesClient.INCREASE_MINIMAP_ZOOM);
        KeyMappingHelper.registerKeyMapping(MapAtlasesClient.DECREASE_SLICE);
        KeyMappingHelper.registerKeyMapping(MapAtlasesClient.INCREASE_SLICE);
    }
}
