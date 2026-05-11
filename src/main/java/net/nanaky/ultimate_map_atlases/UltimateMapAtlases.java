package net.nanaky.ultimate_map_atlases;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.nanaky.ultimate_map_atlases.lifecycle.MapAtlasesServerEvents;

public class UltimateMapAtlases implements ModInitializer {
    @Override
    public void onInitialize() {
        MapAtlasesMod.init();
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (var p : server.getPlayerList().getPlayers()) {
                MapAtlasesServerEvents.onPlayerTick(p);
            }
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                MapAtlasesServerEvents.onPlayerJoin(handler.getPlayer()));
    }
}