package dev.muon.dynamic_difficulty.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.muon.dynamic_difficulty.api.LevelingAPI;
import dev.muon.dynamic_difficulty.client.LevelPlateRenderer;
import dev.muon.dynamic_difficulty.config.Config;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

// Replaces RenderNameTagEvent
@Mixin(EntityRenderer.class)
public class EntityRendererMixin {

    @ModifyExpressionValue(
        method = "render",
        at = @At(value = "INVOKE",
                target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;shouldShowName(Lnet/minecraft/world/entity/Entity;)Z")
    )
    private boolean modifyShouldShowName(boolean original, Entity entity) {
        if (!(entity instanceof LivingEntity living) || !LevelingAPI.shouldShowLevel(living)) {
            return original;
        }

        String entityId = BuiltInRegistries.ENTITY_TYPE.getKey(living.getType()).toString();
        if (Config.CLIENT.hiddenLevelEntities.get().contains(entityId)) {
            return false;
        }

        return LevelPlateRenderer.shouldShowName(living);
    }

    @ModifyVariable(
        method = "renderNameTag",
        at = @At("HEAD"),
        argsOnly = true,
        // `this` is index 0, Entity is index 1
        index = 2
    )
    private Component modifyDisplayName(Component displayName, Entity entity) {
        if (entity instanceof LivingEntity livingEntity) {
            return LevelPlateRenderer.modifyNameTag(displayName, livingEntity);
        }
        return displayName;
    }
}
