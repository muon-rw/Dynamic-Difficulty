package dev.muon.dynamic_difficulty.item;

import dev.muon.dynamic_difficulty.config.Config;
import net.minecraft.core.Holder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

import java.util.function.Supplier;

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
    public static final Supplier<LevelUpItem> POTION_OF_GROWTH_SUPPLIER = () -> new LevelUpItem(
            new Item.Properties().stacksTo(16).rarity(Rarity.COMMON),
            1,
            Config.COMMON.potionOfGrowthMaxLevel,
            false
    );
    
    public static final Supplier<LevelUpItem> ELIXIR_OF_NURTURING_SUPPLIER = () -> new LevelUpItem(
            new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON),
            1,
            Config.COMMON.elixirOfNurturingMaxLevel,
            false
    );
    
    public static final Supplier<LevelUpItem> DRAUGHT_OF_ASCENSION_SUPPLIER = () -> new LevelUpItem(
            new Item.Properties().stacksTo(16).rarity(Rarity.RARE),
            1,
            Config.COMMON.draughtOfAscensionMaxLevel,
            false
    );
    
    public static final Supplier<LevelUpItem> ESSENCE_OF_VITALITY_SUPPLIER = () -> new LevelUpItem(
            new Item.Properties().stacksTo(16).rarity(Rarity.RARE),
            1,
            Config.COMMON.essenceOfVitalityMaxLevel,
            false
    );
    
    public static final Supplier<LevelUpItem> CRYSTAL_OF_AWAKENING_SUPPLIER = () -> new LevelUpItem(
            new Item.Properties().stacksTo(16).rarity(Rarity.EPIC),
            1,
            Config.COMMON.crystalOfAwakeningMaxLevel,
            true  // Has enchantment glint
    );
    
}
