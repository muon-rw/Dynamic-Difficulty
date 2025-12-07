package dev.muon.dynamic_difficulty.loot.condition;

import dev.muon.dynamic_difficulty.DynamicDifficulty;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;

public class ModLootConditionsFabric {
    
    public static final Holder<LootItemConditionType> ENTITY_LEVEL = Registry.registerForHolder(
            BuiltInRegistries.LOOT_CONDITION_TYPE,
            DynamicDifficulty.loc("entity_level"),
            new LootItemConditionType(EntityLevelCondition.CODEC)
    );
    
    public static void init() {
        // Populate common registry reference
        ModLootConditions.ENTITY_LEVEL = ENTITY_LEVEL;
    }
}
