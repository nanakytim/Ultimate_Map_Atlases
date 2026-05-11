package net.nanaky.ultimate_map_atlases.networking;

import net.nanaky.moonlight.api.platform.network.ChannelHandler;
import net.nanaky.moonlight.api.platform.network.Message;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.nanaky.ultimate_map_atlases.item.MapAtlasItem;
import net.nanaky.ultimate_map_atlases.utils.MapAtlasesAccessUtils;

public class C2SRemoveMapPacket implements Message {

    private final int mapId;

    public C2SRemoveMapPacket(FriendlyByteBuf buf) {
        this.mapId = buf.readInt();
    }

    public C2SRemoveMapPacket(int mapId) {
        this.mapId = mapId;
    }

    @Override
    public void writeToBuffer(FriendlyByteBuf buf) {
        buf.writeInt(mapId);
    }

    @Override
    public void handle(ChannelHandler.Context context) {
        if (!(context.getSender() instanceof ServerPlayer player)) return;

        ItemStack atlas = MapAtlasesAccessUtils.getAtlasFromPlayerByConfig(player);
        if (!atlas.isEmpty()) {
            MapAtlasItem.removeMap(atlas, mapId, player);
        }
    }
}
