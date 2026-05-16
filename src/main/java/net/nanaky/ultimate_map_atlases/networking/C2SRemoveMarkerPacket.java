package net.nanaky.ultimate_map_atlases.networking;

import net.nanaky.moonlight.api.platform.network.ChannelHandler;
import net.nanaky.moonlight.api.platform.network.Message;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapBanner;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.nanaky.ultimate_map_atlases.MapAtlasesMod;
import net.nanaky.ultimate_map_atlases.integration.moonlight.MoonlightCompat;
import net.nanaky.ultimate_map_atlases.mixin.MapItemSavedDataAccessor;
import net.nanaky.ultimate_map_atlases.utils.MapAtlasesAccessUtils;

public class C2SRemoveMarkerPacket implements Message {
    private final int decoHash;
    private final String mapId;
    private final int markerType;
    private final String decoKey;
    private final int worldX;
    private final int worldZ;

    public C2SRemoveMarkerPacket(FriendlyByteBuf buf) {
        this.mapId      = buf.readUtf();
        this.decoHash   = buf.readVarInt();
        this.markerType = buf.readVarInt();
        this.decoKey    = buf.readUtf();
        this.worldX     = buf.readVarInt();
        this.worldZ     = buf.readVarInt();
    }

    public C2SRemoveMarkerPacket(String map, int decoHash, boolean isCustom, String decoKey) {
        this.mapId      = map;
        this.decoHash   = decoHash;
        this.markerType = isCustom ? 1 : 0;
        this.decoKey    = decoKey;
        this.worldX     = 0;
        this.worldZ     = 0;
    }

    public static C2SRemoveMarkerPacket forBanner(String mapId, int worldX, int worldZ) {
        return new C2SRemoveMarkerPacket(mapId, worldX, worldZ);
    }

    public static C2SRemoveMarkerPacket forBlockMarker(String mapId, String blockDecoKey) {
        return new C2SRemoveMarkerPacket(mapId, blockDecoKey);
    }

    private C2SRemoveMarkerPacket(String mapId, int x, int z) {
        this.mapId      = mapId;
        this.decoHash   = 0;
        this.markerType = 0;
        this.decoKey    = "";
        this.worldX     = x;
        this.worldZ     = z;
    }

    private C2SRemoveMarkerPacket(String mapId, String blockDecoKey) {
        this.mapId      = mapId;
        this.decoHash   = 0;
        this.markerType = 2;
        this.decoKey    = blockDecoKey;
        this.worldX     = 0;
        this.worldZ     = 0;
    }

    @Override
    public void writeToBuffer(FriendlyByteBuf buf) {
        buf.writeUtf(mapId);
        buf.writeVarInt(decoHash);
        buf.writeVarInt(markerType);
        buf.writeUtf(decoKey);
        buf.writeVarInt(worldX);
        buf.writeVarInt(worldZ);
    }

    @Override
    public void handle(ChannelHandler.Context context) {
        if (!(context.getSender() instanceof ServerPlayer player)) return;

        Level level = player.level();
        MapItemSavedData data = level.getMapData(new MapId(MapAtlasesAccessUtils.findMapIntFromString(mapId)));
        if (data == null) return;
        if (!(data instanceof MapItemSavedDataAccessor accessor)) return;

        switch (markerType) {
            case 0 -> {
                int tolerance = (1 << data.scale) / 4 + 1;
                String bestKey = null;
                long bestDistSq = (long) tolerance * tolerance + 1;

                for (var entry : accessor.getBannerMarkers().entrySet()) {
                    MapBanner banner = entry.getValue();
                    long dx = banner.pos().getX() - worldX;
                    long dz = banner.pos().getZ() - worldZ;
                    long distSq = dx * dx + dz * dz;
                    if (distSq < bestDistSq) {
                        bestDistSq = distSq;
                        bestKey = entry.getKey();
                    }
                }

                if (bestKey == null) {
                    MapAtlasesMod.LOGGER.warn(
                            "C2SRemoveMarkerPacket: no banner found near ({},{}) within tolerance {} in map '{}'",
                            worldX, worldZ, tolerance, mapId);
                    return;
                }
                accessor.getBannerMarkers().remove(bestKey);
                accessor.invokeRemoveDecoration(bestKey);
                accessor.invokeSetDecorationsDirty();
                data.setDirty();
            }
            case 1 -> {
                MoonlightCompat.removeCustomDecoration(data, decoHash);
            }
            case 2 -> {
                if (accessor.getDecorations().containsKey(decoKey)) {
                    accessor.invokeRemoveDecoration(decoKey);
                    accessor.invokeSetDecorationsDirty();
                    data.setDirty();
                } else {
                    MapAtlasesMod.LOGGER.warn(
                            "C2SRemoveMarkerPacket: block marker key '{}' not found in map '{}'", decoKey, mapId);
                }
            }
        }
    }
}