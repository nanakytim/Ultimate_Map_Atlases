package net.nanaky.ultimate_map_atlases.client.screen;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ColumnPos;
import net.nanaky.ultimate_map_atlases.client.CompoundTooltip;
import net.nanaky.ultimate_map_atlases.integration.moonlight.ClientMarkers;
import net.nanaky.ultimate_map_atlases.networking.C2SMarkerPacket;
import net.nanaky.ultimate_map_atlases.networking.MapAtlasesNetworking;
import net.nanaky.ultimate_map_atlases.utils.MapDataHolder;

public class PinButton extends BookmarkButton {
    protected PinButton(int pX, int pY, AtlasOverviewScreen screen) {
        super(pX, pY, 16, 16, 30, 152, screen);
        Tooltip tooltip = Tooltip.create(Component.translatable("message.map_atlases.pin"));
        if (Minecraft.getInstance().options.advancedItemTooltips) {
            tooltip = CompoundTooltip.create(
                    Component.translatable("message.map_atlases.pin"),
                    Component.translatable("message.map_atlases.pin.info").withStyle(ChatFormatting.GRAY));
        }
        this.setTooltip(tooltip);
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        parentScreen.toggleCursorAction(CursorAction.PLACING_PIN);
    }

    public static void placePin(MapDataHolder map, ColumnPos pos, String text, int index) {
        ClientMarkers.addPin(map, pos, text, index);
        MapAtlasesNetworking.CHANNEL.sendToServer(new C2SMarkerPacket(pos, map.stringId, text.isEmpty() ? null : text, index));
    }

}
