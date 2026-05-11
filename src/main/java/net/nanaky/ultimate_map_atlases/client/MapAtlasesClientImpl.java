package net.nanaky.ultimate_map_atlases.client;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.nanaky.ultimate_map_atlases.client.MapAtlasesClient;
import net.nanaky.ultimate_map_atlases.client.ui.MapAtlasesHUD;
import net.nanaky.ultimate_map_atlases.lifecycle.MapAtlasesClientEvents;

import java.util.Set;

public class MapAtlasesClientImpl {

    private static final Set<KeyMapping> KEYBINDS = Set.of(
            MapAtlasesClient.PLACE_PIN_KEYBIND,
            MapAtlasesClient.DECREASE_SLICE,
            MapAtlasesClient.INCREASE_SLICE,
            MapAtlasesClient.DECREASE_MINIMAP_ZOOM,
            MapAtlasesClient.INCREASE_MINIMAP_ZOOM,
            MapAtlasesClient.OPEN_ATLAS_KEYBIND
    );
    private static final MapAtlasesHUD HUD = new MapAtlasesHUD();

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(MapAtlasesClientImpl::mapAtlasClientTick);
        HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS,
                net.nanaky.ultimate_map_atlases.MapAtlasesMod.res("minimap_hud"), HUD);
    }

    private static void mapAtlasClientTick(Minecraft minecraft) {
        for (var k : KEYBINDS) {
            if (k.consumeClick()) {
                MapAtlasesClientEvents.onKeyPressed(k);
            }
        }
    }

    public static void decreaseHoodZoom() {
        HUD.decreaseZoom();
    }

    public static void increaseHoodZoom() {
        HUD.increaseZoom();
    }
}
