package dev.muon.dynamic_difficulty.loot.modifier;

import com.mojang.serialization.MapCodec;
import dev.muon.dynamic_difficulty.DynamicDifficulty;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public class ModLootModifiersNeoForge {
    public static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> REGISTRY =
            DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, DynamicDifficulty.MODID);

    public static final DeferredHolder<MapCodec<? extends IGlobalLootModifier>, MapCodec<InjectLootTableModifier>> INJECT_LOOT_TABLE = 
            REGISTRY.register("inject_loot_table", () -> InjectLootTableModifier.CODEC);
}
