package net.nanaky.ultimate_map_atlases.utils;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.nanaky.ultimate_map_atlases.client.AbstractAtlasWidget;
import net.nanaky.ultimate_map_atlases.client.MapAtlasesClient;
import net.nanaky.ultimate_map_atlases.client.screen.AtlasOverviewScreen;
import net.nanaky.ultimate_map_atlases.integration.moonlight.ClientMarkers;
import net.nanaky.ultimate_map_atlases.integration.moonlight.MoonlightCompat;
import net.nanaky.ultimate_map_atlases.map_collection.IMapCollection;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Collects "priority" (pinned) map decorations - currently vanilla banners and
 * block markers - so they can be highlighted on the rim of the minimap/atlas
 * widget when their world position falls outside the currently rendered area.
 */
public final class PriorityMarkers {

    private PriorityMarkers() {}

    public static List<PriorityMarkerInfo> collect(IMapCollection maps, Slice slice) {
        List<PriorityMarkerInfo> result = new ArrayList<>();
        if (maps == null || slice == null || !slice.hasMarkers()) {
            return result;
        }

        Set<String> priorityIds = ClientMarkers.getLoadedPriorityIds();
        if (priorityIds.isEmpty()) {
            return result;
        }

        for (MapDataHolder holder : maps.selectSection(slice)) {
            MapItemSavedData data = holder.data;
            for (var entry : MapAtlasesClient.getMutableDecorations(data).entrySet()) {
                String key = entry.getKey();
                MapDecoration deco = entry.getValue();

                if (MoonlightCompat.isCustomDecoration(key, deco)) {
                    continue;
                }

                boolean isBlockMarker = MoonlightCompat.isBlockMarkerDecoration(key, deco);
                if (!isBlockMarker && !deco.renderOnFrame()) {
                    continue;
                }

                double worldX = data.centerX - getDecorationPos(deco.x(), data);
                double worldZ = data.centerZ - getDecorationPos(deco.y(), data);

                String priorityKey = isBlockMarker
                        ? key
                        : "banner@" + Math.round(worldX) + "," + Math.round(worldZ);

                if (!priorityIds.contains(priorityKey)) {
                    continue;
                }

                Component name = resolveName(deco, key, holder, isBlockMarker);
                Identifier texture = MapAtlasesClient.getDecorationTexture(deco);

                result.add(new PriorityMarkerInfo(worldX, worldZ, texture, name));
            }
        }
        return result;
    }

    private static double getDecorationPos(int decoCoord, MapItemSavedData data) {
        float s = (1 << data.scale) * (float) AbstractAtlasWidget.MAP_DIMENSION;
        return (s / 2.0d) - ((s / 2.0d) * ((decoCoord + AbstractAtlasWidget.MAP_DIMENSION) / (float) AbstractAtlasWidget.MAP_DIMENSION));
    }

    private static Component resolveName(MapDecoration decoration, String decorationId, MapDataHolder mapData, boolean isBlockMarker) {
        var name = decoration.name();
        if (name.isPresent()) {
            return name.get();
        }

        if (isBlockMarker) {
            Component stored = ClientMarkers.getBlockMarkerName(mapData.id, decorationId);
            if (stored != null) {
                return stored;
            }
            if (decoration.type().isBound()) {
                String path = decoration.type().value().assetId().getPath().toLowerCase(Locale.ROOT);
                if (path.startsWith("bed_")) {
                    return Component.translatable("block.minecraft." + path.substring(4) + "_bed");
                }
                return Component.translatable("block.minecraft." + path);
            }
            return Component.literal(decorationId);
        }

        if (decoration.type().isBound()) {
            String path = decoration.type().value().assetId().getPath().toLowerCase(Locale.ROOT);
            if (path.startsWith("banner_")) {
                return Component.translatable("block.minecraft." + path.substring(7) + "_banner");
            }
            return Component.literal(AtlasOverviewScreen.getReadableName(path));
        }

        return Component.literal(decorationId);
    }
}