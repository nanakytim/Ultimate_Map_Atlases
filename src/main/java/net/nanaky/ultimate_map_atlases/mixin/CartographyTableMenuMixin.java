package net.nanaky.ultimate_map_atlases.mixin;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.nanaky.ultimate_map_atlases.MapAtlasesMod;
import net.nanaky.ultimate_map_atlases.PlatStuff;
import net.nanaky.ultimate_map_atlases.client.MapAtlasesClient;
import net.nanaky.ultimate_map_atlases.config.UltimateMapAtlasesClientConfigManager;
import net.nanaky.ultimate_map_atlases.config.UltimateMapAtlasesServerConfigManager;
import net.nanaky.ultimate_map_atlases.item.MapAtlasItem;
import net.nanaky.ultimate_map_atlases.map_collection.IMapCollection;
import net.nanaky.ultimate_map_atlases.utils.AtlasCartographyTable;
import net.nanaky.ultimate_map_atlases.utils.MapAtlasesAccessUtils;
import net.nanaky.ultimate_map_atlases.utils.MapDataHolder;
import net.nanaky.ultimate_map_atlases.utils.Slice;

import java.util.concurrent.atomic.AtomicReference;


@Mixin(CartographyTableMenu.class)
public abstract class CartographyTableMenuMixin extends AbstractContainerMenu implements AtlasCartographyTable {

    @Shadow
    @Final
    private ResultContainer resultContainer;

    @Shadow
    @Final
    private ContainerLevelAccess access;

    @Shadow
    public abstract void slotsChanged(Container pInventory);

    @Shadow
    @Final
    public Container container;

    @Unique
    private int mapatlases$selectedMapIndex;
    @Nullable
    @Unique
    private Slice mapatlases$selectedSlice;

    protected CartographyTableMenuMixin(@Nullable MenuType<?> arg, int i) {
        super(arg, i);
    }


    @Inject(method = "setupResultSlot", at = @At("HEAD"), cancellable = true)
    void mapAtlas$UpdateResult(ItemStack topItem, ItemStack bottomItem, ItemStack oldResult, CallbackInfo info) {
        if (topItem.getCount() > 1 || bottomItem.getCount() > 1) {
            this.resultContainer.setItem(CartographyTableMenu.RESULT_SLOT, ItemStack.EMPTY);
            info.cancel();
            return;
        }
        if (!topItem.is(MapAtlasesMod.MAP_ATLAS.get())) return;
        if (PlatStuff.isShear(bottomItem)) {
            this.access.execute((world, blockPos) -> {
                var maps = MapAtlasItem.getMaps(topItem, world);
                if (maps.isEmpty()) return;
                if (mapatlases$selectedMapIndex > maps.getCount()) {
                    mapatlases$selectedMapIndex = 0;
                }
                MapDataHolder map = maps.getAll().get(mapatlases$selectedMapIndex);
                ItemStack result = map.createExistingMapItem();
                this.mapatlases$selectedSlice = map.slice;
                this.resultContainer.setItem(CartographyTableMenu.RESULT_SLOT, result);
                this.broadcastChanges();
                info.cancel();
            });
        }
        else if (bottomItem.is(MapAtlasesMod.MAP_ATLAS.get())) {
            this.access.execute((world, blockPos) -> {
                ItemStack result = topItem.copy();
                IMapCollection resultMaps = MapAtlasItem.getMaps(result, world);
                IMapCollection bottomMaps = MapAtlasItem.getMaps(bottomItem, world);
                if (resultMaps.getScale() != bottomMaps.getScale()) return;
                int[] idsToADd = bottomMaps.getAllIds();
                for (var i : idsToADd) {
                    resultMaps.add(i, world);
                }
                MapAtlasItem.setEmptyMaps(result, (int) Math.ceil((MapAtlasItem.getEmptyMaps(result) + MapAtlasItem.getEmptyMaps(bottomItem)) / 2f));

                result.grow(1);
                this.resultContainer.setItem(CartographyTableMenu.RESULT_SLOT, result);
                this.broadcastChanges();
                info.cancel();
            });

        }
        else if (bottomItem.getItem() == Items.MAP
                || (UltimateMapAtlasesServerConfigManager.INSTANCE.acceptPaperForEmptyMaps && bottomItem.getItem() == Items.PAPER)) {
            this.access.execute((world, blockPos) -> {
                ItemStack result = topItem.copy();
                int amountToAdd = MapAtlasesAccessUtils.getMapCountToAdd(topItem, bottomItem, world);
                MapAtlasItem.increaseEmptyMaps(result, amountToAdd);
                this.resultContainer.setItem(CartographyTableMenu.RESULT_SLOT, result);
                this.broadcastChanges();
                info.cancel();
            });
        }
        else if (bottomItem.getItem() == Items.FILLED_MAP) {
            this.access.execute((world, blockPos) -> {
                ItemStack result = topItem.copy();
                Integer mapId = MapAtlasesAccessUtils.getMapId(bottomItem);
                IMapCollection maps = MapAtlasItem.getMaps(result, world);
                if (mapId != null && maps.add(mapId, world)) {
                    this.resultContainer.setItem(CartographyTableMenu.RESULT_SLOT, result);
                    this.broadcastChanges();
                    info.cancel();
                }
            });
        }
    }

    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    void mapAtlas$TransferSlot(Player player, int index, CallbackInfoReturnable<ItemStack> info) {
        if (index == 2) {
            Slot resultSlot = this.slots.get(2);
            if (resultSlot.hasItem()) {
                ItemStack result = resultSlot.getItem().copy();
                resultSlot.onTake(player, resultSlot.getItem());
                player.getInventory().add(result);
            }
            info.setReturnValue(ItemStack.EMPTY);
            return;
        }
        if (index >= 0 && index <= 2) return;

        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return;

        ItemStack stack = slot.getItem();

        if (PlatStuff.isShear(stack)) {
            if (this.slots.get(0).hasItem() && this.slots.get(0).getItem().is(MapAtlasesMod.MAP_ATLAS.get())) {
                if (!this.moveItemStackTo(stack, 1, 2, false)) {
                    info.setReturnValue(ItemStack.EMPTY);
                }
            } else {
                info.setReturnValue(ItemStack.EMPTY);
            }
            return;
        }

        if (stack.getItem() == Items.PAPER) {
            if (this.slots.get(0).hasItem() && 
                (this.slots.get(0).getItem().is(MapAtlasesMod.MAP_ATLAS.get()) || 
                this.slots.get(0).getItem().getItem() == Items.FILLED_MAP)) {
                if (!this.moveItemStackTo(stack, 1, 2, false)) {
                    info.setReturnValue(ItemStack.EMPTY);
                }
            } else {
                info.setReturnValue(ItemStack.EMPTY);
            }
            return;
        }

        if (stack.getItem() == MapAtlasesMod.MAP_ATLAS.get()) {
            ItemStack single = stack.copyWithCount(1);
            if (!this.slots.get(0).hasItem()) {
                this.slots.get(0).set(single);
                stack.shrink(1);
            } else if (this.slots.get(0).getItem().is(MapAtlasesMod.MAP_ATLAS.get()) && !this.slots.get(1).hasItem()) {
                this.slots.get(1).set(single);
                stack.shrink(1);
            } else {
                info.setReturnValue(ItemStack.EMPTY);
            }
            info.setReturnValue(ItemStack.EMPTY);
            return;
        }

        if (stack.getItem() == Items.FILLED_MAP) {
            if (!this.slots.get(0).hasItem()) {
                this.moveItemStackTo(stack, 0, 1, false);
            } else if (!this.slots.get(1).hasItem()) {
                this.moveItemStackTo(stack, 1, 2, false);
            } else {
                info.setReturnValue(ItemStack.EMPTY);
            }
            info.setReturnValue(ItemStack.EMPTY);
            return;
        }

        if (!this.moveItemStackTo(stack, 0, 1, false)) {
            info.setReturnValue(ItemStack.EMPTY);
        }
    }

