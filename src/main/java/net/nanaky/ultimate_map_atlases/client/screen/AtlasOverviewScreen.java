package net.nanaky.ultimate_map_atlases.client.screen;

import com.mojang.datafixers.util.Pair;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ColumnPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2fStack;
import org.joml.Matrix4d;
import org.joml.Vector4d;
import org.lwjgl.glfw.GLFW;
import net.nanaky.ultimate_map_atlases.MapAtlasesMod;
import net.nanaky.ultimate_map_atlases.client.MapAtlasesClient;
import net.nanaky.ultimate_map_atlases.config.MapAtlasesClientConfig;
import net.nanaky.ultimate_map_atlases.config.MapAtlasesConfig;
import net.nanaky.ultimate_map_atlases.integration.moonlight.ClientMarkers;
import net.nanaky.ultimate_map_atlases.integration.moonlight.MoonlightCompat;
import net.nanaky.ultimate_map_atlases.item.MapAtlasItem;
import net.nanaky.ultimate_map_atlases.map_collection.IMapCollection;
import net.nanaky.ultimate_map_atlases.map_collection.MapKey;
import net.nanaky.ultimate_map_atlases.networking.C2SRemoveMapPacket;
import net.nanaky.ultimate_map_atlases.networking.C2SSelectSlicePacket;
import net.nanaky.ultimate_map_atlases.networking.C2STakeAtlasPacket;
import net.nanaky.ultimate_map_atlases.networking.MapAtlasesNetworking;
import net.nanaky.ultimate_map_atlases.utils.*;

import java.util.*;

import static net.nanaky.ultimate_map_atlases.client.MapAtlasesClient.*;

public class AtlasOverviewScreen extends Screen {

    private final boolean bigTexture = MapAtlasesClientConfig.worldMapBigTexture.get();
    private final Identifier texture = bigTexture ? ATLAS_BACKGROUND_TEXTURE_BIG : ATLAS_BACKGROUND_TEXTURE;

    private final int BOOK_WIDTH = bigTexture ? 290 : 162;
    private final int BOOK_HEIGHT = bigTexture ? 231 : 167;
    private final int H_BOOK_WIDTH = BOOK_WIDTH / 2;
    private final int H_BOOK_HEIGHT = BOOK_HEIGHT / 2;
    private final int MAP_WIDGET_WIDTH = bigTexture ? 256 : 128;
    private final int MAP_WIDGET_HEIGHT = bigTexture ? 192 : 128;
    private final int TEXTURE_W = bigTexture ? 512 : 256;
    private final int OVERLAY_UR = bigTexture ? 304 : 189;
    private final int OVERLAY_UL = bigTexture ? 309 : 194;
    private static final int MAX_DECORATION_BOOKMARKS = 8;

    private final ItemStack atlas;
    private final Player player;
    private final Level level;
    @Nullable
    private final LecternBlockEntity lectern;

    private MapWidget mapWidget;
    private PinNameBox editBox;
    private SliceBookmarkButton sliceButton;
    private SliceArrowButton sliceUp;
    private SliceArrowButton sliceDown;
    private final List<DecorationBookmarkButton> decorationBookmarks = new ArrayList<>();
    private final List<DimensionBookmarkButton> dimensionBookmarks = new ArrayList<>();
    public final float globalScale;
    private final boolean isPinOnly;
    private Slice selectedSlice;
    private boolean initialized = false;
    private CursorAction cursorAction;
    private Pair<MapDataHolder, ColumnPos> partialPin = null;
    private PinButton pinButton;

    @NotNull
    private IMapCollection currentMaps;

    public AtlasOverviewScreen() {
        this(MapAtlasesAccessUtils.getAtlasFromPlayerByConfig(Minecraft.getInstance().player), null, false);
    }

