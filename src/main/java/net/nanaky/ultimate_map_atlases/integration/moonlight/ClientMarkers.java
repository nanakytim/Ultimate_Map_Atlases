package net.nanaky.ultimate_map_atlases.integration.moonlight;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ColumnPos;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.nanaky.moonlight.api.platform.PlatHelper;
import org.jetbrains.annotations.ApiStatus;
import net.nanaky.ultimate_map_atlases.client.MapAtlasesClient;
import net.nanaky.ultimate_map_atlases.config.MapAtlasesConfig;
import net.nanaky.ultimate_map_atlases.mixin.MapItemSavedDataAccessor;
import net.nanaky.ultimate_map_atlases.networking.C2SToggleBlockMarkerPacket;
import net.nanaky.ultimate_map_atlases.utils.MapAtlasesAccessUtils;
import net.nanaky.ultimate_map_atlases.utils.MapDataHolder;
import net.nanaky.ultimate_map_atlases.MapAtlasesMod;

import org.jetbrains.annotations.Nullable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ClientMarkers {
    private static final List<String> PIN_TEXTURES = List.of(
            "pin_red",
            "pin_blue",
            "pin_green",
            "pin_yellow",
            "pin_orange",
            "pin_apple",
            "pin_aqua",
            "pin_purple"
    );
    private static final Set<String> FOCUSED_PINS = new HashSet<>();
    private static final Map<Integer, Map<String, PinData>> PINS_BY_MAP = new HashMap<>();
    private static final Map<Integer, Map<String, BlockMarkerData>> BLOCK_MARKERS_BY_MAP = new HashMap<>();
    private static String lastFolderNameOrIP = null;
    private static SaveTarget lastType = SaveTarget.SINGLEPLAYER;
    private static Path currentPath = null;
    private static Path currentBlockMarkersPath = null;
    private static volatile boolean blockMarkersDirty = false;

    @ApiStatus.Internal
    public static void setWorldFolder(String pId, String serializedType) {
        lastFolderNameOrIP = pId;
        lastType = SaveTarget.fromSerializedName(serializedType);
    }

    @ApiStatus.Internal
    public static void deleteAllMarkersData(String folderName) {
        try {
            Files.deleteIfExists(getFilePath(folderName, SaveTarget.SINGLEPLAYER));
            Files.deleteIfExists(getBlockMarkersFilePath(folderName, SaveTarget.SINGLEPLAYER));
        } catch (Exception ignored) {
        }
    }

    @ApiStatus.Internal
    public static synchronized void loadClientMarkers(long seed, String levelName) {
        PINS_BY_MAP.clear();
        BLOCK_MARKERS_BY_MAP.clear();
        String id = lastFolderNameOrIP == null ? levelName : lastFolderNameOrIP;
        currentPath = getFilePath(id, lastType);
        currentBlockMarkersPath = getBlockMarkersFilePath(id, lastType);

        if (Files.exists(currentPath)) {
            try {
                for (String line : Files.readAllLines(currentPath, StandardCharsets.UTF_8)) {
                    PinData pin = PinData.deserialize(line);
                    if (pin != null) {
                        PINS_BY_MAP.computeIfAbsent(pin.mapId(), ignored -> new HashMap<>()).put(pin.id(), pin);
                    }
                }
            } catch (Exception ignored) {
                PINS_BY_MAP.clear();
            }
        }

        if (Files.exists(currentBlockMarkersPath)) {
            try {
                for (String line : Files.readAllLines(currentBlockMarkersPath, StandardCharsets.UTF_8)) {
                    BlockMarkerData marker = BlockMarkerData.deserialize(line);
                    if (marker != null) {
                        BLOCK_MARKERS_BY_MAP.computeIfAbsent(marker.mapId(), ignored -> new HashMap<>())
                                .put(marker.key(), marker);
                    }
                }
            } catch (Exception ignored) {
                BLOCK_MARKERS_BY_MAP.clear();
            }
        }

        lastFolderNameOrIP = null;
        lastType = SaveTarget.SINGLEPLAYER;
    }

    public static void saveClientMarkers() {
        Path path = currentPath;
        if (path == null) return;
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, PINS_BY_MAP.values().stream()
                    .flatMap(pins -> pins.values().stream())
                    .map(PinData::serialize)
                    .toList(), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }

    public static void saveBlockMarkers() {
        Path path = currentBlockMarkersPath;
        if (path == null) return;
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, BLOCK_MARKERS_BY_MAP.values().stream()
                    .flatMap(m -> m.values().stream())
                    .map(BlockMarkerData::serialize)
                    .toList(), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }

    public static synchronized void unloadWorld() {
        saveClientMarkers();
        saveBlockMarkers();
        FOCUSED_PINS.clear();
        PINS_BY_MAP.clear();
        BLOCK_MARKERS_BY_MAP.clear();
        currentPath = null;
        currentBlockMarkersPath = null;
    }

    public static Set<?> send(Integer integer, MapItemSavedData data) {
        return Set.of();
    }

    public static boolean consumeBlockMarkersDirty() {
        boolean v = blockMarkersDirty;
        blockMarkersDirty = false;
        return v;
    }


    public static synchronized void addPin(MapDataHolder holder, ColumnPos pos, String text, int index) {
        String markerId = MapAtlasesConfig.pinMarkerId.get();
        if (markerId.isEmpty()) return;
        BlockPos blockPos = new BlockPos(pos.x(), 0, pos.z());
        Component name = text.isEmpty() ? null : Component.literal(text);
        String pinId = MoonlightCompat.getPinId(blockPos, name, index);
        PINS_BY_MAP.computeIfAbsent(holder.id, ignored -> new HashMap<>())
                .put(pinId, new PinData(holder.stringId, holder.id, blockPos, markerId, text, index, pinId));
        MoonlightCompat.addDecoration(holder.data, blockPos, Identifier.parse(markerId), name, index, pinId);
        MapAtlasesClient.markDecorationsDirty(holder.data);
        saveClientMarkers();
    }

    public static synchronized void addMissingPinsToMap(MapDataHolder holder, Set<String> existingIds) {
        Map<String, PinData> pins = PINS_BY_MAP.get(holder.id);
        if (pins != null) {
            for (PinData pin : pins.values()) {
                if (!existingIds.contains(pin.id())) {
                    MoonlightCompat.addDecoration(holder.data, pin.pos(), Identifier.parse(pin.markerId()),
                            pin.text().isEmpty() ? null : Component.literal(pin.text()), pin.index(), pin.id());
                }
            }
        }
        addMissingBlockMarkersToMap(holder, existingIds);
    }

    public static synchronized boolean removeClientDeco(int mapId, String key) {
        Map<String, PinData> pins = PINS_BY_MAP.get(mapId);
        if (pins != null) {
            pins.remove(key);
            if (pins.isEmpty()) PINS_BY_MAP.remove(mapId);
            saveClientMarkers();
        }
        removeBlockMarker(mapId, key);
        return FOCUSED_PINS.remove(toFocusKey(mapId, key));
    }

    public static void focusClientDeco(MapDataHolder map, Object deco, boolean focused) {
        if (deco instanceof InternalPinDecoration pin) {
            String key = toFocusKey(map.id, pin.id());
            if (focused) FOCUSED_PINS.add(key);
            else FOCUSED_PINS.remove(key);
        }
    }

    public static boolean isClientDecoFocused(MapDataHolder map, Object deco) {
        return deco instanceof InternalPinDecoration pin && FOCUSED_PINS.contains(toFocusKey(map.id, pin.id()));
    }


    public static synchronized void addBlockMarker(int mapIntId, String mapStringId,
                                                    BlockPos headPos, String key,
                                                    String decorationTypeKey, @Nullable Component customName) {
        BlockMarkerData marker = new BlockMarkerData(mapStringId, mapIntId, headPos, key, decorationTypeKey, customName);
        BLOCK_MARKERS_BY_MAP.computeIfAbsent(mapIntId, ignored -> new HashMap<>()).put(key, marker);
        saveBlockMarkers();
    }

    public static synchronized void removeBlockMarker(int mapId, String key) {
        Map<String, BlockMarkerData> markers = BLOCK_MARKERS_BY_MAP.get(mapId);
        if (markers != null) {
            markers.remove(key);
            if (markers.isEmpty()) BLOCK_MARKERS_BY_MAP.remove(mapId);
            saveBlockMarkers();
            blockMarkersDirty = true;
        }
    }

    public static synchronized Component getBlockMarkerName(int mapId, String decorationKey) {
        Map<String, BlockMarkerData> markers = BLOCK_MARKERS_BY_MAP.get(mapId);
        if (markers == null) return null;
        BlockMarkerData marker = markers.get(decorationKey);
        if (marker == null) return null;
        return marker.customName();
    }

    private static String blockMarkerTranslationKey(String decorationTypeKey) {
        if (decorationTypeKey.startsWith("bed_")) {
            return "block.minecraft." + decorationTypeKey.substring(4) + "_bed";
        }
        return "block.minecraft." + decorationTypeKey;
    }

    public static synchronized boolean hasBlockMarker(int mapId, String key) {
        Map<String, BlockMarkerData> markers = BLOCK_MARKERS_BY_MAP.get(mapId);
        return markers != null && markers.containsKey(key);
    }

    private static synchronized void addMissingBlockMarkersToMap(MapDataHolder holder, Set<String> existingIds) {
        Map<String, BlockMarkerData> markers = BLOCK_MARKERS_BY_MAP.get(holder.id);
        if (markers == null || markers.isEmpty()) return;
        if (!(holder.data instanceof MapItemSavedDataAccessor accessor)) return;

        ClientLevel level = Minecraft.getInstance().level;

        List<String> toRemove = new ArrayList<>();
        for (BlockMarkerData marker : markers.values()) {
            if (existingIds.contains(marker.key())) continue;

            if (level != null && level.isLoaded(marker.pos())) {
                BlockPos actualPos = C2SToggleBlockMarkerPacket.blockPosFromKey(marker.key());
                BlockState state = level.getBlockState(actualPos);
                boolean valid = (state.getBlock() instanceof BedBlock
                                && state.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD)
                            || state.getBlock() instanceof CampfireBlock;
                if (!valid) {
                    toRemove.add(marker.key());
                    continue;
                }
            }
            var decorationType = BlockMarkerData.resolveDecorationType(marker.decorationTypeKey());
            if (decorationType != null) {
                accessor.invokeAddDecoration(decorationType, null, marker.key(),
                        marker.pos().getX() + 0.5, marker.pos().getZ() + 0.5, 180.0, marker.customName());
            }
        }

        if (!toRemove.isEmpty()) {
            toRemove.forEach(markers::remove);
            if (markers.isEmpty()) BLOCK_MARKERS_BY_MAP.remove(holder.id);
            saveBlockMarkers();
            blockMarkersDirty = true;
        }

        MapAtlasesClient.markDecorationsDirty(holder.data);
}


    public static Identifier getPinTexture(int index, boolean small) {
        String texture = getPinTextureName(index);
        if (small) texture += "_small";
        return Identifier.fromNamespaceAndPath("map_atlases", "textures/map_marker/" + texture + ".png");
    }

    public static String getPinTextureName(int index) {
        return PIN_TEXTURES.get(normalizePinIndex(index));
    }

    public static int normalizePinIndex(int index) {
        return Math.floorMod(index, PIN_TEXTURES.size());
    }

    private static String toFocusKey(int mapId, String pinId) {
        return mapId + ":" + pinId;
    }

    private static Path getFilePath(String id, SaveTarget type) {
        String fileName = (type == SaveTarget.SINGLEPLAYER ? id : sanitiseServerName(id));
        return PlatHelper.getGamePath().resolve("map_atlases/" + type.getSerializedName() + "/" + fileName + ".pins");
    }

    private static Path getBlockMarkersFilePath(String id, SaveTarget type) {
        String fileName = (type == SaveTarget.SINGLEPLAYER ? id : sanitiseServerName(id));
        return PlatHelper.getGamePath().resolve("map_atlases/" + type.getSerializedName() + "/" + fileName + ".blockmarkers");
    }

    private static String sanitiseServerName(String input) {
        return input.toLowerCase()
                .replaceAll("\\]:\\d+$", "")
                .replaceAll("[\\[\\]]", "")
                .replaceAll("[^a-z0-9 ]", "_");
    }


    private enum SaveTarget {
        SINGLEPLAYER("singleplayer"),
        MULTIPLAYER("multiplayer"),
        REALMS("realms");

        private final String serializedName;

        SaveTarget(String name) { this.serializedName = name; }

        public String getSerializedName() { return serializedName; }

        public static SaveTarget fromSerializedName(String name) {
            for (SaveTarget t : values()) if (t.serializedName.equals(name)) return t;
            return SINGLEPLAYER;
        }
    }


    private record PinData(String mapStringId, int mapId, BlockPos pos, String markerId, String text, int index, String id) {
        private String serialize() {
            return mapStringId + "\t" + pos.getX() + "\t" + pos.getZ() + "\t" + index + "\t" + markerId + "\t" +
                    Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8)) + "\t" + id;
        }

        private static PinData deserialize(String line) {
            String[] parts = line.split("\t", -1);
            if (parts.length != 7) return null;
            try {
                String mapStringId = parts[0];
                int mapId = mapStringId.contains("_")
                        ? MapAtlasesAccessUtils.findMapIntFromString(mapStringId)
                        : Integer.parseInt(mapStringId);
                if (!mapStringId.contains("_")) mapStringId = "map_" + mapStringId;
                int x = Integer.parseInt(parts[1]);
                int z = Integer.parseInt(parts[2]);
                int index = Integer.parseInt(parts[3]);
                String text = new String(Base64.getDecoder().decode(parts[5]), StandardCharsets.UTF_8);
                return new PinData(mapStringId, mapId, new BlockPos(x, 0, z), parts[4], text, index, parts[6]);
            } catch (Exception ignored) { return null; }
        }
    }

    public record BlockMarkerData(String mapStringId, int mapId, BlockPos pos, String key, String decorationTypeKey, @Nullable Component customName) {
        private String serialize() {
            String nameStr = customName != null ? Base64.getEncoder().encodeToString(
                    customName.getString().getBytes(StandardCharsets.UTF_8)) : "";
            return mapStringId + "\t" + pos.getX() + "\t" + pos.getZ() + "\t" + key + "\t" + decorationTypeKey + "\t" + nameStr;
        }

        private static BlockMarkerData deserialize(String line) {
            String[] parts = line.split("\t", -1);
            if (parts.length < 5) return null;
            try {
                String mapStringId = parts[0];
                int mapId = mapStringId.contains("_")
                        ? MapAtlasesAccessUtils.findMapIntFromString(mapStringId)
                        : Integer.parseInt(mapStringId);
                if (!mapStringId.contains("_")) mapStringId = "map_" + mapStringId;
                int x = Integer.parseInt(parts[1]);
                int z = Integer.parseInt(parts[2]);
                Component customName = null;
                if (parts.length >= 6 && !parts[5].isEmpty()) {
                    String text = new String(Base64.getDecoder().decode(parts[5]), StandardCharsets.UTF_8);
                    customName = Component.literal(text);
                }
                return new BlockMarkerData(mapStringId, mapId, new BlockPos(x, 0, z), parts[3], parts[4], customName);
            } catch (Exception ignored) { return null; }
        }

        public static net.minecraft.core.Holder<net.minecraft.world.level.saveddata.maps.MapDecorationType> resolveDecorationType(String key) {
            return switch (key) {
                case "campfire"       -> MapAtlasesMod.CAMPFIRE_DECORATION;
                case "soul_campfire"  -> MapAtlasesMod.SOUL_CAMPFIRE_DECORATION;
                case "bed_white"      -> MapAtlasesMod.BED_WHITE_DECORATION;
                case "bed_orange"     -> MapAtlasesMod.BED_ORANGE_DECORATION;
                case "bed_magenta"    -> MapAtlasesMod.BED_MAGENTA_DECORATION;
                case "bed_light_blue" -> MapAtlasesMod.BED_LIGHT_BLUE_DECORATION;
                case "bed_yellow"     -> MapAtlasesMod.BED_YELLOW_DECORATION;
                case "bed_lime"       -> MapAtlasesMod.BED_LIME_DECORATION;
                case "bed_pink"       -> MapAtlasesMod.BED_PINK_DECORATION;
                case "bed_gray"       -> MapAtlasesMod.BED_GRAY_DECORATION;
                case "bed_light_gray" -> MapAtlasesMod.BED_LIGHT_GRAY_DECORATION;
                case "bed_cyan"       -> MapAtlasesMod.BED_CYAN_DECORATION;
                case "bed_purple"     -> MapAtlasesMod.BED_PURPLE_DECORATION;
                case "bed_blue"       -> MapAtlasesMod.BED_BLUE_DECORATION;
                case "bed_brown"      -> MapAtlasesMod.BED_BROWN_DECORATION;
                case "bed_green"      -> MapAtlasesMod.BED_GREEN_DECORATION;
                case "bed_red"        -> MapAtlasesMod.BED_RED_DECORATION;
                case "bed_black"      -> MapAtlasesMod.BED_BLACK_DECORATION;
                default -> null;
            };
        }
    }
}