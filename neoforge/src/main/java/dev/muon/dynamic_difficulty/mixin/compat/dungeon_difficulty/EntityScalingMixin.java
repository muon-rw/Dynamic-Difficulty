package dev.muon.dynamic_difficulty.mixin.compat.dungeon_difficulty;

import dev.muon.dynamic_difficulty.compat.dungeon_difficulty.DungeonDifficultyCompat;
import net.dungeon_difficulty.logic.EntityScaling;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to capture Dungeon Difficulty scaling data when an entity is scaled.
 */
@Mixin(value = EntityScaling.class, remap = false)
public class EntityScalingMixin {

    @Inject(
            method = "scale",
            at = @At(value = "TAIL")
    )
    private static void onEntityScaled(Entity entity, ServerLevel world, CallbackInfo ci) {
        if (entity instanceof LivingEntity livingEntity) {
            DungeonDifficultyCompat.onEntityScaled(livingEntity, world);
        }
    }
}

