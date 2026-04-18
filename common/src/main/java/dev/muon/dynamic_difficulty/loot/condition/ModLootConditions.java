package dev.muon.dynamic_difficulty.loot.condition;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

/**
 * Common registry references for loot condition types.
 * Populated by platform-specific initialization code.
 */
public class ModLootConditions {

    public static Holder<MapCodec<? extends LootItemCondition>> ENTITY_LEVEL;
}
