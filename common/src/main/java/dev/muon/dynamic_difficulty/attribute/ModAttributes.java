package dev.muon.dynamic_difficulty.attribute;

import dev.muon.dynamic_difficulty.DynamicDifficulty;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;

import java.util.function.Supplier;

public class ModAttributes {

    public static Holder<Attribute> PROJECTILE_DAMAGE_BONUS;
    public static Holder<Attribute> PROJECTILE_DAMAGE_MULTIPLIER;
    public static Holder<Attribute> EXPLOSION_DAMAGE_BONUS;
    public static Holder<Attribute> EXPLOSION_DAMAGE_MULTIPLIER;
    public static Holder<Attribute> DAMAGE_BONUS;
    public static Holder<Attribute> DAMAGE_MULTIPLIER;
    public static Holder<Attribute> MAGIC_DAMAGE_BONUS;
    public static Holder<Attribute> MAGIC_DAMAGE_MULTIPLIER;

    public static final Supplier<Attribute> PROJECTILE_DAMAGE_BONUS_SUPPLIER =
            () -> createAttribute("projectile_damage_bonus");
    public static final Supplier<Attribute> PROJECTILE_DAMAGE_MULTIPLIER_SUPPLIER = 
            () -> createAttribute("projectile_damage_multiplier");
    public static final Supplier<Attribute> EXPLOSION_DAMAGE_BONUS_SUPPLIER = 
            () -> createAttribute("explosion_damage_bonus");
    public static final Supplier<Attribute> EXPLOSION_DAMAGE_MULTIPLIER_SUPPLIER = 
            () -> createAttribute("explosion_damage_multiplier");
    public static final Supplier<Attribute> DAMAGE_BONUS_SUPPLIER = 
            () -> createAttribute("damage_bonus");
    public static final Supplier<Attribute> DAMAGE_MULTIPLIER_SUPPLIER = 
            () -> createAttribute("damage_multiplier");
    public static final Supplier<Attribute> MAGIC_DAMAGE_BONUS_SUPPLIER = 
            () -> createAttribute("magic_damage_bonus");
    public static final Supplier<Attribute> MAGIC_DAMAGE_MULTIPLIER_SUPPLIER = 
            () -> createAttribute("magic_damage_multiplier");
    
    private static Attribute createAttribute(String name) {
        return new RangedAttribute(
                "attribute." + DynamicDifficulty.MODID + "." + name, 
                0, 0, 65536
        ).setSyncable(true);
    }
    
}
