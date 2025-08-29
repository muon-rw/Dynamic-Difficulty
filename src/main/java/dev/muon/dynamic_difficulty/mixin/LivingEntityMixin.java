package dev.muon.dynamic_difficulty.mixin;

import dev.muon.dynamic_difficulty.attribute.ModAttributes;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {

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
            if (damageSource.getDirectEntity() instanceof Projectile || damageSource.is(DamageTypeTags.IS_PROJECTILE)) {
                damageAmount += (float) attacker.getAttributeValue(ModAttributes.PROJECTILE_DAMAGE_BONUS);
                damageAmount *= (float) (1 + attacker.getAttributeValue(ModAttributes.PROJECTILE_DAMAGE_MULTIPLIER));
            } else if (damageSource.is(DamageTypes.EXPLOSION)) {
                damageAmount += (float) attacker.getAttributeValue(ModAttributes.EXPLOSION_DAMAGE_BONUS);
                damageAmount *= (float) (1 + attacker.getAttributeValue(ModAttributes.EXPLOSION_DAMAGE_MULTIPLIER));
            }
        }

        return damageAmount;
    }

}
