package dev.muon.dynamic_difficulty.mixin.compat.reskillable;

import dev.muon.dynamic_difficulty.api.PlayerLevelProvider;
import net.bandit.reskillable.common.network.payload.SyncToClient;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SyncToClient.class, remap = false)
public class SyncToClientMixin {

    @Inject(
        method = "send(Lnet/minecraft/server/level/ServerPlayer;)V",
        at = @At("RETURN"),
        remap = false
    )
    private static void onSync(ServerPlayer player, CallbackInfo ci) {
        PlayerLevelProvider.requestPlayerLevelUpdate(player);
    }
}

