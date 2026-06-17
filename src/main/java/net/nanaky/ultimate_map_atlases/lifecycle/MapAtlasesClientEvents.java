package net.nanaky.ultimate_map_atlases.lifecycle;

import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import org.jetbrains.annotations.Nullable;
import net.nanaky.ultimate_map_atlases.MapAtlasesMod;
import net.nanaky.ultimate_map_atlases.client.MapAtlasesClient;
import net.nanaky.ultimate_map_atlases.config.UltimateMapAtlasesClientConfigManager;
import net.nanaky.ultimate_map_atlases.integration.SupplementariesClientCompat;
import net.nanaky.ultimate_map_atlases.integration.moonlight.ClientMarkers;
import net.nanaky.ultimate_map_atlases.integration.moonlight.EntityRadar;
import net.nanaky.ultimate_map_atlases.item.MapAtlasItem;
import net.nanaky.ultimate_map_atlases.map_collection.IMapCollection;
import net.nanaky.ultimate_map_atlases.networking.C2S2COpenAtlasScreenPacket;
import net.nanaky.ultimate_map_atlases.networking.C2SSelectSlicePacket;
import net.nanaky.ultimate_map_atlases.networking.MapAtlasesNetworking;
import net.nanaky.ultimate_map_atlases.utils.MapAtlasesAccessUtils;
import net.nanaky.ultimate_map_atlases.utils.MapType;
import net.nanaky.ultimate_map_atlases.utils.Slice;

import java.util.TreeSet;

public class MapAtlasesClientEvents {

    public static void onClientTick(Minecraft client, ClientLevel level) {
        long gameTime = level.getGameTime();

        if (MapAtlasesMod.SUPPLEMENTARIES && (gameTime + 27) % 40 == 0) {
            SupplementariesClientCompat.onClientTick(level);
        }
        else if (client.gui.screen() == null && (gameTime + 5) % 40 == 0 && UltimateMapAtlasesClientConfigManager.INSTANCE.automaticSlice) {
            ItemStack atlas = MapAtlasesClient.getCurrentActiveAtlas();
            if (!atlas.isEmpty()) {
                IMapCollection maps = MapAtlasItem.getMaps(atlas, level);

                Slice s = MapAtlasItem.getSelectedSlice(atlas, level.dimension());
                maybeChangeSlice(client.player, level, maps, s, atlas);
            }
        }
        else if ((gameTime + 7) % 40 == 0 && UltimateMapAtlasesClientConfigManager.INSTANCE.entityRadar && UltimateMapAtlasesClientConfigManager.INSTANCE.entityRadar) {
            Player player = client.player;
            if (player != null) {
                EntityRadar.onClientTick(player);
            }
        }
    }

    public static void onKeyPressed(int key, int code) {
    }

    public static void onKeyPressed(KeyMapping keyMapping) {
        Minecraft client = Minecraft.getInstance();
        if (client.gui.screen() != null) return;

        if (keyMapping == MapAtlasesClient.OPEN_ATLAS_KEYBIND) {
            if (client.level == null || client.player == null) return;
            ItemStack atlas = MapAtlasesAccessUtils.getAtlasFromPlayerByConfig(client.player);
            if (atlas.getItem() instanceof MapAtlasItem) {
                MapAtlasesNetworking.CHANNEL.sendToServer(C2S2COpenAtlasScreenPacket.forActiveAtlas(false));
            }
        }

        if (keyMapping == MapAtlasesClient.PLACE_PIN_KEYBIND) {
            if (UltimateMapAtlasesClientConfigManager.INSTANCE.moonlightCompat) {
                if (client.level == null || client.player == null) return;
                ItemStack atlas = MapAtlasesAccessUtils.getAtlasFromPlayerByConfig(client.player);
                if (atlas.getItem() instanceof MapAtlasItem) {
                    MapAtlasesNetworking.CHANNEL.sendToServer(C2S2COpenAtlasScreenPacket.forActiveAtlas(true));
                }
            }
        }

        ItemStack atlas = MapAtlasesClient.getCurrentActiveAtlas();
        if (!atlas.isEmpty()) {
            if (keyMapping == MapAtlasesClient.DECREASE_MINIMAP_ZOOM) {
                MapAtlasesClient.decreaseHoodZoom();
            }

            if (keyMapping == MapAtlasesClient.INCREASE_MINIMAP_ZOOM) {
                MapAtlasesClient.increaseHoodZoom();
            }

            if (keyMapping == MapAtlasesClient.INCREASE_SLICE) {
                IMapCollection maps = MapAtlasItem.getMaps(atlas, client.level);
                ResourceKey<Level> dim = client.level.dimension();
                Slice selectedSlice = MapAtlasItem.getSelectedSlice(atlas, dim);
                int current = selectedSlice.heightOrTop();
                MapType type = selectedSlice.type();
                Integer newHeight = maps.getHeightTree(dim, type).ceiling(current + 1);
                maybeSyncNewSlice(atlas, selectedSlice, newHeight);
            }

            if (keyMapping == MapAtlasesClient.DECREASE_SLICE) {
                IMapCollection maps = MapAtlasItem.getMaps(atlas, client.level);
                ResourceKey<Level> dim = client.level.dimension();
                Slice selectedSlice = MapAtlasItem.getSelectedSlice(atlas, dim);
                int current = selectedSlice.heightOrTop();
                MapType type = selectedSlice.type();
                Integer newHeight = maps.getHeightTree(dim, type).floor(current - 1);
                maybeSyncNewSlice(atlas, selectedSlice, newHeight);
            }
        }
    }

