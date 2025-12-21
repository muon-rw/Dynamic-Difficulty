package dev.muon.dynamic_difficulty.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.muon.dynamic_difficulty.attribute.ModAttributes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {


    @Unique
    private static final TagKey<DamageType> MAGIC_DAMAGE = TagKey.create(
            Registries.DAMAGE_TYPE,
            Identifier.fromNamespaceAndPath("c", "is_magic")
    );

    @ModifyVariable(
            method = "hurtServer",
            at = @At("HEAD"),
            argsOnly = true,
            index = 3
    )
    private float modifyDamageAmount(float damageAmount, @Local(argsOnly = true) DamageSource damageSource) {
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

}
