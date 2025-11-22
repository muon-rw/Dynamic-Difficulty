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

import java.util.function.Consumer;
import java.util.function.Function;

public class ModItems {
    // Level-up items with configurable level caps and progressive rarities
    // Max levels are fetched from config at runtime via suppliers
    public static final Item POTION_OF_GROWTH = registerItem(
            "potion_of_growth",
            props -> props.stacksTo(16).rarity(Rarity.COMMON),
            props -> new LevelUpItem(props, 1, Config.COMMON.potionOfGrowthMaxLevel, false)
    );
    public static final Item ELIXIR_OF_NURTURING = registerItem(
            "elixir_of_nurturing",
            props -> props.stacksTo(16).rarity(Rarity.UNCOMMON),
            props -> new LevelUpItem(props, 1, Config.COMMON.elixirOfNurturingMaxLevel, false)
    );

    public static final Item DRAUGHT_OF_ASCENSION = registerItem(
            "draught_of_ascension",
            props -> props.stacksTo(16).rarity(Rarity.RARE),
            props -> new LevelUpItem(props, 1, Config.COMMON.draughtOfAscensionMaxLevel, false)
    );

    public static final Item ESSENCE_OF_VITALITY = registerItem(
            "essence_of_vitality",
            props -> props.stacksTo(16).rarity(Rarity.RARE),
            props -> new LevelUpItem(props, 1, Config.COMMON.essenceOfVitalityMaxLevel, false)
    );

    public static final Item CRYSTAL_OF_AWAKENING = registerItem(
            "crystal_of_awakening",
            props -> props.stacksTo(16).rarity(Rarity.EPIC),
            props -> new LevelUpItem(props, 1, Config.COMMON.crystalOfAwakeningMaxLevel, true)
    );

    /**
     * Generic method to register any Item type, creating the ResourceKey once and handling both item creation and registration.
     * 
     * @param path The item path (e.g., "potion_of_growth")
     * @param propertiesConfigurator Function to configure the item properties
     * @param itemFactory Function that creates the Item instance from configured Properties
     * @return The registered item
     */
    private static Item registerItem(
            String path,
            Consumer<Item.Properties> propertiesConfigurator,
            Function<Item.Properties, Item> itemFactory
    ) {
        ResourceLocation id = DynamicDifficulty.id(path);
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id);
        
        Item.Properties properties = new Item.Properties();
        propertiesConfigurator.accept(properties);
        properties = properties.setId(itemKey);
        
        Item item = itemFactory.apply(properties);
        Registry.register(BuiltInRegistries.ITEM, itemKey, item);
        
        return item;
    }

    public static void init() {
    }
}

