package dev.muon.dynamic_difficulty.loot.condition;

import com.mojang.serialization.MapCodec;
import dev.muon.dynamic_difficulty.DynamicDifficulty;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class ModLootConditionsFabric {

    public static final Holder<MapCodec<? extends LootItemCondition>> ENTITY_LEVEL = Registry.registerForHolder(
            BuiltInRegistries.LOOT_CONDITION_TYPE,
            DynamicDifficulty.id("entity_level"),
            EntityLevelCondition.CODEC
    );

    public static void init() {
        // Populate common registry reference
        ModLootConditions.ENTITY_LEVEL = ENTITY_LEVEL;
    }
}
