package dev.muon.dynamic_difficulty.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.muon.dynamic_difficulty.api.LevelingAPI;
import dev.muon.dynamic_difficulty.attribute.ModAttributesFabric;
import dev.muon.dynamic_difficulty.config.Configs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
public class LivingEntityMixinFabric {

    @ModifyReturnValue(method = "createLivingAttributes()Lnet/minecraft/world/entity/ai/attributes/AttributeSupplier$Builder;", at = @At("RETURN"))
    private static AttributeSupplier.Builder addAttributes(AttributeSupplier.Builder original) {
        original.add(ModAttributesFabric.PROJECTILE_DAMAGE_BONUS);
        original.add(ModAttributesFabric.PROJECTILE_DAMAGE_MULTIPLIER);
        original.add(ModAttributesFabric.EXPLOSION_DAMAGE_BONUS);
        original.add(ModAttributesFabric.EXPLOSION_DAMAGE_MULTIPLIER);
        original.add(ModAttributesFabric.DAMAGE_BONUS);
        original.add(ModAttributesFabric.DAMAGE_MULTIPLIER);
        original.add(ModAttributesFabric.MAGIC_DAMAGE_BONUS);
        original.add(ModAttributesFabric.MAGIC_DAMAGE_MULTIPLIER);
        return original;
    }

    @ModifyReturnValue(method = "getExperienceReward", at = @At("RETURN"))
    private int modifyExperienceReward(int original, ServerLevel level, @Nullable Entity killer) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!LevelingAPI.hasLevel(self)) {
            return original;
        }
        int levelValue = LevelingAPI.getLevel(self) + 1;
        double expBonus = Configs.SYNC.expBonus.get() * levelValue;
        return (int) (original + original * expBonus);
    }
}
