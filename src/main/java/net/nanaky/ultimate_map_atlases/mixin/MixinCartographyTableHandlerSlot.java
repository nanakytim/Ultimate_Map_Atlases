package net.nanaky.ultimate_map_atlases.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.nanaky.ultimate_map_atlases.MapAtlasesMod;
import net.nanaky.ultimate_map_atlases.PlatStuff;
import net.nanaky.ultimate_map_atlases.config.MapAtlasesConfig;
import net.nanaky.ultimate_map_atlases.utils.AtlasCartographyTable;
import net.nanaky.ultimate_map_atlases.utils.MapAtlasesAccessUtils;

@Mixin(targets = "net.minecraft.world.inventory.CartographyTableMenu$3")
class MixinCartographyTableHandlerFirstSlot {

    @ModifyReturnValue(method = "mayPlace", at = @At("RETURN"))
    boolean mapAtlasCanInsert(boolean original, ItemStack stack) {
        return original || stack.is(MapAtlasesMod.MAP_ATLAS.get());

    }
}

@Mixin(targets = "net.minecraft.world.inventory.CartographyTableMenu$4")
class MixinCartographyTableAbstractContainerMenuSecondSlot {

    @ModifyReturnValue(method = "mayPlace", at = @At("RETURN"))
    boolean mapAtlasCanInsert(boolean original, ItemStack stack) {
        return original || stack.is(MapAtlasesMod.MAP_ATLAS.get()) ||
                MapAtlasesAccessUtils.isValidFilledMap(stack) ||
                PlatStuff.isShear(stack);
    }
}

@Mixin(targets = "net.minecraft.world.inventory.CartographyTableMenu$5")
class MixinCartographyTableAbstractContainerMenuSecondSlotMaps {

    @Shadow(aliases = "this$0") @Final
    CartographyTableMenu field_17303;

    @Inject(method = "onTake", at = @At("HEAD"))
    void mapAtlasOnTakeItem(Player player, ItemStack result, CallbackInfo info) {
        ItemStack atlas = field_17303.slots.get(0).getItem();
        Slot slotOne = field_17303.slots.get(1);
        if (atlas.is(MapAtlasesMod.MAP_ATLAS.get())) {
            ItemStack slotOneItem = slotOne.getItem();
            if (PlatStuff.isShear(slotOneItem)) {
                AtlasCartographyTable menu = (AtlasCartographyTable) this.field_17303;
                menu.mapatlases$removeSelectedMap(atlas);
                atlas.grow(1);
                slotOneItem.grow(1);
                if (player instanceof ServerPlayer serverPlayer && player.level() instanceof ServerLevel serverLevel) {
                    slotOneItem.hurtAndBreak(1, serverLevel, serverPlayer, item -> {
                    });
                }
                menu.mapatlases$setSelectedMapIndex(0);
            } else if (
                    (slotOneItem.is(Items.MAP)
                            || (MapAtlasesConfig.acceptPaperForEmptyMaps.get() && slotOneItem.is(Items.PAPER)))) {
                int amountToTake = MapAtlasesAccessUtils.getMapCountToAdd(atlas, slotOneItem, player.level());
                slotOne.remove(amountToTake - 1);
            } else if (MapAtlasesAccessUtils.isValidFilledMap(slotOneItem)) {
                slotOne.remove(1);
            }
        }
    }
}
