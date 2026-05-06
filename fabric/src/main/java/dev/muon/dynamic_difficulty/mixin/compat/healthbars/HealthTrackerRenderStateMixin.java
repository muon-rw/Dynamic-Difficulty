package dev.muon.dynamic_difficulty.mixin.compat.healthbars;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.muon.dynamic_difficulty.api.LevelingAPI;
import dev.muon.dynamic_difficulty.client.LevelPlateHandler;
import fuzs.healthbars.common.client.renderer.entity.state.HealthTrackerRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Health Bars composes its bar text from {@code livingEntity.getDisplayName()}
 * during render-state extraction; the resulting {@code displayName} field is
 * read by every downstream draw call (centered title, inline name, width
 * measurements). Wrapping the {@code getDisplayName} invoke once here covers
 * the full bar UI without touching Fuzs' visibility logic. The bar still
 * shows or hides under Health Bars' own rules.
 */
@Mixin(value = HealthTrackerRenderState.class, remap = false)
public class HealthTrackerRenderStateMixin {

    @ModifyExpressionValue(
            method = "extractRenderState",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;getDisplayName()Lnet/minecraft/network/chat/Component;",
                    remap = true
            )
    )
    private static Component dynamic_difficulty$appendLevel(
            Component original,
            @Local(argsOnly = true) LivingEntity livingEntity) {
        if (!LevelingAPI.shouldShowLevel(livingEntity)) {
            return original;
        }
        return LevelPlateHandler.modifyNameTag(original, livingEntity);
    }
}