    private static void maybeSyncNewSlice(ItemStack atlas, Slice oldSlice, Integer newHeight) {
        Slice newSlice = Slice.of(oldSlice.type(), newHeight, oldSlice.dimension());
        if (!newSlice.equals(oldSlice)) {
            MapAtlasesNetworking.CHANNEL.sendToServer(new C2SSelectSlicePacket(newSlice, null));
        }
        MapAtlasItem.setSelectedSlice(atlas, newSlice);
    }

    public static void onLoggedOut() {
        MapAtlasesClient.clearPendingOpenScreen();
        ClientMarkers.unloadWorld();
    }

    private static void maybeChangeSlice(Player player, Level level, IMapCollection maps, Slice lastSlice, ItemStack atlas) {
        MapType type = lastSlice.type();
        ResourceKey<Level> dim = lastSlice.dimension();
        Integer newHeight = getClosestSlice(player, level, maps, dim, type);
        if (newHeight != null) {
            maybeSyncNewSlice(atlas, lastSlice, newHeight);
        }
    }


    @Nullable
    public static Integer getClosestSlice(Player player, Level level, IMapCollection cap, ResourceKey<Level> dim, MapType type) {
        TreeSet<Integer> heightTree = cap.getHeightTree(dim, type);
        if (heightTree.size() == 1) return null;
        int y = player.getBlockY();

        int worldSurface = level.getHeight(Heightmap.Types.OCEAN_FLOOR, player.getBlockX(), player.getBlockZ());
        boolean isAboveHeightMap = y >= worldSurface;
        Integer ceiling = heightTree.ceiling(y);
        if (isAboveHeightMap) {
            return ceiling;
        }
        else {
            Integer floor = heightTree.floor(y);

            int aboveDist = ceiling == null ? 0 : ceiling - y;
            int belowDist = floor == null ? 0 : y - floor;
            int max = Math.max(belowDist, aboveDist);
            boolean canGoUp = true;
            boolean canGoDown = true;
            BlockPos.MutableBlockPos pos = player.blockPosition().mutable();
            int startY = pos.getY();
            for (int j = 1; j <= max; j++) {
                if (!canGoUp && !canGoDown) {
                    return null;
                }
                if (j == aboveDist) {
                    return ceiling;
                }
                if (j == belowDist) {
                    return floor;
                }
                if (canGoUp) {
                    pos.setY(startY + j);
                    if (level.getBlockState(pos).getMapColor(level, pos) != MapColor.NONE) {
                        canGoUp = false;
                    }
                }
                if (canGoDown) {
                    pos.setY(startY - j);
                    if (level.getBlockState(pos).getMapColor(level, pos) != MapColor.NONE) {
                        canGoDown = false;
                    }
                }
            }
            return null;
        }
    }


}
