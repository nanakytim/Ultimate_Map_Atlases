package net.nanaky.ultimate_map_atlases;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Items;
import net.nanaky.ultimate_map_atlases.lifecycle.MapAtlasesServerEvents;
import net.nanaky.ultimate_map_atlases.utils.BlockMarkerEvents;


public class UltimateMapAtlases implements ModInitializer {
    @Override
    public void onInitialize() {
        MapAtlasesMod.init();
        BlockMarkerEvents.register();
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(content -> {
            content.insertAfter(Items.MAP, MapAtlasesMod.MAP_ATLAS.get());
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (var p : server.getPlayerList().getPlayers()) {
                MapAtlasesServerEvents.onPlayerTick(p);
            }
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                MapAtlasesServerEvents.onPlayerJoin(handler.getPlayer()));
    }
}