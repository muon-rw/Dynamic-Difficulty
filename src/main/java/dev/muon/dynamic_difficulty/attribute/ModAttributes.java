package dev.muon.dynamic_difficulty.attribute;

import dev.muon.dynamic_difficulty.DynamicDifficulty;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;


public class ModAttributes {
    public static final Holder.Reference<Attribute> PROJECTILE_DAMAGE_BONUS =
            registerAttribute("projectile_damage_bonus", 0, 0, 65536);
    public static final Holder.Reference<Attribute> PROJECTILE_DAMAGE_MULTIPLIER =
            registerAttribute("projectile_damage_multiplier", 0, 0, 65536);
    public static final Holder.Reference<Attribute> EXPLOSION_DAMAGE_BONUS =
            registerAttribute("explosion_damage_bonus", 0, 0, 65536);
    public static final Holder.Reference<Attribute> EXPLOSION_DAMAGE_MULTIPLIER =
            registerAttribute("explosion_damage_multiplier", 0, 0, 65536);
    public static final Holder.Reference<Attribute> DAMAGE_BONUS =
            registerAttribute("damage_bonus", 0, 0, 65536);
    public static final Holder.Reference<Attribute> DAMAGE_MULTIPLIER =
            registerAttribute("damage_multiplier", 0, 0, 65536);
    public static final Holder.Reference<Attribute> MAGIC_DAMAGE_BONUS =
            registerAttribute("magic_damage_bonus", 0, 0, 65536);
    public static final Holder.Reference<Attribute> MAGIC_DAMAGE_MULTIPLIER =
            registerAttribute("magic_damage_multiplier", 0, 0, 65536);

    private static Holder.Reference<Attribute> registerAttribute(
            String name, double defaultValue, double minValue, double maxValue) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(DynamicDifficulty.MODID, name);
        Attribute attribute = new RangedAttribute("attribute." + DynamicDifficulty.MODID + "." + name, defaultValue, minValue, maxValue)
                .setSyncable(true);
        return Registry.registerForHolder(BuiltInRegistries.ATTRIBUTE, id, attribute);
    }

    public static void init() {
    }
}