    public AtlasOverviewScreen(ItemStack atlas, @Nullable LecternBlockEntity lectern, boolean placingPin) {
        super(Component.translatable(MapAtlasesMod.MAP_ATLAS.get().getDescriptionId()));
        this.atlas = atlas;
        this.level = Objects.requireNonNull(Minecraft.getInstance().level);
        this.player = Objects.requireNonNull(Minecraft.getInstance().player);
        this.lectern = lectern;
        this.globalScale = lectern == null ?
                (float) (double) MapAtlasesClientConfig.worldMapScale.get() :
                (float) (double) MapAtlasesClientConfig.lecternWorldMapScale.get();

        this.currentMaps = MapAtlasItem.getMaps(atlas, level);
        MapDataHolder closest = getMapClosestToPlayer();
        this.selectedSlice = closest.slice;

        this.isPinOnly = placingPin;
        this.cursorAction = placingPin ? CursorAction.PLACING_PIN : CursorAction.NONE;
        if (!isPinOnly) {
            this.player.playSound(MapAtlasesMod.ATLAS_OPEN_SOUND_EVENT.get(),
                    (float) (double) MapAtlasesClientConfig.soundScalar.get(), 1.0F);
        } else {
            partialPin = Pair.of(closest, new ColumnPos(player.blockPosition().getX(), player.blockPosition().getZ()));
        }
    }

    @NotNull
    private MapDataHolder getMapClosestToPlayer() {
        this.selectedSlice = MapAtlasItem.getSelectedSlice(atlas, player.level().dimension());
        MapDataHolder closest = currentMaps.getClosest(player, selectedSlice);
        if (closest == null) {
            closest = currentMaps.getAll().stream().findFirst().get();
        }
        return closest;
    }

    public ItemStack getAtlas() {
        return atlas;
    }

    public Slice getSelectedSlice() {
        return selectedSlice;
    }


