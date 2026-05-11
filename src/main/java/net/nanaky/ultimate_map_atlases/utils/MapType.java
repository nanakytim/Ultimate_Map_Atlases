package net.nanaky.ultimate_map_atlases.utils;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ColumnPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import com.mojang.datafixers.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.stream.Collectors;

public enum MapType {
    VANILLA("map_", Items.FILLED_MAP, Items.MAP);

    private static final Map<Item, MapType> FROM_ITEM = Arrays.stream(values())
            .collect(Collectors.toMap(t -> t.filled, c -> c, (existing, replacement) -> existing, IdentityHashMap::new));

    private final String keyPrefix;
    public final Item filled;
    public final Item empty;
    public final String translationKey;

    MapType(String keyPrefix, Item filled, Item empty) {
        this.keyPrefix = keyPrefix;
        this.filled = filled;
        this.empty = empty;
        this.translationKey = filled.getDescriptionId();
    }

    public static MapType fromKey(String mapString, MapItemSavedData data) {
        return VANILLA;
    }

    public String makeStringKey(int id) {
        return keyPrefix + id;
    }

    @Nullable
    public Integer findKey(String s) {
        if (s.startsWith(keyPrefix)) return Integer.parseInt(s.substring(keyPrefix.length()));
        return null;
    }

    public static boolean isEmptyMap(Item i) {
        return i == Items.MAP;
    }

    @Nullable
    public static MapType fromItem(Item item) {
        return FROM_ITEM.get(item);
    }

    @Nullable
    public Pair<String, MapItemSavedData> getMapData(Level level, int id) {
        String key = keyPrefix + id;
        MapItemSavedData data = level.getMapData(new MapId(id));
        return data == null ? null : Pair.of(key, data);
    }

    @Nullable
    public Integer getHeight(@NotNull MapItemSavedData data) {
        return null;
    }

    public ColumnPos getCenter(double px, double pz, int scale) {
        int j = Mth.floor((px + 64.0D) / scale);
        int k = Mth.floor((pz + 64.0D) / scale);
        int mapCenterX = j * scale + scale / 2 - 64;
        int mapCenterZ = k * scale + scale / 2 - 64;
        return new ColumnPos(mapCenterX, mapCenterZ);
    }

    public ItemStack createExistingMapItem(int id, @Nullable Integer height) {
        ItemStack map = new ItemStack(Items.FILLED_MAP);
        map.set(DataComponents.MAP_ID, new MapId(id));
        return map;
    }

    public ItemStack createNewMapItem(int destX, int destZ, byte scale, Level level, @Nullable Integer height, ItemStack atlas) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return ItemStack.EMPTY;
        }
        return MapItem.create(serverLevel, destX, destZ, scale, true, false);
    }

    public boolean hasMarkers() {
        return true;
    }

    public int getDiscoveryReach(@Nullable Integer height) {
        return 128;
    }

    public float getDefaultZoomFactor() {
        return 1;
    }

    public Component getName() {
        return Component.translatable(this.translationKey);
    }
}