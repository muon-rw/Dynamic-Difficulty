package dev.muon.dynamic_difficulty.loot.condition;

import dev.muon.dynamic_difficulty.DynamicDifficulty;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModLootConditions {
    public static final DeferredRegister<LootItemConditionType> REGISTRY =
            DeferredRegister.create(Registries.LOOT_CONDITION_TYPE, DynamicDifficulty.MODID);

    public static final DeferredHolder<LootItemConditionType, LootItemConditionType> ENTITY_LEVEL = REGISTRY.register(
            "entity_level",
            () -> new LootItemConditionType(EntityLevelCondition.CODEC)
    );
}

