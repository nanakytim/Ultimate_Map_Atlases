package net.nanaky.ultimate_map_atlases.client;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.nanaky.ultimate_map_atlases.MapAtlasesMod;
import net.nanaky.ultimate_map_atlases.integration.moonlight.ClientMarkers;
import net.nanaky.ultimate_map_atlases.item.MapAtlasItem;
import net.nanaky.ultimate_map_atlases.utils.MapAtlasesAccessUtils;
import net.nanaky.ultimate_map_atlases.utils.MapDataHolder;

public class BlockMarkerEventsClient {

    public static void register() {
        UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
            if (!level.isClientSide()) return InteractionResult.PASS;
            if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;

            ItemStack item = player.getItemInHand(hand);
            if (!(item.getItem() instanceof MapItem) && !item.is(MapAtlasesMod.MAP_ATLAS.get())) {
                return InteractionResult.PASS;
            }

            BlockPos pos = hitResult.getBlockPos();
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof BedBlock) && !(state.getBlock() instanceof CampfireBlock)) {
                return InteractionResult.PASS;
            }

            String mapStringId;
            if (item.getItem() instanceof MapItem) {
                var mapId = item.get(DataComponents.MAP_ID);
                if (mapId == null) return InteractionResult.PASS;
                mapStringId = "map_" + mapId.id();
            } else {
                var maps = MapAtlasItem.getMaps(item, level);
                var slice = MapAtlasItem.getSelectedSlice(item, level.dimension());
                MapDataHolder holder = maps.getClosest(player, slice);
                if (holder == null) return InteractionResult.PASS;
                mapStringId = holder.stringId;
            }

            int mapIntId = MapAtlasesAccessUtils.findMapIntFromString(mapStringId);

            BlockPos targetPos = pos;
            BlockState targetState = state;
            if (state.getBlock() instanceof BedBlock) {
                if (state.getValue(BlockStateProperties.BED_PART) == BedPart.FOOT) {
                    BlockPos relative = pos.relative(state.getValue(BlockStateProperties.HORIZONTAL_FACING));
                    BlockState relativeState = level.getBlockState(relative);
                    if (relativeState.getBlock() instanceof BedBlock &&
                            relativeState.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD) {
                        targetPos = relative;
                        targetState = relativeState;
                    } else {
                        targetPos = pos.relative(state.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite());
                        targetState = level.getBlockState(targetPos);
                    }
                }
            }

            String key = "block_" + targetPos.toShortString();

            if (ClientMarkers.hasBlockMarker(mapIntId, key)) {
                ClientMarkers.removeBlockMarker(mapIntId, key);
            } else {
                String dyeKey;
                if (state.getBlock() instanceof CampfireBlock) {
                    String blockName = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
                    dyeKey = blockName.contains("soul") ? "soul_campfire" : "campfire";
                } else {
                    String name = BuiltInRegistries.BLOCK.getKey(targetState.getBlock()).getPath();
                    dyeKey = name.endsWith("_bed") ? "bed_" + name.substring(0, name.length() - 4) : "bed_white";
                }
                Component customName = null;
                if (level.getBlockEntity(targetPos) instanceof net.minecraft.world.level.block.entity.BlockEntity be) {
                    if (be instanceof net.minecraft.world.Nameable nameable && nameable.hasCustomName()) {
                        customName = nameable.getCustomName();
                    }
                }
                ClientMarkers.addBlockMarker(mapIntId, mapStringId, targetPos, key, dyeKey, customName);
            }

            return state.getBlock() instanceof CampfireBlock
            ? InteractionResult.SUCCESS
            : InteractionResult.PASS;
        });

        net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
            if (!level.isClientSide()) return;

            boolean isBedHead = state.getBlock() instanceof BedBlock
                    && state.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD;
            boolean isCampfire = state.getBlock() instanceof CampfireBlock;
            if (!isBedHead && !isCampfire) return;

            String key = "block_" + pos.toShortString();

            ItemStack item = player.getMainHandItem();
            if (!(item.getItem() instanceof MapItem) && !item.is(MapAtlasesMod.MAP_ATLAS.get())) {
                item = player.getOffhandItem();
            }
            if (!(item.getItem() instanceof MapItem) && !item.is(MapAtlasesMod.MAP_ATLAS.get())) return;

            String mapStringId;
            if (item.getItem() instanceof MapItem) {
                var mapId = item.get(DataComponents.MAP_ID);
                if (mapId == null) return;
                mapStringId = "map_" + mapId.id();
            } else if (item.is(MapAtlasesMod.MAP_ATLAS.get())) {
                var maps = MapAtlasItem.getMaps(item, level);
                var slice = MapAtlasItem.getSelectedSlice(item, level.dimension());
                for (MapDataHolder holder : maps.getAll()) {
                    ClientMarkers.removeBlockMarker(holder.id, key);
                }
            }
        });
    }
}