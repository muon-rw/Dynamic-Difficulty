package dev.muon.dynamic_difficulty.attribute;

import dev.muon.dynamic_difficulty.DynamicDifficulty;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.function.BiConsumer;

@EventBusSubscriber
public class ModAttributesNeoForge {
    public static final DeferredRegister<Attribute> REGISTRY =
            DeferredRegister.create(BuiltInRegistries.ATTRIBUTE, DynamicDifficulty.MODID);

    public static final DeferredHolder<Attribute, Attribute> PROJECTILE_DAMAGE_BONUS =
            REGISTRY.register("projectile_damage_bonus", ModAttributes.PROJECTILE_DAMAGE_BONUS_SUPPLIER);
    public static final DeferredHolder<Attribute, Attribute> PROJECTILE_DAMAGE_MULTIPLIER =
            REGISTRY.register("projectile_damage_multiplier", ModAttributes.PROJECTILE_DAMAGE_MULTIPLIER_SUPPLIER);
    public static final DeferredHolder<Attribute, Attribute> EXPLOSION_DAMAGE_BONUS =
            REGISTRY.register("explosion_damage_bonus", ModAttributes.EXPLOSION_DAMAGE_BONUS_SUPPLIER);
    public static final DeferredHolder<Attribute, Attribute> EXPLOSION_DAMAGE_MULTIPLIER =
            REGISTRY.register("explosion_damage_multiplier", ModAttributes.EXPLOSION_DAMAGE_MULTIPLIER_SUPPLIER);
    public static final DeferredHolder<Attribute, Attribute> DAMAGE_BONUS =
            REGISTRY.register("damage_bonus", ModAttributes.DAMAGE_BONUS_SUPPLIER);
    public static final DeferredHolder<Attribute, Attribute> DAMAGE_MULTIPLIER =
            REGISTRY.register("damage_multiplier", ModAttributes.DAMAGE_MULTIPLIER_SUPPLIER);
    public static final DeferredHolder<Attribute, Attribute> MAGIC_DAMAGE_BONUS =
            REGISTRY.register("magic_damage_bonus", ModAttributes.MAGIC_DAMAGE_BONUS_SUPPLIER);
    public static final DeferredHolder<Attribute, Attribute> MAGIC_DAMAGE_MULTIPLIER =
            REGISTRY.register("magic_damage_multiplier", ModAttributes.MAGIC_DAMAGE_MULTIPLIER_SUPPLIER);

    @SubscribeEvent
    public static void attachMobAttributes(EntityAttributeModificationEvent e) {
        e.getTypes().forEach(type -> {
            addAll(type, e::add,
                    PROJECTILE_DAMAGE_BONUS,
                    PROJECTILE_DAMAGE_MULTIPLIER,
                    EXPLOSION_DAMAGE_BONUS,
                    EXPLOSION_DAMAGE_MULTIPLIER,
                    DAMAGE_BONUS,
                    DAMAGE_MULTIPLIER,
                    MAGIC_DAMAGE_BONUS,
                    MAGIC_DAMAGE_MULTIPLIER
            );
        });
    }

    @SafeVarargs
    private static void addAll(EntityType<? extends LivingEntity> type, BiConsumer<EntityType<? extends LivingEntity>, Holder<Attribute>> add, Holder<Attribute>... attribs) {
        for (Holder<Attribute> a : attribs)
            add.accept(type, a);
    }
    
    public static void init() {
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
