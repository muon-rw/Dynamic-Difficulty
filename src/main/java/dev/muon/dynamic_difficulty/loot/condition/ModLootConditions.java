package dev.muon.dynamic_difficulty.loot.condition;

import dev.muon.dynamic_difficulty.DynamicDifficulty;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;

public class ModLootConditions {
    public static final LootItemConditionType ENTITY_LEVEL = new LootItemConditionType(EntityLevelCondition.CODEC);

    public static void init() {
        Registry.register(BuiltInRegistries.LOOT_CONDITION_TYPE, ResourceLocation.fromNamespaceAndPath(DynamicDifficulty.MODID, "entity_level"), ENTITY_LEVEL);
    }
}

