package dev.muon.dynamic_difficulty.item;

import dev.muon.dynamic_difficulty.config.Config;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

import java.util.function.Function;

/**
 * Common item definitions and registry references.
 * Item factories are defined here; platform code handles registration.
 */
public class ModItems {
    
    // Registry references - initialized by platform-specific code
    public static Holder<Item> POTION_OF_GROWTH;
    public static Holder<Item> ELIXIR_OF_NURTURING;
    public static Holder<Item> DRAUGHT_OF_ASCENSION;
    public static Holder<Item> ESSENCE_OF_VITALITY;
    public static Holder<Item> CRYSTAL_OF_AWAKENING;
    
    // Item factories - shared definitions, platform code uses these for registration
    // These accept a ResourceKey and set the ID on properties before creating the item
    public static final Function<ResourceKey<Item>, LevelUpItem> POTION_OF_GROWTH_FACTORY = (key) -> {
        Item.Properties props = new Item.Properties().stacksTo(16).rarity(Rarity.COMMON);
        props = props.setId(key);
        return new LevelUpItem(props, 1, Config.COMMON.potionOfGrowthMaxLevel, false);
    };
    
    public static final Function<ResourceKey<Item>, LevelUpItem> ELIXIR_OF_NURTURING_FACTORY = (key) -> {
        Item.Properties props = new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON);
        props = props.setId(key);
        return new LevelUpItem(props, 1, Config.COMMON.elixirOfNurturingMaxLevel, false);
    };
    
    public static final Function<ResourceKey<Item>, LevelUpItem> DRAUGHT_OF_ASCENSION_FACTORY = (key) -> {
        Item.Properties props = new Item.Properties().stacksTo(16).rarity(Rarity.RARE);
        props = props.setId(key);
        return new LevelUpItem(props, 1, Config.COMMON.draughtOfAscensionMaxLevel, false);
    };
    
    public static final Function<ResourceKey<Item>, LevelUpItem> ESSENCE_OF_VITALITY_FACTORY = (key) -> {
        Item.Properties props = new Item.Properties().stacksTo(16).rarity(Rarity.RARE);
        props = props.setId(key);
        return new LevelUpItem(props, 1, Config.COMMON.essenceOfVitalityMaxLevel, false);
    };
    
    public static final Function<ResourceKey<Item>, LevelUpItem> CRYSTAL_OF_AWAKENING_FACTORY = (key) -> {
        Item.Properties props = new Item.Properties().stacksTo(16).rarity(Rarity.EPIC);
        props = props.setId(key);
        return new LevelUpItem(props, 1, Config.COMMON.crystalOfAwakeningMaxLevel, true);  // Has enchantment glint
    };
    
}
