package dev.muon.dynamic_difficulty.attribute;

import dev.muon.dynamic_difficulty.DynamicDifficulty;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.ai.attributes.Attribute;

public class ModAttributesFabric {
    
    // Attribute addition to entities is handled via LivingEntityMixin
    
    public static final Holder<Attribute> PROJECTILE_DAMAGE_BONUS = Registry.registerForHolder(
            BuiltInRegistries.ATTRIBUTE,
            DynamicDifficulty.loc("projectile_damage_bonus"),
            ModAttributes.PROJECTILE_DAMAGE_BONUS_SUPPLIER.get()
    );
    public static final Holder<Attribute> PROJECTILE_DAMAGE_MULTIPLIER = Registry.registerForHolder(
            BuiltInRegistries.ATTRIBUTE,
            DynamicDifficulty.loc("projectile_damage_multiplier"),
            ModAttributes.PROJECTILE_DAMAGE_MULTIPLIER_SUPPLIER.get()
    );
    public static final Holder<Attribute> EXPLOSION_DAMAGE_BONUS = Registry.registerForHolder(
            BuiltInRegistries.ATTRIBUTE,
            DynamicDifficulty.loc("explosion_damage_bonus"),
            ModAttributes.EXPLOSION_DAMAGE_BONUS_SUPPLIER.get()
    );
    public static final Holder<Attribute> EXPLOSION_DAMAGE_MULTIPLIER = Registry.registerForHolder(
            BuiltInRegistries.ATTRIBUTE,
            DynamicDifficulty.loc("explosion_damage_multiplier"),
            ModAttributes.EXPLOSION_DAMAGE_MULTIPLIER_SUPPLIER.get()
    );
    public static final Holder<Attribute> DAMAGE_BONUS = Registry.registerForHolder(
            BuiltInRegistries.ATTRIBUTE,
            DynamicDifficulty.loc("damage_bonus"),
            ModAttributes.DAMAGE_BONUS_SUPPLIER.get()
    );
    public static final Holder<Attribute> DAMAGE_MULTIPLIER = Registry.registerForHolder(
            BuiltInRegistries.ATTRIBUTE,
            DynamicDifficulty.loc("damage_multiplier"),
            ModAttributes.DAMAGE_MULTIPLIER_SUPPLIER.get()
    );
    public static final Holder<Attribute> MAGIC_DAMAGE_BONUS = Registry.registerForHolder(
            BuiltInRegistries.ATTRIBUTE,
            DynamicDifficulty.loc("magic_damage_bonus"),
            ModAttributes.MAGIC_DAMAGE_BONUS_SUPPLIER.get()
    );
    public static final Holder<Attribute> MAGIC_DAMAGE_MULTIPLIER = Registry.registerForHolder(
            BuiltInRegistries.ATTRIBUTE,
            DynamicDifficulty.loc("magic_damage_multiplier"),
            ModAttributes.MAGIC_DAMAGE_MULTIPLIER_SUPPLIER.get()
    );
    
    public static void init() {
        // Populate common registry references
        ModAttributes.PROJECTILE_DAMAGE_BONUS = PROJECTILE_DAMAGE_BONUS;
        ModAttributes.PROJECTILE_DAMAGE_MULTIPLIER = PROJECTILE_DAMAGE_MULTIPLIER;
        ModAttributes.EXPLOSION_DAMAGE_BONUS = EXPLOSION_DAMAGE_BONUS;
        ModAttributes.EXPLOSION_DAMAGE_MULTIPLIER = EXPLOSION_DAMAGE_MULTIPLIER;
        ModAttributes.DAMAGE_BONUS = DAMAGE_BONUS;
        ModAttributes.DAMAGE_MULTIPLIER = DAMAGE_MULTIPLIER;
        ModAttributes.MAGIC_DAMAGE_BONUS = MAGIC_DAMAGE_BONUS;
        ModAttributes.MAGIC_DAMAGE_MULTIPLIER = MAGIC_DAMAGE_MULTIPLIER;
    }
}
