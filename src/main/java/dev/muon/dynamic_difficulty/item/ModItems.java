package dev.muon.dynamic_difficulty.item;

import dev.muon.dynamic_difficulty.DynamicDifficulty;
import dev.muon.dynamic_difficulty.config.Config;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

public class ModItems {
    // Level-up items with configurable level caps and progressive rarities
    // Max levels are fetched from config at runtime via suppliers
    public static final Item POTION_OF_GROWTH = new LevelUpItem(
                    new Item.Properties().stacksTo(16).rarity(Rarity.COMMON), 
                    1, 
                    Config.COMMON.potionOfGrowthMaxLevel,
            false);

    public static final Item ELIXIR_OF_NURTURING = new LevelUpItem(
                    new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON), 
                    1, 
                    Config.COMMON.elixirOfNurturingMaxLevel,
            false);

    public static final Item DRAUGHT_OF_ASCENSION = new LevelUpItem(
                    new Item.Properties().stacksTo(16).rarity(Rarity.RARE), 
                    1, 
                    Config.COMMON.draughtOfAscensionMaxLevel,
            false);

    public static final Item ESSENCE_OF_VITALITY = new LevelUpItem(
                    new Item.Properties().stacksTo(16).rarity(Rarity.RARE), 
                    1,
                    Config.COMMON.essenceOfVitalityMaxLevel,
            false);

    public static final Item CRYSTAL_OF_AWAKENING = new LevelUpItem(
                    new Item.Properties().stacksTo(16).rarity(Rarity.EPIC), 
                    1,
                    Config.COMMON.crystalOfAwakeningMaxLevel,
            true);  // Has enchantment glint

    public static void register() {
        Registry.register(BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath(DynamicDifficulty.MODID, "potion_of_growth"), POTION_OF_GROWTH);
        Registry.register(BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath(DynamicDifficulty.MODID, "elixir_of_nurturing"), ELIXIR_OF_NURTURING);
        Registry.register(BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath(DynamicDifficulty.MODID, "draught_of_ascension"), DRAUGHT_OF_ASCENSION);
        Registry.register(BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath(DynamicDifficulty.MODID, "essence_of_vitality"), ESSENCE_OF_VITALITY);
        Registry.register(BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath(DynamicDifficulty.MODID, "crystal_of_awakening"), CRYSTAL_OF_AWAKENING);
    }
}

