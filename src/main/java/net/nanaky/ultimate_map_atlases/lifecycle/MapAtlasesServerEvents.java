package net.nanaky.ultimate_map_atlases.lifecycle;

import net.nanaky.moonlight.api.platform.PlatHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.Tuple;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2i;
import net.nanaky.ultimate_map_atlases.MapAtlasesMod;
import net.nanaky.ultimate_map_atlases.config.MapAtlasesConfig;
import net.nanaky.ultimate_map_atlases.integration.SupplementariesCompat;
import net.nanaky.ultimate_map_atlases.integration.moonlight.EntityRadar;
import net.nanaky.ultimate_map_atlases.item.MapAtlasItem;
import net.nanaky.ultimate_map_atlases.map_collection.IMapCollection;
import net.nanaky.ultimate_map_atlases.map_collection.MapKey;
import net.nanaky.ultimate_map_atlases.networking.MapAtlasesNetworking;
import net.nanaky.ultimate_map_atlases.networking.S2CWorldHashPacket;
import net.nanaky.ultimate_map_atlases.utils.*;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

public class MapAtlasesServerEvents {

    private static final ReentrantLock mutex = new ReentrantLock();

    private static final WeakHashMap<Player, Tuple<Float, HashMap<String, MapUpdateTicket>>> updateQueue = new WeakHashMap<>();
    private static final WeakHashMap<Player, MapDataHolder> lastMapData = new WeakHashMap<>();

    private static class MapUpdateTicket {
        private static final Comparator<MapUpdateTicket> COMPARATOR = Comparator.comparingDouble(MapUpdateTicket::getPriority);

        private final MapDataHolder holder;
        private int waitTime = 20; //set to zero when this is updated.
        private double lastDistance = 1000000;
        private double currentPriority; //bigger the better
        private boolean hasBlankPixels = true;
        private int lastI = 0;
        private final float lowUpdateWeight;

        private MapUpdateTicket(MapDataHolder data) {
            this.holder = data;
            this.updateHasBlankPixels();
            if (data.type == MapType.VANILLA && data.slice.height() != null) {
                hasBlankPixels = false; //hack since these can have blank pixels when populated
                lowUpdateWeight = 0.6f;
            } else lowUpdateWeight = 0.15f;
        }

        public double getPriority() {
            return hasBlankPixels ? currentPriority : currentPriority * 0.15f;
        }

        public void updatePriority(int px, int pz) {
            this.waitTime++;
            double distSquared = Mth.lengthSquared(px - holder.data.centerX, pz - holder.data.centerZ);
            double movingDistanceWeight = 1; // Adjust this based on your preference
            double staticDistanceWeight = 5000; // Adjust this based on your preference
            double waitTimeWeight = 1; // Adjust this based on your preference

            double deltaDist = (lastDistance - distSquared); //for maps getting closer
            this.currentPriority = (movingDistanceWeight * deltaDist) + (waitTimeWeight * this.waitTime * this.waitTime) + (staticDistanceWeight * Mth.fastInvSqrt(distSquared));
            this.lastDistance = distSquared;
        }

        public void updateHasBlankPixels() {
            if (hasBlankPixels) {
                for (; lastI < this.holder.data.colors.length; lastI++) {
                    if (this.holder.data.colors[lastI] == 0) {
                        return;
                    }
                }
                hasBlankPixels = false;
            }
        }

        public float getUpdateFrequencyWeight() {
            return hasBlankPixels ? 1 : lowUpdateWeight;
        }
    }