    @Override
    public void mapatlases$setSelectedMapIndex(int index) {
        mapatlases$selectedMapIndex = index;
    }

    @Override
    public int mapatlases$getSelectedMapIndex() {
        return mapatlases$selectedMapIndex;
    }

    @Nullable
    @Override
    public Slice mapatlases$getSelectedSlice() {
        return mapatlases$selectedSlice;
    }

    @Override
    public void mapatlases$removeSelectedMap(ItemStack atlas) {
        access.execute((level, pos) -> {
            var maps = MapAtlasItem.getMaps(atlas, level);
            MapDataHolder m = maps.getAll().get(mapatlases$selectedMapIndex);
            maps.remove(m);
        });
    }

    @Override
    public boolean clickMenuButton(Player pPlayer, int pId) {
        ItemStack atlas = this.slots.get(0).getItem();
        if (pId == 4 || pId == 5) {
            AtomicReference<Level> l = new AtomicReference<>();
            access.execute((level, pos) -> {
                l.set(level);
            });
            if (l.get() == null) {
                try {
                    MapAtlasesClient.getClientAccess().execute((level, pos) -> l.set(level));
                } catch (Exception ignored) {
                }
            }
            if (l.get() != null) {
                if (atlas.getItem() == MapAtlasesMod.MAP_ATLAS.get()) {
                    var maps = MapAtlasItem.getMaps(atlas, l.get());
                    mapatlases$selectedMapIndex = (mapatlases$selectedMapIndex
                            + (pId == 4 ? maps.getCount() - 1 : 1)) % maps.getCount();
                    try {
                        MapDataHolder map = maps.getAll().get(mapatlases$selectedMapIndex);
                        if (map != null) {
                            this.mapatlases$selectedSlice = map.slice;
                        } else {
                            this.mapatlases$selectedSlice = null;
                        }
                    } catch (Exception e) {
                        int a = 1;
                    }
                }
            }
            this.slotsChanged(this.container);
            return true;
        }
        return super.clickMenuButton(pPlayer, pId);
    }
}
