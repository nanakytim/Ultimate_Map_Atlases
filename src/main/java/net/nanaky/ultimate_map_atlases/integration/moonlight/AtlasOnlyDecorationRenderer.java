package net.nanaky.ultimate_map_atlases.integration.moonlight;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.nanaky.moonlight.api.map.CustomMapDecoration;
import net.nanaky.moonlight.api.map.client.DecorationRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.jetbrains.annotations.Nullable;
import net.nanaky.ultimate_map_atlases.client.MapAtlasesClient;

public class AtlasOnlyDecorationRenderer<T extends CustomMapDecoration> extends DecorationRenderer<T> {

    public AtlasOnlyDecorationRenderer(Identifier texture) {
        super(texture);
    }

    @Override
    public boolean render(T decoration, PoseStack matrixStack, VertexConsumer vertexBuilder, MultiBufferSource buffer, @Nullable MapItemSavedData mapData, boolean isOnFrame, int light, int index, boolean rendersText) {
        if (!MapAtlasesClient.isDrawingAtlas()) return false;
        return super.render(decoration, matrixStack, vertexBuilder, buffer, mapData, isOnFrame, light, index, rendersText);
    }

}
