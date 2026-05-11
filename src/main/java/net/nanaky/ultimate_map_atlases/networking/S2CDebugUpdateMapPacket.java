package net.nanaky.ultimate_map_atlases.networking;

import net.nanaky.moonlight.api.platform.network.ChannelHandler;
import net.nanaky.moonlight.api.platform.network.Message;
import net.minecraft.network.FriendlyByteBuf;
import net.nanaky.ultimate_map_atlases.client.MapAtlasesClient;

public class S2CDebugUpdateMapPacket implements Message {
    private final String mapId;

    public S2CDebugUpdateMapPacket(FriendlyByteBuf buf) {
        this.mapId = buf.readUtf();
    }

    public S2CDebugUpdateMapPacket(String map) {
        this.mapId = map;
    }

    @Override
    public void writeToBuffer(FriendlyByteBuf buf) {
        buf.writeUtf(mapId);

    }

    @Override
    public void handle(ChannelHandler.Context context) {
        MapAtlasesClient.debugMapUpdated( mapId);
    }
}
