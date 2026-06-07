package dev.muon.dynamic_difficulty.loot.condition;

import com.mojang.serialization.MapCodec;
import dev.muon.dynamic_difficulty.DynamicDifficulty;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModLootConditionsNeoForge {
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static final DeferredRegister<MapCodec<? extends LootItemCondition>> REGISTRY =
            (DeferredRegister) DeferredRegister.create(Registries.LOOT_CONDITION_TYPE, DynamicDifficulty.MODID);

    public static final DeferredHolder<MapCodec<? extends LootItemCondition>, MapCodec<EntityLevelCondition>> ENTITY_LEVEL = REGISTRY.register(
            "entity_level",
            () -> EntityLevelCondition.CODEC
    );

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void init() {
        ModLootConditions.ENTITY_LEVEL = (Holder) ENTITY_LEVEL;
    }
}