    public static void onPlayerTick(Player p) {
        ServerPlayer player = ((ServerPlayer) p);

        var server = player.level().getServer();
        ItemStack atlas = MapAtlasesAccessUtils.getAtlasFromInventoryForMinimap(player);
        if (atlas.isEmpty()) return;

        Level level = player.level();
        ResourceKey<Level> dimension = level.dimension();
        IMapCollection maps = MapAtlasItem.getMaps(atlas, level);
        maps.addNotSynced(level);

        Slice slice = MapAtlasItem.getSelectedSlice(atlas, dimension);
        MapKey activeKey = MapKey.at(maps.getScale(), player, slice);

        if ((level.getGameTime() + 13) % 40 == 0) {
            sendSlicesAboveAndBelow(player, atlas, maps, activeKey);
        }

        int playX = player.blockPosition().getX();
        int playZ = player.blockPosition().getZ();
        byte scale = maps.getScale();
        int scaleWidth = (1 << scale) * 128;
        Set<Vector2i> discoveringEdges = getPlayerDiscoveringMapEdges(
                activeKey.mapX(),
                activeKey.mapZ(),
                scaleWidth,
                playX,
                playZ,
                slice.getDiscoveryReach()
        );

        List<MapDataHolder> nearbyExistentMaps = new ArrayList<>();
        int[] offsets = {-1, 0, 1};
        for (int dx : offsets) {
            for (int dz : offsets) {
                MapDataHolder neighbor = maps.select(
                    activeKey.mapX() + dx * scaleWidth,
                    activeKey.mapZ() + dz * scaleWidth,
                    slice
                );
                if (neighbor != null) nearbyExistentMaps.add(neighbor);
            }
        }

        MapDataHolder activeInfo = maps.select(activeKey);
        if (activeInfo == null && !MapAtlasItem.isLocked(atlas)) {
            maybeCreateNewMapEntry(player, atlas, maps, slice, Mth.floor(player.getX()), Mth.floor(player.getZ()));
            activeInfo = maps.select(activeKey);
        }
        if (activeInfo != null && !nearbyExistentMaps.contains(activeInfo)) nearbyExistentMaps.add(activeInfo);
        if (!nearbyExistentMaps.isEmpty()) {
            MapDataHolder selected;
            if (MapAtlasesConfig.roundRobinUpdate.get()) {
                selected = nearbyExistentMaps.get(server.getTickCount() % nearbyExistentMaps.size());
                selected.updateMap(player);
            } else {
                for (int j = 0; j < MapAtlasesConfig.mapUpdatePerTick.get(); j++) {
                    selected = getMapToUpdate(nearbyExistentMaps, player);
                    if (selected != null) selected.updateMap(player);
                }
            }
        }


        for (var mapInfo : nearbyExistentMaps) {
            MapAtlasesAccessUtils.updateMapDataAndSync(mapInfo, player, atlas, TriState.SET_TRUE);
        }
        MapDataHolder lastData = lastMapData.get(player);
        if (lastData != null && !nearbyExistentMaps.contains(lastData)) {
           MapAtlasesAccessUtils.updateMapDataAndSync(lastData, player, atlas, TriState.SET_FALSE);
        }
        lastMapData.put(player, activeInfo);

        if (!MapAtlasesConfig.enableEmptyMapEntryAndFill.get() ||
                MapAtlasItem.isLocked(atlas)) return;

        if (isPlayerTooFarAway(activeKey, player, scaleWidth)) {
            maybeCreateNewMapEntry(player, atlas, maps, slice, Mth.floor(player.getX()),
                    Mth.floor(player.getZ()));
        }
        discoveringEdges.removeIf(e -> nearbyExistentMaps.stream().anyMatch(
                d -> d.data.centerX == e.x && d.data.centerZ == e.y));
        for (var edge : discoveringEdges) {
            maybeCreateNewMapEntry(player, atlas, maps, slice, edge.x, edge.y);
        }
    }

    private static void sendSlicesAboveAndBelow(ServerPlayer player, ItemStack atlas, IMapCollection maps, MapKey activeKey) {
        Slice slice = activeKey.slice();
        var dimension = activeKey.slice().dimension();
        var tree = maps.getHeightTree(dimension, slice.type());
        for (Integer hh : tree) {
            if (hh != slice.heightOrTop()) {
                var below = maps.select(activeKey.mapX(), activeKey.mapZ(), Slice.of(slice.type(), hh, dimension));
                if (below != null)
                    MapAtlasesAccessUtils.updateMapDataAndSync(below, player, atlas, TriState.SET_TRUE);
            }
        }
    }

