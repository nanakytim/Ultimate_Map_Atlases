package net.nanaky.ultimate_map_atlases.utils;

import com.google.common.base.Preconditions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.saveddata.maps.MapBanner;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import net.nanaky.ultimate_map_atlases.MapAtlasesMod;
import net.nanaky.ultimate_map_atlases.config.UltimateMapAtlasesClientConfigManager;
import net.nanaky.ultimate_map_atlases.config.UltimateMapAtlasesServerConfig;
import net.nanaky.ultimate_map_atlases.config.UltimateMapAtlasesServerConfigManager;
import net.nanaky.ultimate_map_atlases.integration.moonlight.MoonlightCompat;
import net.nanaky.ultimate_map_atlases.map_collection.MapKey;
import net.nanaky.ultimate_map_atlases.mixin.MapItemSavedDataAccessor;
import net.nanaky.ultimate_map_atlases.networking.MapAtlasesNetworking;
import net.nanaky.ultimate_map_atlases.networking.S2CDebugUpdateMapPacket;
import net.nanaky.ultimate_map_atlases.networking.C2SToggleBlockMarkerPacket;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MapDataHolder {
    public final int id;
    public final String stringId;
    public final MapItemSavedData data;

    public final Slice slice;
    public final MapType type;
    @Nullable
    public final Integer height;

    public MapDataHolder(String name, @NotNull MapItemSavedData data) {
        this(MapAtlasesAccessUtils.findMapIntFromString(name), name, data);
    }

    private MapDataHolder(int id, String stringId, @NotNull MapItemSavedData data) {
        Preconditions.checkNotNull(data);
        this.id = id;
        this.stringId = stringId;
        this.data = data;
        this.type = MapType.fromKey(stringId, data);
        this.height = type.getHeight(data);
        this.slice = Slice.of(type, height, data.dimension);
    }

    @Nullable
    public static MapDataHolder findFromId(Level level, int id) {
        for (var t : MapType.values()) {
            var d = t.getMapData(level, id);
            if (d != null) {
                return new MapDataHolder(id, d.getFirst(), d.getSecond());
            }
        }
        return null;
    }

    public MapKey makeKey() {
        return MapKey.at(data.scale, data.centerX, data.centerZ, slice);
    }

    public void updateMap(ServerPlayer player) {
        if (canMultiThread(player.level())) {
            EXECUTORS.submit(() -> {
                ((MapItem) type.filled).update(player.level(), player, data);
            });

            updateMarkers(player, 128);

        } else {
            ((MapItem) type.filled).update(player.level(), player, data);
        }
        if (UltimateMapAtlasesServerConfigManager.INSTANCE.debugUpdate) {
            MapAtlasesNetworking.CHANNEL.sendToClientPlayer(player, new S2CDebugUpdateMapPacket(stringId));
        }
    }

    private static boolean canMultiThread(Level level) {
        UltimateMapAtlasesServerConfig.UpdateType updateType = UltimateMapAtlasesServerConfigManager.INSTANCE.mapUpdateMultithreaded;
        return switch (updateType) {
            case OFF -> false;
            case ALWAYS_ON -> true;
            case SINGLE_PLAYER_ONLY -> !level.getServer().isPublished();
        };
    }

    private void updateMarkers(Player player, int maxRange) {
        int step = data.getHoldingPlayer(player).step;
        int frenquency = UltimateMapAtlasesServerConfigManager.INSTANCE.markersUpdatePeriod;
        if (step % frenquency == 0) {
            MapItemSavedDataAccessor accessor = (MapItemSavedDataAccessor) data;
            Level level = player.level();

            var markers = accessor.getBannerMarkers();
            Iterator<MapBanner> iterator = markers.values().iterator();
            while (iterator.hasNext()) {
                var banner = iterator.next();
                BlockPos pos = banner.pos();
                if (pos.distToCenterSqr(player.position()) < (maxRange * maxRange)) {
                    if (level.isLoaded(pos)) {
                        MapBanner mapbanner1 = MapBanner.fromWorld(level, pos);
                        if (!banner.equals(mapbanner1)) {
                            iterator.remove();
                            accessor.invokeRemoveDecoration(banner.getId());
                        }
                    }
                }
            }

            List<String> blockKeysToRemove = new ArrayList<>();
            for (String key : accessor.getDecorations().keySet()) {
                if (!key.startsWith("block_")) continue;
                BlockPos pos = C2SToggleBlockMarkerPacket.blockPosFromKey(key);
                if (pos.distToCenterSqr(player.position()) < (maxRange * maxRange)) {
                    if (level.isLoaded(pos)) {
                        BlockState state = level.getBlockState(pos);
                        boolean valid = (state.getBlock() instanceof BedBlock
                                        && state.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD)
                                    || state.getBlock() instanceof CampfireBlock;
                        if (!valid) blockKeysToRemove.add(key);
                    }
                }
            }
            for (String key : blockKeysToRemove) {
                accessor.invokeRemoveDecoration(key);
                accessor.invokeSetDecorationsDirty();
                data.setDirty();
            }

            if (MapAtlasesMod.MOONLIGHT) MoonlightCompat.updateMarkers(data, player, maxRange);
        }
    }



    private static final ExecutorService EXECUTORS = Executors.newFixedThreadPool(6);


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MapDataHolder holder = (MapDataHolder) o;
        return Objects.equals(data, holder.data);
    }

    @Override
    public int hashCode() {
        return Objects.hash(data);
    }

    public ItemStack createExistingMapItem() {
        return type.createExistingMapItem(id, slice.height());
    }
}
