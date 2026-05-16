package net.nanaky.ultimate_map_atlases.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.client.renderer.state.MapRenderState.MapDecorationRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.nanaky.ultimate_map_atlases.client.screen.AtlasOverviewScreen;
import net.nanaky.ultimate_map_atlases.integration.moonlight.ClientMarkers;
import net.nanaky.ultimate_map_atlases.integration.moonlight.InternalPinDecoration;
import net.nanaky.ultimate_map_atlases.integration.moonlight.MoonlightCompat;
import net.nanaky.ultimate_map_atlases.utils.DecorationHolder;
import net.nanaky.ultimate_map_atlases.utils.MapDataHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class AtlasInHandRenderer {

    private static final RenderType MAP_BACKGROUND =
            RenderTypes.text(Identifier.withDefaultNamespace("textures/map/map_background.png"));
    private static final RenderType MAP_BACKGROUND_CHECKERBOARD =
            RenderTypes.text(Identifier.withDefaultNamespace("textures/map/map_background_checkerboard.png"));
    private static final float MAP_PRE_ROT_SCALE = 0.38F;
    private static final float MAP_GLOBAL_X_POS = -0.5F;
    private static final float MAP_GLOBAL_Y_POS = -0.5F;
    private static final float MAP_GLOBAL_Z_POS = 0.0F;
    private static final float MAP_FINAL_SCALE = 0.0078125F;
    private static final int MAP_BORDER = 7;
    private static final int MAP_HEIGHT = 128;
    private static final int MAP_WIDTH = 128;
    private static final MapRenderState MAP_RENDER_STATE = new MapRenderState();

    public static void render(PoseStack pPoseStack, SubmitNodeCollector submitNodeCollector, int pCombinedLight,
                              ItemStack pStack, Minecraft mc) {
        if (mc.screen instanceof AtlasOverviewScreen) return;

        MapDataHolder state = MapAtlasesClient.getActiveMap();
        if (state != null) {
            MapAtlasesClient.setIsDrawingAtlas(true);
            try {
                pPoseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
                pPoseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
                pPoseStack.scale(MAP_PRE_ROT_SCALE, MAP_PRE_ROT_SCALE, MAP_PRE_ROT_SCALE);
                pPoseStack.translate(MAP_GLOBAL_X_POS, MAP_GLOBAL_Y_POS, MAP_GLOBAL_Z_POS);
                pPoseStack.scale(MAP_FINAL_SCALE, MAP_FINAL_SCALE, MAP_FINAL_SCALE);

                MapItemSavedData data = state.data;
                submitNodeCollector.submitCustomGeometry(
                        pPoseStack,
                        data == null ? MAP_BACKGROUND : MAP_BACKGROUND_CHECKERBOARD,
                        (pose, vertexConsumer) -> {
                            vertexConsumer.addVertex(pose, -MAP_BORDER, MAP_HEIGHT + MAP_BORDER, 0.0F)
                                    .setColor(-1)
                                    .setUv(0.0F, 1.0F)
                                    .setLight(pCombinedLight);
                            vertexConsumer.addVertex(pose, MAP_WIDTH + MAP_BORDER, MAP_HEIGHT + MAP_BORDER, 0.0F)
                                    .setColor(-1)
                                    .setUv(1.0F, 1.0F)
                                    .setLight(pCombinedLight);
                            vertexConsumer.addVertex(pose, MAP_WIDTH + MAP_BORDER, -MAP_BORDER, 0.0F)
                                    .setColor(-1)
                                    .setUv(1.0F, 0.0F)
                                    .setLight(pCombinedLight);
                            vertexConsumer.addVertex(pose, -MAP_BORDER, -MAP_BORDER, 0.0F)
                                    .setColor(-1)
                                    .setUv(0.0F, 0.0F)
                                    .setLight(pCombinedLight);
                        }
                );
                if (data != null) {
                    renderMapData(pPoseStack, submitNodeCollector, pCombinedLight, mc, state, data);
                }
            } finally {
                MapAtlasesClient.setIsDrawingAtlas(false);
            }
        }
    }

    private static void renderMapData(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int combinedLight,
                                      Minecraft minecraft, MapDataHolder state, MapItemSavedData data) {
        List<DecorationHolder> customPins = new ArrayList<>(MoonlightCompat.getCustomDecorations(state));

        var mapRenderer = minecraft.getMapRenderer();
        mapRenderer.extractRenderState(new MapId(state.id), data, MAP_RENDER_STATE);
        removeBackingPinStates(MAP_RENDER_STATE, customPins);

        List<MapDecoration> blockMarkerDecorations = new ArrayList<>();
        Map<String, MapDecoration> decorations = MapAtlasesClient.getMutableDecorations(data);
        for (var entry : decorations.entrySet()) {
            if (entry.getKey().startsWith(MoonlightCompat.BLOCK_MARKER_PREFIX)) {
                MapDecoration deco = entry.getValue();
                MAP_RENDER_STATE.decorations.removeIf(rs -> rs.x == deco.x() && rs.y == deco.y());
                blockMarkerDecorations.add(deco);
            }
        }

        mapRenderer.render(MAP_RENDER_STATE, poseStack, submitNodeCollector, false, combinedLight);
        renderCustomPins(poseStack, submitNodeCollector, combinedLight, customPins);
        renderBlockMarkers(poseStack, submitNodeCollector, combinedLight, blockMarkerDecorations);
    }

    private static void removeBackingPinStates(MapRenderState renderState, List<DecorationHolder> customPins) {
        if (customPins.isEmpty()) {
            return;
        }
        renderState.decorations.removeIf(renderStateDecoration -> customPins.stream()
                .filter(holder -> holder.deco() instanceof InternalPinDecoration)
                .map(holder -> ((InternalPinDecoration) holder.deco()).decoration())
                .anyMatch(decoration -> sameDecoration(renderStateDecoration, decoration)));
    }

    private static boolean sameDecoration(MapDecorationRenderState renderState, MapDecoration decoration) {
        return renderState.x == decoration.x()
                && renderState.y == decoration.y()
                && renderState.rot == decoration.rot()
                && Objects.equals(renderState.name, decoration.name().orElse(null));
    }

    private static void renderCustomPins(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int combinedLight,
                                         List<DecorationHolder> customPins) {
        for (var holder : customPins) {
            if (!(holder.deco() instanceof InternalPinDecoration pin)) {
                continue;
            }
            float x = MAP_WIDTH / 2f + pin.decoration().x() / 2f;
            float y = MAP_HEIGHT / 2f + pin.decoration().y() / 2f;
            renderPin(poseStack, submitNodeCollector, combinedLight, pin, x, y);
        }
    }

    private static void renderBlockMarkers(PoseStack poseStack, SubmitNodeCollector submitNodeCollector,
                                           int combinedLight, List<MapDecoration> decorations) {
        for (MapDecoration deco : decorations) {
            float x = MAP_WIDTH / 2f + deco.x() / 2f;
            float y = MAP_HEIGHT / 2f + deco.y() / 2f;
            Identifier texture = MapAtlasesClient.getDecorationTexture(deco);
            poseStack.pushPose();
            poseStack.translate(x, y, -0.02F);
            poseStack.scale(4.0F, 4.0F, 3.0F);
            poseStack.translate(-0.125F, 0.125F, 0.0F);
            submitNodeCollector.order(0).submitCustomGeometry(
                    poseStack,
                    RenderTypes.text(texture),
                    (pose, vertexConsumer) -> {
                        vertexConsumer.addVertex(pose, -1.0F,  1.0F, -0.001F).setColor(-1).setUv(0.0F, 1.0F).setLight(combinedLight);
                        vertexConsumer.addVertex(pose,  1.0F,  1.0F, -0.001F).setColor(-1).setUv(1.0F, 1.0F).setLight(combinedLight);
                        vertexConsumer.addVertex(pose,  1.0F, -1.0F, -0.001F).setColor(-1).setUv(1.0F, 0.0F).setLight(combinedLight);
                        vertexConsumer.addVertex(pose, -1.0F, -1.0F, -0.001F).setColor(-1).setUv(0.0F, 0.0F).setLight(combinedLight);
                    }
            );
            poseStack.popPose();
        }
    }

    private static void renderPin(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int combinedLight,
                                  InternalPinDecoration pin, float x, float y) {
        poseStack.pushPose();
        poseStack.translate(x, y, -0.02F);
        poseStack.scale(4.0F, 4.0F, 3.0F);
        poseStack.translate(-0.125F, 0.125F, 0.0F);
        submitNodeCollector.order(1).submitCustomGeometry(
                poseStack,
                RenderTypes.text(ClientMarkers.getPinTexture(pin.index(), false)),
                (pose, vertexConsumer) -> {
                    vertexConsumer.addVertex(pose, -1.0F, 1.0F, -0.001F)
                            .setColor(-1)
                            .setUv(0.0F, 1.0F)
                            .setLight(combinedLight);
                    vertexConsumer.addVertex(pose, 1.0F, 1.0F, -0.001F)
                            .setColor(-1)
                            .setUv(1.0F, 1.0F)
                            .setLight(combinedLight);
                    vertexConsumer.addVertex(pose, 1.0F, -1.0F, -0.001F)
                            .setColor(-1)
                            .setUv(1.0F, 0.0F)
                            .setLight(combinedLight);
                    vertexConsumer.addVertex(pose, -1.0F, -1.0F, -0.001F)
                            .setColor(-1)
                            .setUv(0.0F, 0.0F)
                            .setLight(combinedLight);
                }
        );
        poseStack.popPose();
    }
}