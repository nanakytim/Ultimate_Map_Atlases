package net.nanaky.ultimate_map_atlases.networking;

import net.nanaky.moonlight.api.platform.network.ChannelHandler;
import net.nanaky.moonlight.api.platform.network.Message;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.nanaky.ultimate_map_atlases.integration.moonlight.ClientMarkers;

public class S2CWorldHashPacket implements Message {

    public final long seed;
    private final String name;
    // NEW: carry the resolved folder id and save-target type to the client
    private final String folderIdOrIp;
    private final String saveTargetType; // "singleplayer", "multiplayer", or "realms"

    public S2CWorldHashPacket(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        Level level = player.level();

        this.seed = server.overworld().getSeed();
        this.name = server.getWorldData().getLevelName();

        // Resolve the correct save-target type and folder identifier here on
        // the server, where the information is authoritative.
        if (server.isDedicatedServer()) {
            // Real multiplayer server: use the server IP:port as the key so
            // markers are stored per-server, not per-world-name collision.
            this.saveTargetType = "multiplayer";
            String ip = server.getLocalIp();
            int port = server.getPort();
            // Fall back gracefully if getLocalIp() returns null/empty (common
            // on servers that bind to 0.0.0.0).
            String host = (ip != null && !ip.isEmpty() && !ip.equals("0.0.0.0"))
                    ? ip : "server";
            this.folderIdOrIp = host + ":" + port;
        } else if (server.isPublished()) {
            // LAN game — treat as multiplayer so a second player on the same
            // LAN session shares markers correctly.
            this.saveTargetType = "multiplayer";
            int port = server.getPort();
            this.folderIdOrIp = "lan:" + port;
        } else {
            // True singleplayer: use the level folder name as the key.
            this.saveTargetType = "singleplayer";
            this.folderIdOrIp = this.name;
        }
    }

    public S2CWorldHashPacket(FriendlyByteBuf buf) {
        this.seed = buf.readVarLong();
        this.name = buf.readUtf();
        this.folderIdOrIp = buf.readUtf();
        this.saveTargetType = buf.readUtf();
    }

    @Override
    public void writeToBuffer(FriendlyByteBuf buf) {
        buf.writeVarLong(seed);
        buf.writeUtf(name);
        buf.writeUtf(folderIdOrIp);
        buf.writeUtf(saveTargetType);
    }

    @Override
    public void handle(ChannelHandler.Context context) {
        // Tell ClientMarkers which folder/type to use BEFORE loading, so
        // loadClientMarkers resolves the correct file path.
        ClientMarkers.setWorldFolder(folderIdOrIp, saveTargetType);
        ClientMarkers.loadClientMarkers(this.seed, this.name);
    }
}