    private static boolean isTimeToUpdate(MapItemSavedData data, Player player,
                                          Slice slice, int min, int max) {
        int i = 1 << data.scale;
        int range;
        if (slice != null && MapAtlasesMod.SUPPLEMENTARIES) {
            range = (SupplementariesCompat.getSliceReach() / i);
        } else {
            range = 128 / i;
        }
        Level level = player.level();
        int rx = level.getRandom().nextIntBetweenInclusive(-range, range);
        int rz = level.getRandom().nextIntBetweenInclusive(-range, range);
        int x = (int) Mth.clamp((player.getX() + rx - data.centerX) / i + 64, 0, 127);
        int z = (int) Mth.clamp((player.getZ() + rz - data.centerZ) / i + 64, 0, 127);
        boolean filled = data.colors[x + z * 128] != 0;

        int interval = filled ? max : min;

        return level.getGameTime() % interval == 0;
    }

    @Nullable
    private static MapDataHolder getMapToUpdate(List<MapDataHolder> nearbyExistentMaps, ServerPlayer player) {
        var tup = updateQueue.computeIfAbsent(player, a -> new Tuple<>(0f, new HashMap<>()));
        var mapsToUpdate = tup.getB();
        Set<String> nearbyIds = new HashSet<>();
        for (var holder : nearbyExistentMaps) {
            nearbyIds.add(holder.stringId);
            mapsToUpdate.computeIfAbsent(holder.stringId, a -> new MapUpdateTicket(holder));
        }
        int px = player.getBlockX();
        int pz = player.getBlockZ();
        var iterator = mapsToUpdate.entrySet().iterator();
        float totalWeight = 0;
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (!nearbyIds.contains(entry.getKey())) {
                iterator.remove();
            } else {
                MapUpdateTicket ticket = entry.getValue();
                ticket.updatePriority(px, pz);
                totalWeight += ticket.getUpdateFrequencyWeight();
            }
        }
        float callsPerTick = totalWeight / (nearbyExistentMaps.size()); // default with nine empty maps around
        float counter = tup.getA() + callsPerTick;
        boolean shouldUpdate = false;
        if (counter >= 1) {
            shouldUpdate = true;
            counter -= 1;
        }
        tup.setA(counter);

