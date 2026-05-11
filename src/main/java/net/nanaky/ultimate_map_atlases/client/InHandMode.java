package net.nanaky.ultimate_map_atlases.client;

import net.minecraft.world.item.ItemStack;
import net.nanaky.ultimate_map_atlases.item.MapAtlasItem;

public enum InHandMode {
    ON, NOT_LOCKED, OFF;

    public boolean isOn(ItemStack stack) {
        return switch (this) {
            case OFF -> false;
            case ON -> true;
            case NOT_LOCKED -> !MapAtlasItem.isLocked(stack);
        };
    }
}