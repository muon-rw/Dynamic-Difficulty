package dev.muon.dynamic_difficulty.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.muon.dynamic_difficulty.api.LevelingAPI;
import dev.muon.dynamic_difficulty.attribute.ModAttributes;
import dev.muon.dynamic_difficulty.config.Config;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.projectile.Projectile;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {


    @Unique
    private static final TagKey<DamageType> MAGIC_DAMAGE = TagKey.create(
            Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath("c", "is_magic")
    );

    @ModifyVariable(
            method = "hurt",
            at = @At("HEAD"),
            argsOnly = true,
            index = 2
    )
    private float modifyDamageAmount(float damageAmount, DamageSource damageSource) {
        if (damageAmount <= 0) {
            return damageAmount;
        }

        if (damageSource.getEntity() instanceof LivingEntity attacker) {
            damageAmount += (float) attacker.getAttributeValue(ModAttributes.DAMAGE_BONUS);
            damageAmount *= (float) (1 + attacker.getAttributeValue(ModAttributes.DAMAGE_MULTIPLIER));

            if (damageSource.getDirectEntity() instanceof Projectile || damageSource.is(DamageTypeTags.IS_PROJECTILE)) {
                damageAmount += (float) attacker.getAttributeValue(ModAttributes.PROJECTILE_DAMAGE_BONUS);
                damageAmount *= (float) (1 + attacker.getAttributeValue(ModAttributes.PROJECTILE_DAMAGE_MULTIPLIER));
            }
            if (damageSource.is(DamageTypes.EXPLOSION)) {
                damageAmount += (float) attacker.getAttributeValue(ModAttributes.EXPLOSION_DAMAGE_BONUS);
                damageAmount *= (float) (1 + attacker.getAttributeValue(ModAttributes.EXPLOSION_DAMAGE_MULTIPLIER));
            }
            if (damageSource.is(MAGIC_DAMAGE)) {
                damageAmount += (float) attacker.getAttributeValue(ModAttributes.MAGIC_DAMAGE_BONUS);
                damageAmount *= (float) (1 + attacker.getAttributeValue(ModAttributes.MAGIC_DAMAGE_MULTIPLIER));
            }
        }

        return damageAmount;
    }

    @ModifyReturnValue(method = "createLivingAttributes()Lnet/minecraft/world/entity/ai/attributes/AttributeSupplier$Builder;", at = @At("RETURN"))
    private static AttributeSupplier.Builder addAttributes(AttributeSupplier.Builder original) {
        original.add(ModAttributes.PROJECTILE_DAMAGE_BONUS);
        original.add(ModAttributes.PROJECTILE_DAMAGE_MULTIPLIER);
        original.add(ModAttributes.EXPLOSION_DAMAGE_BONUS);
        original.add(ModAttributes.EXPLOSION_DAMAGE_MULTIPLIER);
        original.add(ModAttributes.DAMAGE_BONUS);
        original.add(ModAttributes.DAMAGE_MULTIPLIER);
        original.add(ModAttributes.MAGIC_DAMAGE_BONUS);
        original.add(ModAttributes.MAGIC_DAMAGE_MULTIPLIER);
        return original;
    }

    @ModifyReturnValue(method = "getExperienceReward", at = @At("RETURN"))
    private int modifyExperienceReward(int original, ServerLevel level, @Nullable Entity killer) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!LevelingAPI.hasLevel(self)) {
            return original;
        }
        int levelValue = LevelingAPI.getLevel(self) + 1;
        double expBonus = Config.COMMON.expBonus.get() * levelValue;
        return (int) (original + original * expBonus);
    }

}