        if (shouldUpdate) {
            MapUpdateTicket selected = mapsToUpdate.values().stream().max(MapUpdateTicket.COMPARATOR).orElseThrow();
            selected.waitTime = 0;
            selected.updateHasBlankPixels();
            return selected.holder;
        }
        return null;
    }


    public static boolean isPlayerTooFarAway(
            MapKey key,
            Player player, int width
    ) {
        return Mth.square(key.mapX() - player.getX()) + Mth.square(key.mapZ() - player.getZ()) > width * width;
    }

    private static void maybeCreateNewMapEntry(
            ServerPlayer player,
            ItemStack atlas,
            IMapCollection maps,
            Slice slice,
            int destX,
            int destZ
    ) {
        Level level = player.level();
        if (ItemStackData.getTag(atlas) == null) {
            MapAtlasItem.setEmptyMaps(atlas, MapAtlasesConfig.pityActivationMapCount.get());
        }

        int emptyCount = MapAtlasItem.getEmptyMaps(atlas);
        boolean bypassEmptyMaps = !MapAtlasesConfig.requireEmptyMapsToExpand.get();
        boolean addedMap = false;
        boolean atlasChanged = false;
        if (!mutex.isLocked() && (emptyCount > 0 || player.isCreative() || bypassEmptyMaps)) {
            mutex.lock();

            if (!player.isCreative() && !bypassEmptyMaps) {
                MapAtlasItem.increaseEmptyMaps(atlas, -1);
                atlasChanged = true;
            }
            Integer height = slice.height();
            if (height != null && !maps.getHeightTree(player.level().dimension(), slice.type()).contains(height)) {
                int error = 1;
            }

            byte scale = maps.getScale();


            ItemStack newMap = slice.createNewMap(destX, destZ, scale, player.level(), atlas);
            Integer mapId = MapAtlasesAccessUtils.getMapId(newMap);

            if (mapId != null) {
                MapDataHolder newData = MapDataHolder.findFromId(level, mapId);
                if (newData != null) {
                    MapAtlasesAccessUtils.updateMapDataAndSync(newData, player, newMap, TriState.SET_TRUE);
                }
                addedMap = maps.add(mapId, level);
                atlasChanged |= addedMap;
            }
            mutex.unlock();
        }

        if (atlasChanged) {
            player.getInventory().setChanged();
        }
        if (addedMap) {
            player.level().playSound(null, player.blockPosition(),
                    MapAtlasesMod.ATLAS_CREATE_MAP_SOUND_EVENT.get(),
                    SoundSource.PLAYERS, 1, 1.0F);
        }
    }

    private static Set<Vector2i> getPlayerDiscoveringMapEdges(
            int xCenter,
            int zCenter,
            int width,
            int xPlayer,
            int zPlayer,
            int reach) {


        int halfWidth = width / 2;
        Set<Vector2i> results = new HashSet<>();
        for (int i = -1; i < 2; i++) {
            for (int j = -1; j < 2; j++) {
                if (i != 0 || j != 0) {
                    int qI = xCenter;
                    int qJ = zCenter;
                    if (i == -1 && xPlayer - reach <= xCenter - halfWidth) {
                        qI -= width;
                    } else if (i == 1 && xPlayer + reach >= xCenter + halfWidth) {
                        qI += width;
                    }
                    if (j == -1 && zPlayer - reach <= zCenter - halfWidth) {
                        qJ -= width;
                    } else if (j == 1 && zPlayer + reach >= zCenter + halfWidth) {
                        qJ += width;
                    }
                    if (!(qI == xCenter && qJ == zCenter)) {
                        results.add(new Vector2i(qI, qJ));
                    }
                }
            }
        }
        return results;
    }


    public static void onPlayerJoin(ServerPlayer player) {
        MapAtlasesNetworking.CHANNEL.sendToClientPlayer(player, new S2CWorldHashPacket(player));
        ItemStack atlas = MapAtlasesAccessUtils.getAtlasFromInventoryForMinimap(player);
        if (atlas.isEmpty()) return;

        Level level = player.level();
        ResourceKey<Level> dimension = level.dimension();
        IMapCollection maps = MapAtlasItem.getMaps(atlas, level);
        maps.addNotSynced(level);

        Slice slice = MapAtlasItem.getSelectedSlice(atlas, dimension);
        MapKey activeKey = MapKey.at(maps.getScale(), player, slice);
        sendSlicesAboveAndBelow(player, atlas, maps, activeKey);

        MapDataHolder activeInfo = maps.select(activeKey);
        if (activeInfo != null) {
            MapAtlasesAccessUtils.updateMapDataAndSync(activeInfo, player, atlas, TriState.SET_TRUE);
        }

        if (PlatHelper.getPlatform().isFabric()) {
            for (var info : maps.getAll()) {
                MapAtlasesAccessUtils.updateMapDataAndSync(info, player, atlas, TriState.SET_TRUE);
            }
            player.inventoryMenu.broadcastFullState();
            if (player.containerMenu != player.inventoryMenu) {
                player.containerMenu.broadcastFullState();
            }
        }
    }

    public static void syncAllMaps(ServerPlayer player) {
        ItemStack atlas = MapAtlasesAccessUtils.getAtlasFromInventoryForMinimap(player);
        if (atlas.isEmpty()) return;
        Level level = player.level();
        IMapCollection maps = MapAtlasItem.getMaps(atlas, level);
        maps.addNotSynced(level);
        for (var mapInfo : maps.getAll()) {
            MapAtlasesAccessUtils.forceFullResync(mapInfo, player, atlas);
        }
    }

    public static void onDimensionUnload() {
        if (MapAtlasesMod.MOONLIGHT) EntityRadar.unloadLevel();
    }

}
