package net.nanaky.ultimate_map_atlases.utils;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component; 
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.nanaky.ultimate_map_atlases.MapAtlasesMod;
import net.nanaky.ultimate_map_atlases.integration.moonlight.ClientMarkers;
import net.nanaky.ultimate_map_atlases.item.MapAtlasItem;
import net.nanaky.ultimate_map_atlases.mixin.MapItemSavedDataAccessor;

import java.util.List;

public class BlockMarkerEvents {

    public static void register() {
        UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
            if (level.isClientSide()) return InteractionResult.PASS;
            if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;
            if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;

            ItemStack item = player.getItemInHand(hand);
            String mapStringId;
            if (item.getItem() instanceof MapItem) {
                var mapId = item.get(DataComponents.MAP_ID);
                if (mapId == null) return InteractionResult.PASS;
                mapStringId = "map_" + mapId.id();
            } else if (item.is(MapAtlasesMod.MAP_ATLAS.get())) {
                var maps = MapAtlasItem.getMaps(item, level);
                var slice = MapAtlasItem.getSelectedSlice(item, serverPlayer.level().dimension());
                MapDataHolder holder = maps.getClosest(serverPlayer, slice);
                if (holder == null) return InteractionResult.PASS;
                mapStringId = holder.stringId;
            } else {
                return InteractionResult.PASS;
            }

            BlockPos pos = hitResult.getBlockPos();
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof BedBlock) && !(state.getBlock() instanceof CampfireBlock)) {
                return InteractionResult.PASS;
            }
            serverPlayer.swing(hand, true);
            return InteractionResult.SUCCESS;
        });

        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
            if (level.isClientSide()) return;
            if (!(player instanceof ServerPlayer serverPlayer)) return;
            boolean isBedHead = state.getBlock() instanceof BedBlock
                    && state.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD;
            boolean isCampfire = state.getBlock() instanceof CampfireBlock;
            if (!isBedHead && !isCampfire) return;

            String key = "block_" + pos.toShortString();

            for (ItemStack item : List.of(player.getMainHandItem(), player.getOffhandItem())) {
            if (item.getItem() instanceof MapItem) {
                var mapId = item.get(DataComponents.MAP_ID);
                if (mapId == null) continue;
                int mapIntId = mapId.id();
                MapItemSavedData data = level.getMapData(new MapId(mapIntId));
                if (data instanceof MapItemSavedDataAccessor accessor
                        && accessor.getDecorations().containsKey(key)) {
                    accessor.invokeRemoveDecoration(key);
                    data.setDirty();
                }
            } else if (item.is(MapAtlasesMod.MAP_ATLAS.get())) {
                var maps = MapAtlasItem.getMaps(item, level);
                for (MapDataHolder holder : maps.getAll()) {
                    MapItemSavedData data = level.getMapData(new MapId(holder.id));
                    if (data instanceof MapItemSavedDataAccessor accessor
                            && accessor.getDecorations().containsKey(key)) {
                        accessor.invokeRemoveDecoration(key);
                        data.setDirty();
                    }
                }
            }
        }
    });
    }
}