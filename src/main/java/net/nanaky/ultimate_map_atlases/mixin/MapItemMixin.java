package net.nanaky.ultimate_map_atlases.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.nanaky.ultimate_map_atlases.MapAtlasesMod;

@Mixin(value = MapItem.class, priority = 1200)
public class MapItemMixin {

    @WrapOperation(method = "update", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getChunk(II)Lnet/minecraft/world/level/chunk/LevelChunk;"))
    public LevelChunk reduceUpdateNonGeneratedChunks(Level level, int chunkX, int chunkZ,
                                                     Operation<LevelChunk> original,
                                                     @Local(ordinal = 8) int distance,
                                                     @Local(ordinal = 5) int range,
                                                     @Local(ordinal = 0) int scale) {
        if (MapAtlasesMod.rangeCheck(distance, range, scale)) {
            if (level.getChunkSource().hasChunk(chunkX, chunkZ)) {
                return original.call(level, chunkX, chunkZ);
            }

        }
        return new EmptyLevelChunk(level, new ChunkPos(chunkX, chunkZ),
                level.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.FOREST));
    }

    @Inject(method = "update", at = @At(value = "NEW", target = "()Lnet/minecraft/core/BlockPos$MutableBlockPos;",
            ordinal = 0), require = 1)
    public void startFromZeroStep(Level level, Entity viewer, MapItemSavedData data, CallbackInfo ci,
                                  @Local MapItemSavedData.HoldingPlayer holdingPlayer, @Share("needsPostIncrement") LocalRef<MapItemSavedData.HoldingPlayer> needsPostInc) {
        holdingPlayer.step--;
        needsPostInc.set(holdingPlayer);
    }

    @Inject(method = "update", at = @At(value = "RETURN"))
    public void doPostIncrement(Level level, Entity viewer, MapItemSavedData data, CallbackInfo ci,
                                @Share("needsPostIncrement") LocalRef<MapItemSavedData.HoldingPlayer> needsPostInc) {
        MapItemSavedData.HoldingPlayer holdingPlayer = needsPostInc.get();
        if (holdingPlayer != null) {
            holdingPlayer.step++;
            needsPostInc.set(null);
        }
    }
}
