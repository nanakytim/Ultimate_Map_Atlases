package net.nanaky.ultimate_map_atlases.networking;

import net.nanaky.moonlight.api.platform.network.ChannelHandler;
import net.nanaky.moonlight.api.platform.network.Message;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.nanaky.ultimate_map_atlases.MapAtlasesMod;
import net.nanaky.ultimate_map_atlases.integration.moonlight.ClientMarkers;
import net.nanaky.ultimate_map_atlases.mixin.MapItemSavedDataAccessor;
import net.nanaky.ultimate_map_atlases.utils.MapAtlasesAccessUtils;

public class C2SToggleBlockMarkerPacket implements Message {

    private final BlockPos pos;
    private final String mapId;

    public C2SToggleBlockMarkerPacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.mapId = buf.readUtf();
    }

    public C2SToggleBlockMarkerPacket(BlockPos pos, String mapId) {
        this.pos = pos;
        this.mapId = mapId;
    }

    @Override
    public void writeToBuffer(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeUtf(mapId);
    }

    @Override
    public void handle(ChannelHandler.Context context) {
        if (!(context.getSender() instanceof ServerPlayer player)) return;
        handleOnServer(pos, mapId, player);
    }

    public static BlockPos blockPosFromKey(String key) {
        String coords = key.substring("block_".length());
        String[] parts = coords.split(", ");
        return new BlockPos(
                Integer.parseInt(parts[0]),
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2]));
    }

    public static void checkBlockMarkers(Level level, MapItemSavedData data) {
        if (!(data instanceof MapItemSavedDataAccessor accessor)) return;

        List<String> toRemove = new ArrayList<>();
        for (String key : accessor.getDecorations().keySet()) {
            if (!key.startsWith("block_")) continue;
            BlockPos pos = blockPosFromKey(key);
            BlockState state = level.getBlockState(pos);
            boolean valid = (state.getBlock() instanceof BedBlock
                            && state.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD)
                        || state.getBlock() instanceof CampfireBlock;
            if (!valid) toRemove.add(key);
        }

        for (String key : toRemove) {
            accessor.invokeRemoveDecoration(key);
            data.setDirty();
        }
    }

    public static void handleOnServer(BlockPos pos, String mapId, ServerPlayer player) {
        Level level = player.level();
        int mapIntId = MapAtlasesAccessUtils.findMapIntFromString(mapId);
        MapItemSavedData data = level.getMapData(new MapId(mapIntId));
        if (data == null) {
            MapAtlasesMod.LOGGER.warn("handleOnServer: no map data found for {}", mapId);
            return;
        }
        if (!(data instanceof MapItemSavedDataAccessor accessor)) {
            MapAtlasesMod.LOGGER.warn("handleOnServer: data is not accessor-backed");
            return;
        }

        BlockState state = level.getBlockState(pos);

        if (state.getBlock() instanceof BedBlock) {
            BlockPos headPos;
            BlockState headState;
            if (state.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD) {
                headPos = pos;
                headState = state;
            } else {
                BlockPos relative = pos.relative(state.getValue(BlockStateProperties.HORIZONTAL_FACING));
                BlockState relativeState = level.getBlockState(relative);
                if (relativeState.getBlock() instanceof BedBlock &&
                        relativeState.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD) {
                    headPos = relative;
                    headState = relativeState;
                } else {
                    relative = pos.relative(state.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite());
                    relativeState = level.getBlockState(relative);
                    headPos = relative;
                    headState = relativeState;
                }
            }

            String key = "block_" + headPos.toShortString();

            if (accessor.getDecorations().containsKey(key)) {
                accessor.invokeRemoveDecoration(key);
                accessor.invokeSetDecorationsDirty();
                data.setDirty();
                return;
            }

            String dyeKey = getBedDyeKey(headState.getBlock());
            var decorationType = ClientMarkers.BlockMarkerData.resolveDecorationType(dyeKey);
            if (decorationType == null) return;

            accessor.invokeAddDecoration(decorationType, level, key,
                    headPos.getX() + 0.5, headPos.getZ() + 0.5, 180.0, null);
            accessor.invokeSetDecorationsDirty();
            data.setDirty();

        } else if (state.getBlock() instanceof CampfireBlock) {
            String key = "block_" + pos.toShortString();

            if (accessor.getDecorations().containsKey(key)) {
                accessor.invokeRemoveDecoration(key);
                accessor.invokeSetDecorationsDirty();
                data.setDirty();
                return;
            }

            String blockName = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
            var campfireType = blockName.contains("soul")
                    ? MapAtlasesMod.SOUL_CAMPFIRE_DECORATION
                    : MapAtlasesMod.CAMPFIRE_DECORATION;

            accessor.invokeAddDecoration(campfireType, level, key,
                    pos.getX() + 0.5, pos.getZ() + 0.5, 180.0, null);
            accessor.invokeSetDecorationsDirty();
            data.setDirty();

        } else {
            MapAtlasesMod.LOGGER.warn("handleOnServer: block is neither bed nor campfire: {}", state.getBlock());
        }
    }

    private static String getBedDyeKey(Block block) {
        String name = BuiltInRegistries.BLOCK.getKey(block).getPath();
        if (name.endsWith("_bed")) {
            return "bed_" + name.substring(0, name.length() - 4);
        }
        return "bed_white";
    }
}