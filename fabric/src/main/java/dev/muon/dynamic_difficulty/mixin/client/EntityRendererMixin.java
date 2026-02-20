package dev.muon.dynamic_difficulty.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.muon.dynamic_difficulty.api.LevelingAPI;
import dev.muon.dynamic_difficulty.client.LevelPlateHandler;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Replaces RenderNameTagEvent
@Mixin(EntityRenderer.class)
public class EntityRendererMixin {

    @ModifyExpressionValue(
            method = "extractRenderState",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;shouldShowName(Lnet/minecraft/world/entity/Entity;D)Z")
    )
    private boolean modifyShouldShowName(boolean original, Entity entity) {
        if (!(entity instanceof LivingEntity living) || !LevelingAPI.shouldShowLevel(living)) {
            return original;
        }
        if (!LevelPlateHandler.shouldOverrideNameplateVisibility(living)) {
            return original;
        }

        return LevelPlateHandler.shouldShowName(living);
    }

    @ModifyReturnValue(
            method = "getNameTag",
            at = @At("RETURN")
    )
    @Nullable
    private Component modifyDisplayName(@Nullable Component displayName, Entity entity) {
        if (displayName == null || !(entity instanceof LivingEntity livingEntity)) {
            return displayName;
        }
        if (LevelPlateHandler.shouldInjectLevel(livingEntity)) {
            return LevelPlateHandler.modifyNameTag(displayName, livingEntity);
        }
        return displayName;
    }

}