package net.nanaky.ultimate_map_atlases.utils;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public record PriorityMarkerInfo(double worldX, double worldZ, Identifier texture, Component name) {
}