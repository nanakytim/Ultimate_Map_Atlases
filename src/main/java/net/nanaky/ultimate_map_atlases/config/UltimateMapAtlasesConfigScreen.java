package net.nanaky.ultimate_map_atlases.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.nanaky.ultimate_map_atlases.client.Anchoring;
import net.nanaky.ultimate_map_atlases.client.InHandMode;
import net.nanaky.ultimate_map_atlases.utils.ActivationLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class UltimateMapAtlasesConfigScreen {

    public static ConfigScreenFactory<?> create() {
        return parent -> new MapAtlasesConfigScreen(parent);
    }

    static class MapAtlasesConfigScreen extends Screen {

        private final Screen parent;

        private int currentTab = 0;
        private static final String[] TAB_NAMES = { "Minimap", "World Map", "Misc", "Moonlight", "Server" };

        private int scrollOffset = 0;
        private static final int ENTRY_HEIGHT    = 24;
        private static final int LIST_TOP        = 58;  // below tabs
        private static final int LIST_BOTTOM_PAD = 36;
        private static final int TAB_Y           = 18;
        private static final int TAB_H           = 20;

        private final List<ConfigEntry> entries = new ArrayList<>();

        MapAtlasesConfigScreen(Screen parent) {
            super(Component.literal("Ultimate Map Atlases Config"));
            this.parent = parent;
        }

        public void addEntry(AbstractWidget widget) {
            addRenderableWidget(widget);
        }

        @Override
        protected void init() {
            entries.clear();
            scrollOffset = 0;
            buildEntries();

            // ── Tabs ──────────────────────────────────────────────────────────
            int tabCount = TAB_NAMES.length;
            int tabW = Math.min(90, (width - 20) / tabCount);
            int tabsTotal = tabCount * tabW + (tabCount - 1) * 2;
            int tabStartX = (width - tabsTotal) / 2;

            for (int i = 0; i < tabCount; i++) {
                final int idx = i;
                int tx = tabStartX + i * (tabW + 2);
                addRenderableWidget(Button.builder(Component.literal(TAB_NAMES[i]), b -> {
                    currentTab = idx;
                    rebuildWidgets();
                }).bounds(tx, TAB_Y, tabW, TAB_H).build());
            }

            // ── Save / Cancel ─────────────────────────────────────────────────
            int btnY = height - LIST_BOTTOM_PAD + 8;
            addRenderableWidget(Button.builder(Component.literal("Save & Close"), b -> {
                saveAll();
                onClose();
            }).bounds(width / 2 - 104, btnY, 100, 20).build());

            addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
                    .bounds(width / 2 + 4, btnY, 100, 20).build());

            // ── Entry widgets ─────────────────────────────────────────────────
            int y = LIST_TOP + 4 - scrollOffset;
            for (ConfigEntry entry : entries) {
                entry.addWidget(this, width, y);
                y += ENTRY_HEIGHT;
            }
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {

            int listBottom = height - LIST_BOTTOM_PAD;

            // ── Background ────────────────────────────────────────────────────
            // Dark panel behind the entry list
            g.fill(0, LIST_TOP, width, listBottom, 0xA0000000);
            // Slightly lighter header band
            g.fill(0, 0, width, TAB_Y - 1, 0x60000000);
            // Separator lines
            g.fill(0, TAB_Y - 1,  width, TAB_Y,      0xFF555555);
            g.fill(0, LIST_TOP,   width, LIST_TOP + 1, 0xFF555555);
            g.fill(0, listBottom, width, listBottom + 1, 0xFF555555);

            // ── Title ─────────────────────────────────────────────────────────
            g.centeredText(font, "Ultimate Map Atlases Config", width / 2, 5, 0xFFFFFF);

            // ── Active tab underline ──────────────────────────────────────────
            int tabCount = TAB_NAMES.length;
            int tabW = Math.min(90, (width - 20) / tabCount);
            int tabsTotal = tabCount * tabW + (tabCount - 1) * 2;
            int tabStartX = (width - tabsTotal) / 2;
            int tx = tabStartX + currentTab * (tabW + 2);
            g.fill(tx, TAB_Y + TAB_H - 2, tx + tabW, TAB_Y + TAB_H, 0xFFFFFFFF);

            // ── Entry labels + alternating row shading ────────────────────────
            g.enableScissor(0, LIST_TOP + 1, width, listBottom);
            int y = LIST_TOP + 4 - scrollOffset;
            for (int i = 0; i < entries.size(); i++) {
                ConfigEntry entry = entries.get(i);
                int rowTop = y - 2;
                int rowBot = y + ENTRY_HEIGHT - 2;
                if (rowBot > LIST_TOP && rowTop < listBottom) {
                    // Alternating row tint
                    if (i % 2 == 0) g.fill(0, rowTop, width, rowBot, 0x18FFFFFF);
                    g.text(font, entry.label, 12, y + 5, 0xDDDDDD);
                }
                y += ENTRY_HEIGHT;
            }
            g.disableScissor();

            // ── Server tab warning ────────────────────────────────────────────
            if (currentTab == 4) {
                g.fill(0, listBottom - 14, width, listBottom, 0xAA774400);
                g.centeredText(font, "! Server settings require op permissions and a restart",
                        width / 2, listBottom - 11, 0xFFCC44);
            }

            super.extractRenderState(g, mx, my, pt);
        }

        @Override
        public boolean mouseScrolled(double mx, double my, double dx, double dy) {
            int listBottom = height - LIST_BOTTOM_PAD;
            int listHeight = listBottom - LIST_TOP;
            int totalH = entries.size() * ENTRY_HEIGHT;
            int maxScroll = Math.max(0, totalH - listHeight);
            scrollOffset = (int) Math.max(0, Math.min(maxScroll, scrollOffset - dy * 12));
            rebuildWidgets();
            return true;
        }

        @Override
        public void onClose() {
            Minecraft.getInstance().gui.setScreen(parent);
        }

        private void saveAll() {
            for (ConfigEntry e : entries) e.save();
            UltimateMapAtlasesClientConfigManager.save();
            UltimateMapAtlasesServerConfigManager.save();
        }

        // ── Entry builders ────────────────────────────────────────────────────

        private void buildEntries() {
            UltimateMapAtlasesClientConfig cfg = UltimateMapAtlasesClientConfigManager.INSTANCE;
            UltimateMapAtlasesServerConfig  srv = UltimateMapAtlasesServerConfigManager.INSTANCE;
            switch (currentTab) {
                case 0 -> buildMinimapEntries(cfg);
                case 1 -> buildWorldMapEntries(cfg);
                case 2 -> buildMiscEntries(cfg);
                case 3 -> buildMoonlightEntries(cfg);
                case 4 -> buildServerEntries(srv);
            }
        }

        private void buildMinimapEntries(UltimateMapAtlasesClientConfig cfg) {
            addBool("Enable Minimap HUD",            () -> cfg.drawMiniMapHUD,            v -> cfg.drawMiniMapHUD = v);
            addBool("Hide When Inventory Open",      () -> cfg.hideWhenInventoryOpen,     v -> cfg.hideWhenInventoryOpen = v);
            addBool("Hide When Atlas In Hand",       () -> cfg.hideWhenInHand,            v -> cfg.hideWhenInHand = v);
            addDouble("Global Scale",                () -> cfg.miniMapScale,              v -> cfg.miniMapScale = v,              0, 20);
            addDouble("Zoom Multiplier",             () -> cfg.miniMapZoomMultiplier,     v -> cfg.miniMapZoomMultiplier = v,     0.001, 100);
            addEnum("Anchoring", Anchoring.values(), () -> cfg.miniMapAnchoring,          v -> cfg.miniMapAnchoring = v);
            addInt("Horizontal Offset",              () -> cfg.miniMapHorizontalOffset,   v -> cfg.miniMapHorizontalOffset = v,   -4000, 4000);
            addInt("Vertical Offset",                () -> cfg.miniMapVerticalOffset,     v -> cfg.miniMapVerticalOffset = v,     -4000, 4000);
            addInt("Potion Effect V-Offset",         () -> cfg.activePotionVerticalOffset,v -> cfg.activePotionVerticalOffset = v,-4000, 4000);
            addBool("Follow Player",                 () -> cfg.miniMapFollowPlayer,       v -> cfg.miniMapFollowPlayer = v);
            addBool("Rotate With Player",            () -> cfg.miniMapRotate,             v -> cfg.miniMapRotate = v);
            addBool("Show Map Borders",              () -> cfg.miniMapBorder,             v -> cfg.miniMapBorder = v);
            addBool("Darken At Night",               () -> cfg.minimapSkyLight,           v -> cfg.minimapSkyLight = v);
            addBool("Show Background Per Map",       () -> cfg.showsMapBackground,        v -> cfg.showsMapBackground = v);
            addBool("Map Change Sound",              () -> cfg.mapChangeSound,            v -> cfg.mapChangeSound = v);
            addBool("Automatic Slice Change",        () -> cfg.automaticSlice,            v -> cfg.automaticSlice = v);
            addBool("Show Cardinals",                () -> cfg.drawMinimapCardinals,      v -> cfg.drawMinimapCardinals = v);
            addDouble("Cardinals Scale",             () -> cfg.miniMapCardinalsScale,     v -> cfg.miniMapCardinalsScale = v,     0, 2);
            addBool("Only Show North",               () -> cfg.miniMapOnlyNorth,          v -> cfg.miniMapOnlyNorth = v);
            addBool("Show Coordinates",              () -> cfg.drawMinimapCoords,         v -> cfg.drawMinimapCoords = v);
            addBool("Show Chunk Coordinates",        () -> cfg.drawMinimapChunkCoords,    v -> cfg.drawMinimapChunkCoords = v);
            addBool("Show Biome",                    () -> cfg.drawMinimapBiome,          v -> cfg.drawMinimapBiome = v);
            addDouble("Coords & Biome Scale",        () -> cfg.minimapCoordsAndBiomeScale,v -> cfg.minimapCoordsAndBiomeScale = v,0, 10);
            addBool("Only Y With Slices",            () -> cfg.yOnlyWithSlice,            v -> cfg.yOnlyWithSlice = v);
            addBool("Coords Position: Left",         () -> cfg.compassPositionIsLeft,     v -> cfg.compassPositionIsLeft = v);
            addBool("Coords Position: Center",       () -> cfg.compassPositionIsCenter,   v -> cfg.compassPositionIsCenter = v);
            addBool("Coords Position: Right",        () -> cfg.compassPositionIsRight,    v -> cfg.compassPositionIsRight = v);
            addInt("Coords V-Offset",                () -> cfg.compassHeightOffset,       v -> cfg.compassHeightOffset = v,       0, 3000);
            addBool("Coords Text Shadow",            () -> cfg.drawTextShadow,            v -> cfg.drawTextShadow = v);
            addDouble("Decoration Icon Scale",       () -> cfg.miniMapDecorationScale,    v -> cfg.miniMapDecorationScale = v,    0, 10);
            addDouble("Decoration Text Scale",       () -> cfg.miniMapDecorationTextScale,v -> cfg.miniMapDecorationTextScale = v,0, 10);
        }

        private void buildWorldMapEntries(UltimateMapAtlasesClientConfig cfg) {
            addDouble("World Map Scale",             () -> cfg.worldMapScale,             v -> cfg.worldMapScale = v,             0, 20);
            addDouble("Lectern Scale",               () -> cfg.lecternWorldMapScale,      v -> cfg.lecternWorldMapScale = v,      0, 20);
            addBool("Show Crosshair",                () -> cfg.worldMapCrossair,          v -> cfg.worldMapCrossair = v);
            addBool("Alternative Texture",           () -> cfg.worldMapBigTexture,        v -> cfg.worldMapBigTexture = v);
            addBool("Smooth Panning",                () -> cfg.worldMapSmoothPanning,     v -> cfg.worldMapSmoothPanning = v);
            addBool("Smooth Zooming",                () -> cfg.worldMapSmoothZooming,     v -> cfg.worldMapSmoothZooming = v);
            addDouble("Zoom Scroll Speed",           () -> cfg.worldMapZoomScrollSpeed,   v -> cfg.worldMapZoomScrollSpeed = v,   0, 10);
            addBool("Show Map Borders",              () -> cfg.worldMapBorder,            v -> cfg.worldMapBorder = v);
            addBool("Show Coordinates",              () -> cfg.drawWorldMapCoords,        v -> cfg.drawWorldMapCoords = v);
            addBool("Show Pin Coordinates",          () -> cfg.drawPinMapCoords,          v -> cfg.drawPinMapCoords = v);
            addDouble("Coordinates Scale",           () -> cfg.worldMapCoordsScale,       v -> cfg.worldMapCoordsScale = v,       0, 10);
            addDouble("Decoration Icon Scale",       () -> cfg.worldMapDecorationScale,   v -> cfg.worldMapDecorationScale = v,   0, 10);
            addDouble("Decoration Text Scale",       () -> cfg.worldMapDecorationTextScale,v->cfg.worldMapDecorationTextScale=v,  0, 10);
            addBool("Compact Slice Indicator",       () -> cfg.worldMapCompactSliceIndicator,v->cfg.worldMapCompactSliceIndicator=v);
            addBool("Follow Player",                 () -> cfg.worldMapFollowPlayer,      v -> cfg.worldMapFollowPlayer = v);
        }

        private void buildMiscEntries(UltimateMapAtlasesClientConfig cfg) {
            addDouble("Sound Scalar",                () -> cfg.soundScalar,               v -> cfg.soundScalar = v,               0, 10);
            addEnum("In-Hand Mode", InHandMode.values(), () -> cfg.inHandMode,            v -> cfg.inHandMode = v);
        }

        private void buildMoonlightEntries(UltimateMapAtlasesClientConfig cfg) {
            addBool("Enable Moonlight Compat",       () -> cfg.moonlightCompat,           v -> cfg.moonlightCompat = v);
            addBool("Pin Tracking",                  () -> cfg.moonlightPinTracking,      v -> cfg.moonlightPinTracking = v);
            addBool("Mob Radar",                     () -> cfg.entityRadar,               v -> cfg.entityRadar = v);
            addInt("Radar Radius",                   () -> cfg.radarRadius,               v -> cfg.radarRadius = v,               0, 256);
            addBool("Radar Pins Rotate",             () -> cfg.radarRotation,             v -> cfg.radarRotation = v);
            addBool("Radar Single Color",            () -> cfg.radarColor,                v -> cfg.radarColor = v);
            addBool("Night Lightmap",                () -> cfg.nightLightMap,             v -> cfg.nightLightMap = v);
            addBool("Convert Xaero Waypoints",       () -> cfg.convertXaero,              v -> cfg.convertXaero = v);
        }

        private void buildServerEntries(UltimateMapAtlasesServerConfig srv) {
            addInt("Max Map Count",                  () -> srv.maxMapCount,               v -> srv.maxMapCount = v,               0, 1000000);
            addBool("Accept Paper For Empty Maps",   () -> srv.acceptPaperForEmptyMaps,   v -> srv.acceptPaperForEmptyMaps = v);
            addBool("Require Empty Maps To Expand",  () -> srv.requireEmptyMapsToExpand,  v -> srv.requireEmptyMapsToExpand = v);
            addInt("Map Entry Value Multiplier",     () -> srv.mapEntryValueMultiplier,   v -> srv.mapEntryValueMultiplier = v,   0, 64);
            addInt("Pity Activation Map Count",      () -> srv.pityActivationMapCount,    v -> srv.pityActivationMapCount = v,    0, 64);
            addBool("Enable Empty Map Entry & Fill", () -> srv.enableEmptyMapEntryAndFill,v -> srv.enableEmptyMapEntryAndFill = v);
            addEnum("Activation Location", ActivationLocation.values(), () -> srv.activationLocation, v -> srv.activationLocation = v);
            addBool("Creative Teleport",             () -> srv.creativeTeleport,          v -> srv.creativeTeleport = v);
            addText("Pin Marker ID",                 () -> srv.pinMarkerId,               v -> srv.pinMarkerId = v);
            addBool("Light Map",                     () -> srv.lightMap,                  v -> srv.lightMap = v);
            addBool("Mob Radar (Server)",            () -> srv.entityRadar,               v -> srv.entityRadar = v);
            addBool("Shear Button",                  () -> srv.shearButton,               v -> srv.shearButton = v);
            addBool("Round Robin Update",            () -> srv.roundRobinUpdate,          v -> srv.roundRobinUpdate = v);
            addInt("Map Updates Per Tick",           () -> srv.mapUpdatePerTick,          v -> srv.mapUpdatePerTick = v,          0, 9);
            addEnum("Multithreaded Update", UltimateMapAtlasesServerConfig.UpdateType.values(), () -> srv.mapUpdateMultithreaded, v -> srv.mapUpdateMultithreaded = v);
            addBool("Debug Map Updates",             () -> srv.debugUpdate,               v -> srv.debugUpdate = v);
            addInt("Markers Update Period (ticks)",  () -> srv.markersUpdatePeriod,       v -> srv.markersUpdatePeriod = v,       1, 200);
        }

        private void addBool(String label, Supplier<Boolean> get, Consumer<Boolean> set) {
            entries.add(new BoolEntry(label, get, set));
        }
        private void addInt(String label, Supplier<Integer> get, Consumer<Integer> set, int min, int max) {
            entries.add(new IntEntry(label, get, set, min, max));
        }
        private void addDouble(String label, Supplier<Double> get, Consumer<Double> set, double min, double max) {
            entries.add(new DoubleEntry(label, get, set, min, max));
        }
        private void addText(String label, Supplier<String> get, Consumer<String> set) {
            entries.add(new TextEntry(label, get, set));
        }
        private <E extends Enum<E>> void addEnum(String label, E[] values, Supplier<E> get, Consumer<E> set) {
            entries.add(new EnumEntry<>(label, values, get, set));
        }
    }

    // ── Entry types ───────────────────────────────────────────────────────────

    interface ConfigEntry {
        String label = "";
        void addWidget(MapAtlasesConfigScreen screen, int screenWidth, int y);
        void save();
    }

    static class BoolEntry implements ConfigEntry {
        final String label;
        private final Supplier<Boolean> get;
        private final Consumer<Boolean> set;
        private boolean value;

        BoolEntry(String label, Supplier<Boolean> get, Consumer<Boolean> set) {
            this.label = label; this.get = get; this.set = set;
            this.value = get.get();
        }

        @Override
        public void addWidget(MapAtlasesConfigScreen screen, int screenWidth, int y) {
            int listBottom = screen.height - MapAtlasesConfigScreen.LIST_BOTTOM_PAD;
            if (y + 20 < MapAtlasesConfigScreen.LIST_TOP || y > listBottom) return;
            Button btn = Button.builder(Component.literal(value ? "ON" : "OFF"), b -> {
                value = !value;
                b.setMessage(Component.literal(value ? "ON" : "OFF"));
            }).bounds(screenWidth - 72, y, 60, 18).build();
            screen.addEntry(btn);
        }

        @Override
        public void save() { set.accept(value); }
    }

    static class IntEntry implements ConfigEntry {
        final String label;
        private final Supplier<Integer> get;
        private final Consumer<Integer> set;
        private final int min, max;
        private EditBox box;

        IntEntry(String label, Supplier<Integer> get, Consumer<Integer> set, int min, int max) {
            this.label = label; this.get = get; this.set = set; this.min = min; this.max = max;
        }

        @Override
        public void addWidget(MapAtlasesConfigScreen screen, int screenWidth, int y) {
            int listBottom = screen.height - MapAtlasesConfigScreen.LIST_BOTTOM_PAD;
            if (y + 16 < MapAtlasesConfigScreen.LIST_TOP || y > listBottom) return;
            box = new EditBox(Minecraft.getInstance().font, screenWidth - 122, y + 1, 110, 16, Component.literal(label));
            box.setValue(String.valueOf(get.get()));
            box.setMaxLength(10);
            screen.addEntry(box);
        }

        @Override
        public void save() {
            if (box == null) return;
            try { set.accept(Math.max(min, Math.min(max, Integer.parseInt(box.getValue().trim())))); }
            catch (NumberFormatException ignored) {}
        }
    }

    static class DoubleEntry implements ConfigEntry {
        final String label;
        private final Supplier<Double> get;
        private final Consumer<Double> set;
        private final double min, max;
        private EditBox box;

        DoubleEntry(String label, Supplier<Double> get, Consumer<Double> set, double min, double max) {
            this.label = label; this.get = get; this.set = set; this.min = min; this.max = max;
        }

        @Override
        public void addWidget(MapAtlasesConfigScreen screen, int screenWidth, int y) {
            int listBottom = screen.height - MapAtlasesConfigScreen.LIST_BOTTOM_PAD;
            if (y + 16 < MapAtlasesConfigScreen.LIST_TOP || y > listBottom) return;
            box = new EditBox(Minecraft.getInstance().font, screenWidth - 122, y + 1, 110, 16, Component.literal(label));
            box.setValue(String.valueOf(get.get()));
            box.setMaxLength(16);
            screen.addEntry(box);
        }

        @Override
        public void save() {
            if (box == null) return;
            try { set.accept(Math.max(min, Math.min(max, Double.parseDouble(box.getValue().trim())))); }
            catch (NumberFormatException ignored) {}
        }
    }

    static class TextEntry implements ConfigEntry {
        final String label;
        private final Supplier<String> get;
        private final Consumer<String> set;
        private EditBox box;

        TextEntry(String label, Supplier<String> get, Consumer<String> set) {
            this.label = label; this.get = get; this.set = set;
        }

        @Override
        public void addWidget(MapAtlasesConfigScreen screen, int screenWidth, int y) {
            int listBottom = screen.height - MapAtlasesConfigScreen.LIST_BOTTOM_PAD;
            if (y + 16 < MapAtlasesConfigScreen.LIST_TOP || y > listBottom) return;
            box = new EditBox(Minecraft.getInstance().font, screenWidth - 222, y + 1, 210, 16, Component.literal(label));
            box.setValue(get.get());
            box.setMaxLength(256);
            screen.addEntry(box);
        }

        @Override
        public void save() { if (box != null) set.accept(box.getValue()); }
    }

    static class EnumEntry<E extends Enum<E>> implements ConfigEntry {
        final String label;
        private final E[] values;
        private final Supplier<E> get;
        private final Consumer<E> set;
        private int index;

        EnumEntry(String label, E[] values, Supplier<E> get, Consumer<E> set) {
            this.label = label; this.values = values; this.get = get; this.set = set;
            E current = get.get();
            for (int i = 0; i < values.length; i++) if (values[i] == current) { index = i; break; }
        }

        @Override
        public void addWidget(MapAtlasesConfigScreen screen, int screenWidth, int y) {
            int listBottom = screen.height - MapAtlasesConfigScreen.LIST_BOTTOM_PAD;
            if (y + 20 < MapAtlasesConfigScreen.LIST_TOP || y > listBottom) return;
            Button btn = Button.builder(Component.literal("< " + values[index].name() + " >"), b -> {
                index = (index + 1) % values.length;
                b.setMessage(Component.literal("< " + values[index].name() + " >"));
            }).bounds(screenWidth - 162, y, 150, 18).build();
            screen.addEntry(btn);
        }

        @Override
        public void save() { set.accept(values[index]); }
    }
}