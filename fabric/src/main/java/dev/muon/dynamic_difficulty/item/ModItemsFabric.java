package dev.muon.dynamic_difficulty.item;

import dev.muon.dynamic_difficulty.DynamicDifficulty;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;

public class ModItemsFabric {
    
    public static final Holder<Item> POTION_OF_GROWTH = Registry.registerForHolder(
            BuiltInRegistries.ITEM,
            DynamicDifficulty.loc("potion_of_growth"),
            ModItems.POTION_OF_GROWTH_SUPPLIER.get()
    );

    public static final Holder<Item> ELIXIR_OF_NURTURING = Registry.registerForHolder(
            BuiltInRegistries.ITEM,
            DynamicDifficulty.loc("elixir_of_nurturing"),
            ModItems.ELIXIR_OF_NURTURING_SUPPLIER.get()
    );

    public static final Holder<Item> DRAUGHT_OF_ASCENSION = Registry.registerForHolder(
            BuiltInRegistries.ITEM,
            DynamicDifficulty.loc("draught_of_ascension"),
            ModItems.DRAUGHT_OF_ASCENSION_SUPPLIER.get()
    );

    public static final Holder<Item> ESSENCE_OF_VITALITY = Registry.registerForHolder(
            BuiltInRegistries.ITEM,
            DynamicDifficulty.loc("essence_of_vitality"),
            ModItems.ESSENCE_OF_VITALITY_SUPPLIER.get()
    );

    public static final Holder<Item> CRYSTAL_OF_AWAKENING = Registry.registerForHolder(
            BuiltInRegistries.ITEM,
            DynamicDifficulty.loc("crystal_of_awakening"),
            ModItems.CRYSTAL_OF_AWAKENING_SUPPLIER.get()
    );
    
    public static void init() {
        // Populate common registry references
        ModItems.POTION_OF_GROWTH = POTION_OF_GROWTH;
        ModItems.ELIXIR_OF_NURTURING = ELIXIR_OF_NURTURING;
        ModItems.DRAUGHT_OF_ASCENSION = DRAUGHT_OF_ASCENSION;
        ModItems.ESSENCE_OF_VITALITY = ESSENCE_OF_VITALITY;
        ModItems.CRYSTAL_OF_AWAKENING = CRYSTAL_OF_AWAKENING;
    }
}
