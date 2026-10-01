package dev.muon.dynamic_difficulty.mixin.compat.healthbars;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.muon.dynamic_difficulty.client.LevelPlateHandler;
import fuzs.healthbars.client.helper.HealthTracker;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = HealthTracker.EntityDataCache.class, remap = false)
public class EntityDataCacheMixin {

    @ModifyExpressionValue(
            method = "of",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;getDisplayName()Lnet/minecraft/network/chat/Component;",
                    remap = true
            )
    )
    private static Component appendLevelToDisplayName(Component original, @Local(argsOnly = true) LivingEntity livingEntity) {
        return LevelPlateHandler.modifyHealthBarsName(original, livingEntity);
    }
}
