package dev.muon.dynamic_difficulty.item;

import dev.muon.dynamic_difficulty.DynamicDifficulty;
import dev.muon.dynamic_difficulty.config.Config;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

public class ModItems {
    public static final DeferredRegister<Item> REGISTRY =
            DeferredRegister.create(BuiltInRegistries.ITEM, DynamicDifficulty.MODID);

    // Level-up items with configurable level caps and progressive rarities
    // Max levels are fetched from config at runtime via suppliers
    public static final DeferredHolder<Item, LevelUpItem> POTION_OF_GROWTH = REGISTRY.register(
            "potion_of_growth",
            () -> new LevelUpItem(
                    new Item.Properties().stacksTo(16).rarity(Rarity.COMMON), 
                    1, 
                    Config.COMMON.potionOfGrowthMaxLevel,
                    false)
    );

    public static final DeferredHolder<Item, LevelUpItem> ELIXIR_OF_NURTURING = REGISTRY.register(
            "elixir_of_nurturing",
            () -> new LevelUpItem(
                    new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON), 
                    1, 
                    Config.COMMON.elixirOfNurturingMaxLevel,
                    false)
    );

    public static final DeferredHolder<Item, LevelUpItem> DRAUGHT_OF_ASCENSION = REGISTRY.register(
            "draught_of_ascension",
            () -> new LevelUpItem(
                    new Item.Properties().stacksTo(16).rarity(Rarity.RARE), 
                    1, 
                    Config.COMMON.draughtOfAscensionMaxLevel,
                    false)
    );

    public static final DeferredHolder<Item, LevelUpItem> ESSENCE_OF_VITALITY = REGISTRY.register(
            "essence_of_vitality",
            () -> new LevelUpItem(
                    new Item.Properties().stacksTo(16).rarity(Rarity.RARE), 
                    1,
                    Config.COMMON.essenceOfVitalityMaxLevel,
                    false)
    );

    public static final DeferredHolder<Item, LevelUpItem> CRYSTAL_OF_AWAKENING = REGISTRY.register(
            "crystal_of_awakening",
            () -> new LevelUpItem(
                    new Item.Properties().stacksTo(16).rarity(Rarity.EPIC), 
                    1,
                    Config.COMMON.crystalOfAwakeningMaxLevel,
                    true)  // Has enchantment glint
    );
}

