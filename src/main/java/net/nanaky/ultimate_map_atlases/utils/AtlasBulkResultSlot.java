package net.nanaky.ultimate_map_atlases.utils;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.function.IntSupplier;

public class AtlasBulkResultSlot extends Slot {
    private final Slot vanilla;
    private final Slot bottomSlot;
    private final IntSupplier extraBottomConsumed;

    public AtlasBulkResultSlot(Slot vanilla, Slot bottomSlot, IntSupplier extraBottomConsumed) {
        super(vanilla.container, vanilla.getContainerSlot(), vanilla.x, vanilla.y);
        this.index = vanilla.index;
        this.vanilla = vanilla;
        this.bottomSlot = bottomSlot;
        this.extraBottomConsumed = extraBottomConsumed;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return false;
    }

    @Override
    public void onTake(Player player, ItemStack stack) {
        int extra = extraBottomConsumed.getAsInt();
        vanilla.onTake(player, stack);
        if (extra > 0 && bottomSlot.hasItem()) {
            bottomSlot.remove(Math.min(extra, bottomSlot.getItem().getCount()));
        }
    }
}