    @Override
    protected void init() {
        super.init();

        this.editBox = new PinNameBox(this.font,
                (width - 100) / 2,
                (height - 20) / 2,
                100, 20,
                Component.translatable("message.map_atlases.marker_name"), this::addNewPin);

        this.sliceButton = new SliceBookmarkButton(
                (width + BOOK_WIDTH) / 2 - 13,
                (height - BOOK_HEIGHT) / 2 + (BOOK_HEIGHT - 36),
                selectedSlice, this);
        this.addRenderableWidget(sliceButton);
        sliceUp = new SliceArrowButton(false, sliceButton, this);
        this.addRenderableWidget(sliceUp);
        sliceDown = new SliceArrowButton(true, sliceButton, this);
        this.addRenderableWidget(sliceDown);

        int i = 0;
        Collection<ResourceKey<Level>> dimensions = currentMaps.getAvailableDimensions();
        int separation = (int) Math.min(22, (BOOK_HEIGHT - 50f) / dimensions.size());
        for (var d : dimensions.stream().sorted(Comparator.comparingInt(e -> {
                    var s = e.identifier().toString();
                    if (MapAtlasesClient.DIMENSION_TEXTURE_ORDER.contains(s)) {
                        return MapAtlasesClient.DIMENSION_TEXTURE_ORDER.indexOf(s);
                    }
                    return 999;
                }
        )).toList()) {
            DimensionBookmarkButton pWidget = new DimensionBookmarkButton(
                    (width + BOOK_WIDTH) / 2 - 10,
                    (height - BOOK_HEIGHT) / 2 + 15 + i * separation, d, this);
            this.addRenderableWidget(pWidget);
            this.dimensionBookmarks.add(pWidget);
            i++;
        }

        this.mapWidget = this.addRenderableWidget(new MapWidget(
                (width - MAP_WIDGET_WIDTH) / 2,
                (height - MAP_WIDGET_HEIGHT) / 2 + (bigTexture ? 2 : 5),
                MAP_WIDGET_WIDTH, MAP_WIDGET_HEIGHT, 3,
                this, getMapClosestToPlayer()));

        this.setFocused(mapWidget);

        int by = 0;
        if (!MapAtlasesConfig.pinMarkerId.get().isEmpty() && MapAtlasesClientConfig.moonlightCompat.get()) {
            this.pinButton = new PinButton((width + BOOK_WIDTH) / 2 + 20,
                    (height - BOOK_HEIGHT) / 2 + 16, this);
            this.addRenderableWidget(pinButton);
            by += 20;
        }
        if (MapAtlasesConfig.shearButton.get()) {
            ShearButton shearButton = new ShearButton((width + BOOK_WIDTH) / 2 + 20,
                    (height - BOOK_HEIGHT) / 2 + 16 + by, this);
            this.addRenderableWidget(shearButton);
        }

        this.selectDimension(level.dimension());

        if (lectern != null) {
            int pY = (int) (globalScale * (height + BOOK_HEIGHT + 4) / 2);
            if (player.mayBuild()) {
                this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, (button) -> {
                    this.onClose();
                }).bounds(this.width / 2 - 100, pY, 98, 20).build());
                this.addRenderableWidget(Button.builder(Component.translatable("lectern.take_book"), (button) -> {
                    MapAtlasesNetworking.CHANNEL.sendToServer(new C2STakeAtlasPacket(lectern.getBlockPos()));
                    this.onClose();
                }).bounds(this.width / 2 + 2, pY, 98, 20).build());
            } else {
                this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, (button) -> {
                    this.onClose();
                }).bounds(this.width / 2 - 100, pY, 200, 20).build());
            }
        }

        if (isPinOnly) focusEditBox(true);

        this.initialized = true;
    }


    protected boolean isValid() {
        return this.minecraft != null && this.minecraft.player != null &&
                (this.lectern == null || (
                        !this.lectern.isRemoved() && this.lectern.getBook().is(MapAtlasesMod.MAP_ATLAS.get())
                                && !playerIsTooFarAwayToEdit(this.minecraft.player, this.lectern)));
    }

    protected static boolean playerIsTooFarAwayToEdit(Player player, LecternBlockEntity tile) {
        return player.distanceToSqr(tile.getBlockPos().getX(), tile.getBlockPos().getY(),
                tile.getBlockPos().getZ()) > 64.0D;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int lastDecorationCount = -1;

    @Override
    public void tick() {
        this.currentMaps = MapAtlasItem.getMaps(atlas, level);
        this.currentMaps.addNotSynced(level);

        if (mapWidget != null) mapWidget.tick();
        if (this.editBox != null && editBox.active) this.editBox.tick();

        int decoCount = 0;
        for (MapDataHolder holder : currentMaps.selectSection(selectedSlice)) {
            decoCount += MapAtlasesClient.getMutableDecorations(holder.data).size();
        }

        if (ClientMarkers.consumeBlockMarkersDirty() || decoCount != lastDecorationCount) {
            lastDecorationCount = decoCount;
            recalculateDecorationWidgets();
        }

        if (!isValid()) this.minecraft.setScreen(null);
        if (false && lectern != null && selectedSlice.dimension().equals(lectern.getLevel().dimension())) {
            var data = currentMaps.getClosest(
                    lectern.getBlockPos().getX(), lectern.getBlockPos().getZ(),
                    selectedSlice).data;
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE && editBox.active) {
            editBox.active = false;
            editBox.visible = false;
            partialPin = null;
            if (isPinOnly) {
                this.onClose();
            }
            return true;
        }
        if (!MapAtlasesClient.PLACE_PIN_KEYBIND.isUnbound() && MapAtlasesClient.PLACE_PIN_KEYBIND.matches(event)) {
            if (!isPinOnly && pinButton != null) {
                this.toggleCursorAction(CursorAction.PLACING_PIN);
            }
            return true;
        }
        if (super.keyPressed(event) || editBox.keyPressed(event)) {
            return true;
        }
        if (!editBox.active && MapAtlasesClient.OPEN_ATLAS_KEYBIND.matches(event)) {
            this.onClose();
            return true;
        }
        for (var v : decorationBookmarks) {
            if (v.keyPressed(event)) return true;
        }
        return false;
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        for (var v : decorationBookmarks) {
            v.keyReleased(event);
        }
        return super.keyReleased(event);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        Matrix3x2fStack poseStack = graphics.pose();

        if (!isPinOnly) {

            poseStack.pushMatrix();

            poseStack.translate(width / 2f, height / 2f);
            poseStack.scale(globalScale, globalScale);

            poseStack.pushMatrix();

            graphics.blit(
                    RenderPipelines.GUI_TEXTURED,
                    texture,
                    -H_BOOK_WIDTH,
                    -H_BOOK_HEIGHT,
                    0,
                    0,
                    BOOK_WIDTH,
                    BOOK_HEIGHT,
                    TEXTURE_W,
                    256
            );
            graphics.blit(
                    RenderPipelines.GUI_TEXTURED,
                    ATLAS_OVERLAY_TEXTURE,
                    -H_BOOK_WIDTH,
                    -H_BOOK_HEIGHT,
                    0,
                    0,
                    BOOK_WIDTH,
                    BOOK_HEIGHT,
                    TEXTURE_W,
                    256
            );

            graphics.nextStratum();
            graphics.blit(
                    RenderPipelines.GUI_TEXTURED,
                    texture,
                    H_BOOK_WIDTH - 10,
                    -H_BOOK_HEIGHT,
                    OVERLAY_UR,
                    0,
                    5,
                    BOOK_HEIGHT,
                    TEXTURE_W,
                    256
            );
            graphics.blit(
                    RenderPipelines.GUI_TEXTURED,
                    texture,
                    -H_BOOK_WIDTH + 5,
                    -H_BOOK_HEIGHT,
                    OVERLAY_UL,
                    0,
                    5,
                    BOOK_HEIGHT,
                    TEXTURE_W,
                    256
            );
            poseStack.popMatrix();

            poseStack.pushMatrix();

            poseStack.translate(-width / 2f, -height / 2f);
            var v = transformMousePos(mouseX, mouseY);
            super.extractRenderState(graphics, (int) v.x, (int) v.y, delta);
            poseStack.popMatrix();

            poseStack.popMatrix();
        }
        poseStack.pushMatrix();
        poseStack.popMatrix();

        if (editBox.active) editBox.extractWidgetRenderState(graphics, mouseX, mouseY, delta);

        else if (MapAtlasesClientConfig.worldMapCrossair.get()) {
            poseStack.pushMatrix();
            graphics.nextStratum();
            graphics.blit(RenderPipelines.GUI_TEXTURED, GUI_ICONS_TEXTURE, (width - 15) / 2, (height - 15) / 2,
                    0, 0, 15, 15, 256, 256);
            poseStack.popMatrix();
        }
    }


    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!editBox.active) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        return editBox.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void mouseMoved(double pMouseX, double pMouseY) {
        if (!editBox.active) {
            var v = transformMousePos(pMouseX, pMouseY);
            super.mouseMoved(v.x, v.y);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!editBox.active) {
            var v = transformMousePos(event.x(), event.y());
            return super.mouseClicked(new MouseButtonEvent(v.x, v.y, event.buttonInfo()), doubleClick);
        } else return editBox.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double pDragX, double pDragY) {
        if (!editBox.active) {
            var v = transformMousePos(event.x(), event.y());
            return super.mouseDragged(new MouseButtonEvent(v.x, v.y, event.buttonInfo()), pDragX, pDragY);
        } else return editBox.mouseDragged(event, pDragX, pDragY);
    }

    public Vector4d transformMousePos(double mouseX, double mouseZ) {
        return scaleVector(mouseX, mouseZ, 1 / globalScale, width, height);
    }

    public Vector4d transformPos(double mouseX, double mouseZ) {
        return scaleVector(mouseX, mouseZ, globalScale, width, height);
    }


    public MapItemSavedData getCenterMapForSelectedDim() {
        if (selectedSlice.dimension().equals(level.dimension())) {
            return getMapClosestToPlayer().data;
        } else {
            MapItemSavedData best = null;
            float averageX = 0;
            float averageZ = 0;
            int count = 0;
            for (MapDataHolder holder : currentMaps.selectSection(selectedSlice)) {
                MapItemSavedData d = holder.data;
                averageX += d.centerX;
                averageZ += d.centerZ;
                count++;
                boolean hasRenderable = false;
                for (MapDecoration decoration : d.getDecorations()) {
                    if (decoration.renderOnFrame()) {
                        hasRenderable = true;
                        break;
                    }
                }
                if (hasRenderable) {
                    if (best != null) {
                        if (Mth.lengthSquared(best.centerX, best.centerZ) > Mth.lengthSquared(d.centerX, d.centerZ)) {
                            best = d;
                        }
                    } else best = d;
                }
            }
            if (best != null) return best;
            if (count == 0) {
                return null;
            }
            averageX /= count;
            averageZ /= count;
            MapDataHolder closest = currentMaps.getClosest(averageX, averageZ, selectedSlice);
            if (closest == null) {
                int error = 1;
            }
            return closest == null ? null : closest.data;
        }
    }

    @Nullable
    protected MapDataHolder findMapWithCenter(int reqXCenter, int reqZCenter) {
        return currentMaps.select(reqXCenter, reqZCenter, selectedSlice);
    }

    @Nullable
    protected MapDataHolder findMapContaining(int x, int z) {
        return currentMaps.select(MapKey.at(currentMaps.getScale(), x, z, selectedSlice));
    }

    @Nullable
    protected MapDataHolder findClosestMap(double x, double z) {
        return currentMaps.getClosest(x, z, selectedSlice);
    }

    public static String getReadableName(Identifier id) {
        return getReadableName(id.getPath());
    }

    @NotNull
    public static String getReadableName(String s) {
        s = s.replace(".", " ").replace("_", " ");
        char[] array = s.toCharArray();
        array[0] = Character.toUpperCase(array[0]);
        for (int j = 1; j < array.length; j++) {
            if (Character.isWhitespace(array[j - 1])) {
                array[j] = Character.toUpperCase(array[j]);
            }
        }
        return new String(array);
    }

    public void selectDimension(ResourceKey<Level> dimension) {
        boolean changedDim = selectedSlice.dimension().equals(dimension);
        if (changedDim) this.selectedSlice = Slice.of(selectedSlice.type(), selectedSlice.height(), dimension);
        updateSlice(!initialized ? selectedSlice : MapAtlasItem.getSelectedSlice(atlas, dimension));
        boolean isWherePlayerIs = level.dimension().equals(dimension);

        int centerX, centerZ;
        if (isWherePlayerIs) {
            byte scale = currentMaps.getScale();
            int scaleWidth = (1 << scale) * 128;
            centerX = Math.floorDiv((int) player.getX(), scaleWidth) * scaleWidth + scaleWidth / 2;
            centerZ = Math.floorDiv((int) player.getZ(), scaleWidth) * scaleWidth + scaleWidth / 2;
        } else {
            MapItemSavedData center = this.getCenterMapForSelectedDim();
            if (center == null) return;
            centerX = center.centerX;
            centerZ = center.centerZ;
        }

        boolean followPlayer = isWherePlayerIs && MapAtlasesClientConfig.worldMapFollowPlayer.get();
        this.mapWidget.resetAndCenter(centerX, centerZ, followPlayer, changedDim, true);
        for (var v : dimensionBookmarks) {
            v.setSelected(v.getDimension().equals(dimension));
        }
        recalculateDecorationWidgets();
    }

    private static final LinkedHashSet<String> priorityIds = new LinkedHashSet<>();

    public void togglePriority(String decorationId) {
        if (!priorityIds.remove(decorationId)) {
            priorityIds.add(decorationId);
        }
        recalculateDecorationWidgets();
    }

    public boolean isPriority(String decorationId) {
        return priorityIds.contains(decorationId);
    }

    protected void recalculateDecorationWidgets() {
        for (var v : decorationBookmarks) {
            this.removeWidget(v);
        }
        decorationBookmarks.clear();

        if (!selectedSlice.hasMarkers()) return;
        List<DecorationHolder> mapIcons = new ArrayList<>();

        for (MapDataHolder holder : currentMaps.selectSection(selectedSlice)) {
            MapItemSavedData data = holder.data;
            for (var d : MapAtlasesClient.getMutableDecorations(data).entrySet()) {
                MapDecoration deco = d.getValue();
                boolean isCustom = MoonlightCompat.isCustomDecoration(d.getKey(), deco);
                boolean isBlockMarker = MoonlightCompat.isBlockMarkerDecoration(d.getKey(), deco);
                if ((deco.renderOnFrame() || isBlockMarker) && !isCustom) {
                    mapIcons.add(new DecorationHolder(deco, d.getKey(), holder));
                }
            }
            mapIcons.addAll(MoonlightCompat.getCustomDecorations(holder));
        }

        List<DecorationBookmarkButton> allWidgets = new ArrayList<>();
        for (var e : mapIcons) {
            allWidgets.add(DecorationBookmarkButton.of(
                    (width - BOOK_WIDTH) / 2 + 10, 0, e, this));
        }

        double px = player.getX(), pz = player.getZ();
        allWidgets.sort(Comparator.comparingDouble(w ->
                Mth.square(w.getWorldX() - px) + Mth.square(w.getWorldZ() - pz)));

        List<DecorationBookmarkButton> priorityWidgets = new ArrayList<>();
        List<DecorationBookmarkButton> normalWidgets = new ArrayList<>();

        for (String pid : priorityIds) {
            allWidgets.stream()
                    .filter(w -> w.getDecorationId().equals(pid))
                    .findFirst()
                    .ifPresent(priorityWidgets::add);
        }
        for (var w : allWidgets) {
            if (!priorityIds.contains(w.getDecorationId())) {
                normalWidgets.add(w);
            }
        }

        int normalCap = Math.max(0, MAX_DECORATION_BOOKMARKS - priorityWidgets.size());
        if (normalWidgets.size() > normalCap) {
            normalWidgets = new ArrayList<>(normalWidgets.subList(0, normalCap));
        }

        List<DecorationBookmarkButton> finalWidgets = new ArrayList<>();
        finalWidgets.addAll(priorityWidgets);
        finalWidgets.addAll(normalWidgets);

        int separation = Math.min(17, (int) ((BOOK_HEIGHT - 22f) / Math.max(finalWidgets.size(), 1)));
        int i = 0;
        for (var w : finalWidgets) {
            w.setY((height - BOOK_HEIGHT) / 2 + 15 + i * separation);
            w.setIndex(i);
            decorationBookmarks.add(w);
            i++;
        }

        finalWidgets.sort(Comparator.comparingInt(DecorationBookmarkButton::getBatchGroup));
        finalWidgets.forEach(this::addRenderableWidget);
    }

    public void updateVisibleDecoration(int currentXCenter, int currentZCenter, float radius, boolean followingPlayer) {
        if (decorationBookmarks.isEmpty()) return;
        float minX = currentXCenter - radius;
        float maxX = currentXCenter + radius;
        float minZ = currentZCenter - radius;
        float maxZ = currentZCenter + radius;

        List<Pair<Double, DecorationBookmarkButton>> byDistance = new ArrayList<>();
        for (var bookmark : decorationBookmarks) {
            double x = bookmark.getWorldX();
            double z = bookmark.getWorldZ();
            if (x >= minX && x <= maxX && z >= minZ && z <= maxZ) bookmark.setSelected(true);
            if (followingPlayer) {
                double distance = Mth.square(x - currentXCenter) + Mth.square(z - currentZCenter);
                byDistance.add(Pair.of(distance, bookmark));
            }
        }

        if (followingPlayer) {
            byDistance.sort(Comparator.comparingDouble(Pair::getFirst));

            List<DecorationBookmarkButton> priorityOrdered = new ArrayList<>();
            List<DecorationBookmarkButton> normalOrdered = new ArrayList<>();

            for (String pid : priorityIds) {
                byDistance.stream()
                        .map(Pair::getSecond)
                        .filter(w -> w.getDecorationId().equals(pid))
                        .findFirst()
                        .ifPresent(priorityOrdered::add);
            }
            for (var e : byDistance) {
                var w = e.getSecond();
                if (!priorityIds.contains(w.getDecorationId())) {
                    normalOrdered.add(w);
                }
            }

            List<DecorationBookmarkButton> ordered = new ArrayList<>();
            ordered.addAll(priorityOrdered);
            ordered.addAll(normalOrdered);

            int maxW = BOOK_HEIGHT - 24;
            int separation = Math.min(17, maxW / Math.max(ordered.size(), 1));
            for (int index = 0; index < ordered.size(); index++) {
                var w = ordered.get(index);
                w.setY((height - BOOK_HEIGHT) / 2 + 15 + index * separation);
                w.setIndex(index);
            }
        }
    }

    public void centerOnDecoration(DecorationBookmarkButton button) {
        int x = (int) button.getWorldX();
        int z = (int) button.getWorldZ();
        this.mapWidget.resetAndCenter(x, z, false, true, false);
    }

    public boolean decreaseSlice() {
        int current = selectedSlice.heightOrTop();
        MapType type = selectedSlice.type();
        ResourceKey<Level> dim = selectedSlice.dimension();
        Integer newHeight = currentMaps.getHeightTree(dim, type).floor(current - 1);
        return updateSlice(Slice.of(type, newHeight, dim));
    }

    public boolean increaseSlice() {
        int current = selectedSlice.heightOrTop();
        MapType type = selectedSlice.type();
        ResourceKey<Level> dim = selectedSlice.dimension();
        Integer newHeight = currentMaps.getHeightTree(dim, type).ceiling(current + 1);
        return updateSlice(Slice.of(type, newHeight, dim));
    }

    public void cycleSliceType() {
        ResourceKey<Level> dim = selectedSlice.dimension();
        var slices = new ArrayList<>(currentMaps.getAvailableTypes(dim));
        if (!slices.isEmpty()) {
            int index = slices.indexOf(selectedSlice.type());
            index = (index + 1) % slices.size();
            MapType type = slices.get(index);
            TreeSet<Integer> heightTree = currentMaps.getHeightTree(dim, type);
            Integer ceiling = heightTree.floor(selectedSlice.heightOrTop());
            if (ceiling == null) ceiling = heightTree.first();
            updateSlice(Slice.of(type, ceiling, dim));
        }
    }

    private boolean updateSlice(Slice newSlice) {
        boolean changed = false;
        if (!Objects.equals(selectedSlice, newSlice)) {
            selectedSlice = newSlice;
            sliceButton.setSlice(selectedSlice);
            MapAtlasesNetworking.CHANNEL.sendToServer(new C2SSelectSlicePacket(selectedSlice,
                    lectern == null ? null : lectern.getBlockPos()));
            MapAtlasItem.setSelectedSlice(atlas, selectedSlice);
            recalculateDecorationWidgets();
            changed = true;
        }
        var dim = selectedSlice.dimension();
        boolean manySlices = currentMaps.getHeightTree(dim, selectedSlice.type()).size() > 1;
        boolean manyTypes = currentMaps.getAvailableTypes(dim).size() != 1;
        sliceButton.refreshState(manySlices, manyTypes);
        sliceDown.setActive(manySlices);
        sliceUp.setActive(manySlices);
        mapWidget.resetZoom();
        return changed;
    }

    public boolean isEditingText() {
        return editBox.active;
    }

    public boolean isPlacingPin() {
        return this.cursorAction == CursorAction.PLACING_PIN;
    }

    public void clearCursorAction() {
        this.cursorAction = CursorAction.NONE;
    }

    public void toggleCursorAction(CursorAction targetAction) {
        if (this.cursorAction == targetAction) {
            this.cursorAction = CursorAction.NONE;
        } else {
            this.cursorAction = targetAction;
        }
    }

    public boolean isShearing() {
      return this.cursorAction == CursorAction.SHEARING;
    }

    public void shearMapAt(ColumnPos pos) {
        MapDataHolder selected = findMapContaining(pos.x(), pos.z());
        if (selected != null) {
            MapAtlasesNetworking.CHANNEL.sendToServer(new C2SRemoveMapPacket(selected.id));
            currentMaps.remove(selected);
            recalculateDecorationWidgets();
        }
        this.clearCursorAction();
    }

    public void placePinAt(ColumnPos pos) {
        MapDataHolder selected = findMapContaining(pos.x(), pos.z());
        if (selected != null) {
            editBox.setValue("");
            this.partialPin = Pair.of(selected, pos);
            if (isShiftDown() || isAltDown()) {
                focusEditBox(true);
            } else {
                addNewPin();
            }
        }
        this.clearCursorAction();
    }

    private void focusEditBox(boolean on) {
        editBox.active = on;
        editBox.visible = on;
        editBox.setCanLoseFocus(!on);
        editBox.setFocused(on);
        this.setFocused(on ? editBox : mapWidget);
        if (!on && isPinOnly) this.onClose();
    }

    private void addNewPin() {
        if (partialPin != null) {
            String text = editBox.getValue();
            PinButton.placePin(partialPin.getFirst(), partialPin.getSecond(), text, editBox.getIndex());
            editBox.increasePinIndex();
            focusEditBox(false);
            partialPin = null;

            this.recalculateDecorationWidgets();
        }
    }


    public boolean canTeleport() {
        return MapAtlasesConfig.creativeTeleport.get()
                && isShiftDown()
                && minecraft.gameMode.getPlayerMode().isCreative() &&
                cursorAction == CursorAction.NONE && !editBox.active;
    }

    public static boolean isShiftDown() {
        var window = Minecraft.getInstance().getWindow();
        return InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_SHIFT)
                || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    public static boolean isControlDown() {
        var window = Minecraft.getInstance().getWindow();
        return InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_CONTROL)
                || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_CONTROL);
    }

    public static boolean isAltDown() {
        var window = Minecraft.getInstance().getWindow();
        return InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_ALT)
                || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_ALT);
    }


    public static Vector4d scaleVector(double mouseX, double mouseZ, float scale, int w, int h) {
        Matrix4d matrix4d = new Matrix4d();

        double translateX = w / 2.0;
        double translateY = h / 2.0;
        double scaleFactor = scale - 1.0;

        matrix4d.translate(translateX, translateY, 0);

        matrix4d.scale(1.0 + scaleFactor);

        matrix4d.translate(-translateX, -translateY, 0);

        Vector4d v = new Vector4d(mouseX, mouseZ, 0, 1.0F);

        matrix4d.transform(v);
        return v;
    }

    public Minecraft getMinecraft() {
        return minecraft;
    }

    public void removeMapAt(double mouseX, double mouseY) {
        var v = transformMousePos(mouseX, mouseY);
        MapDataHolder map = currentMaps.select((int) v.x, (int) v.y, selectedSlice);
        if (map != null) {
            MapAtlasesNetworking.CHANNEL.sendToServer(new C2SRemoveMapPacket(map.id));
        }
    }
}