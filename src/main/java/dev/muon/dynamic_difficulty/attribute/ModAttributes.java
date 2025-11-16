package dev.muon.dynamic_difficulty.attribute;

import dev.muon.dynamic_difficulty.DynamicDifficulty;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.function.BiConsumer;


@EventBusSubscriber
public class ModAttributes {
    public static final DeferredRegister<Attribute> REGISTRY =
            DeferredRegister.create(BuiltInRegistries.ATTRIBUTE, DynamicDifficulty.MODID);

    public static final DeferredHolder<Attribute, Attribute> PROJECTILE_DAMAGE_BONUS =
            rangedAttribute("generic", "projectile_damage_bonus", 0, 0, 65536);
    public static final DeferredHolder<Attribute, Attribute> PROJECTILE_DAMAGE_MULTIPLIER =
            rangedAttribute("generic", "projectile_damage_multiplier", 0, 0, 65536);
    public static final DeferredHolder<Attribute, Attribute> EXPLOSION_DAMAGE_BONUS =
            rangedAttribute("generic", "explosion_damage_bonus", 0, 0, 65536);
    public static final DeferredHolder<Attribute, Attribute> EXPLOSION_DAMAGE_MULTIPLIER =
            rangedAttribute("generic", "explosion_damage_multiplier", 0, 0, 65536);
    public static final DeferredHolder<Attribute, Attribute> DAMAGE_BONUS =
            rangedAttribute("generic", "damage_bonus", 0, 0, 65536);
    public static final DeferredHolder<Attribute, Attribute> DAMAGE_MULTIPLIER =
            rangedAttribute("generic", "damage_multiplier", 0, 0, 65536);
    public static final DeferredHolder<Attribute, Attribute> MAGIC_DAMAGE_BONUS =
            rangedAttribute("generic", "magic_damage_bonus", 0, 0, 65536);
    public static final DeferredHolder<Attribute, Attribute> MAGIC_DAMAGE_MULTIPLIER =
            rangedAttribute("generic", "magic_damage_multiplier", 0, 0, 65536);



    private static DeferredHolder<Attribute, Attribute> rangedAttribute(
            String category, String name, double defaultValue, double minValue, double maxValue) {
        return REGISTRY.register(
                name,
                () ->
                        new RangedAttribute("attribute." + DynamicDifficulty.MODID + "." + name, defaultValue, minValue, maxValue)
                                .setSyncable(true));
    }


    @SubscribeEvent
    public static void attachMobAttributes(EntityAttributeModificationEvent e) {
        e.getTypes().forEach(type -> {
            addAll(type, e::add,
                    ModAttributes.PROJECTILE_DAMAGE_BONUS,
                    ModAttributes.PROJECTILE_DAMAGE_MULTIPLIER,
                    ModAttributes.EXPLOSION_DAMAGE_BONUS,
                    ModAttributes.EXPLOSION_DAMAGE_MULTIPLIER,
                    ModAttributes.DAMAGE_BONUS,
                    ModAttributes.DAMAGE_MULTIPLIER,
                    ModAttributes.MAGIC_DAMAGE_BONUS,
                    ModAttributes.MAGIC_DAMAGE_MULTIPLIER
            );
        });
    }

    @SafeVarargs
    private static void addAll(EntityType<? extends LivingEntity> type, BiConsumer<EntityType<? extends LivingEntity>, Holder<Attribute>> add, Holder<Attribute>... attribs) {
        for (Holder<Attribute> a : attribs)
            add.accept(type, a);
    }
}
