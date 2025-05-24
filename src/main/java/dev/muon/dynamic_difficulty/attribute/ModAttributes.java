package dev.muon.dynamic_difficulty.attribute;

import dev.muon.dynamic_difficulty.DynamicDifficulty;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;


@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD)
public class ModAttributes {
    public static final DeferredRegister<Attribute> REGISTRY =
            DeferredRegister.create(BuiltInRegistries.ATTRIBUTE, DynamicDifficulty.MODID);

    public static final DeferredHolder<Attribute, Attribute> PROJECTILE_DAMAGE_MULTIPLIER =
            rangedAttribute("monster", "projectile_damage_bonus", 1, 1, 1000);
    public static final DeferredHolder<Attribute, Attribute> EXPLOSION_DAMAGE_MULTIPLIER =
            rangedAttribute("monster", "explosion_damage_bonus", 1, 1, 1000);

    private static DeferredHolder<Attribute, Attribute> rangedAttribute(
            String category, String name, double defaultValue, double minValue, double maxValue) {
        return REGISTRY.register(
                name,
                () ->
                        new RangedAttribute("attribute." + DynamicDifficulty.MODID + "." + name, defaultValue, minValue, maxValue)
                                .setSyncable(true));
    }


    @SubscribeEvent
    public static void attachMobAttributes(EntityAttributeModificationEvent event) {
        event
                .getTypes()
                .forEach(
                        entityType -> {
                            event.add(entityType, Holder.direct(ModAttributes.PROJECTILE_DAMAGE_MULTIPLIER.get()));
                            event.add(entityType, Holder.direct(ModAttributes.EXPLOSION_DAMAGE_MULTIPLIER.get()));
                        });
    }
}
