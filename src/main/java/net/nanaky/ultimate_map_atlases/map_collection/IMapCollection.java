package net.nanaky.ultimate_map_atlases.map_collection;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.jetbrains.annotations.Nullable;

import net.nanaky.ultimate_map_atlases.utils.MapDataHolder;
import net.nanaky.ultimate_map_atlases.utils.MapType;
import net.nanaky.ultimate_map_atlases.utils.Slice;

import java.util.Collection;
import java.util.List;
import java.util.TreeSet;
import java.util.function.Predicate;

public interface IMapCollection {

    static IMapCollection get(ItemStack stack, Level level) {
        return IMapCollectionImpl.get(stack, level);
    }

    boolean add(int mapId, Level level);

    boolean remove(MapDataHolder obj);

    int getCount();

    boolean isEmpty();

    byte getScale();

    int[] getAllIds();

    boolean hasId(int id);

    Collection<ResourceKey<Level>> getAvailableDimensions();

    Collection<MapType> getAvailableTypes(ResourceKey<Level> dimension);

    TreeSet<Integer> getHeightTree(ResourceKey<Level> dimension, MapType type);

    List<MapDataHolder> selectSection(Slice slice);

    List<MapDataHolder> filterSection(Slice slice, Predicate<MapItemSavedData> predicate);

    @Nullable
    default MapDataHolder select(int x, int z, Slice slice) {
        return select(new MapKey(x, z, slice));
    }

    @Nullable
    MapDataHolder select(MapKey key);


    @Nullable
    MapDataHolder getClosest(double x, double z, Slice slice);

    @Nullable
    default MapDataHolder getClosest(Player player, Slice slice) {
        return getClosest(player.getX(), player.getZ(), slice);
    }

    List<MapDataHolder> getAll();


    void addNotSynced(Level level);

    boolean hasOneSlice();
}
