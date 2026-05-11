package net.nanaky.ultimate_map_atlases.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

@Mixin(value = MapItemSavedData.class, priority = 1100)
public class MapItemSavedDataMixin {
    @Shadow
    @Final
    private Map<Player, MapItemSavedData.HoldingPlayer> carriedByPlayers;

    @Inject(method = "checkBanners", at = @At("HEAD"), cancellable = true)
    public void mapAtlases$preventCheckingOffThread(BlockGetter world, int x, int z, CallbackInfo ci) {
        if (world instanceof ServerLevel l && !l.getServer().isSameThread()) {
            ci.cancel();
        }
    }

    @Inject(method = "getHoldingPlayer", at = @At("HEAD"), cancellable = true)
    public void mapAtlases$preventModifyingOffThread(Player player,
                                                     CallbackInfoReturnable<MapItemSavedData.HoldingPlayer> cir) {
        if (player.level() instanceof ServerLevel l && !l.getServer().isSameThread()) {
            var value = this.carriedByPlayers.get(player);
            cir.setReturnValue(value);
        }
    }
}