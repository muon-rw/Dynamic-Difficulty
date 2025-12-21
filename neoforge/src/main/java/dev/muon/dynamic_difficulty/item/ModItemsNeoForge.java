package dev.muon.dynamic_difficulty.item;

import dev.muon.dynamic_difficulty.DynamicDifficulty;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

public class ModItemsNeoForge {
    public static final DeferredRegister<Item> REGISTRY =
            DeferredRegister.create(BuiltInRegistries.ITEM, DynamicDifficulty.MODID);

    public static final DeferredHolder<Item, LevelUpItem> POTION_OF_GROWTH = 
            REGISTRY.register("potion_of_growth", () -> ModItems.POTION_OF_GROWTH_FACTORY.apply(
                    ResourceKey.create(Registries.ITEM, DynamicDifficulty.id("potion_of_growth"))
            ));

    public static final DeferredHolder<Item, LevelUpItem> ELIXIR_OF_NURTURING = 
            REGISTRY.register("elixir_of_nurturing", () -> ModItems.ELIXIR_OF_NURTURING_FACTORY.apply(
                    ResourceKey.create(Registries.ITEM, DynamicDifficulty.id("elixir_of_nurturing"))
            ));

    public static final DeferredHolder<Item, LevelUpItem> DRAUGHT_OF_ASCENSION = 
            REGISTRY.register("draught_of_ascension", () -> ModItems.DRAUGHT_OF_ASCENSION_FACTORY.apply(
                    ResourceKey.create(Registries.ITEM, DynamicDifficulty.id("draught_of_ascension"))
            ));

    public static final DeferredHolder<Item, LevelUpItem> ESSENCE_OF_VITALITY = 
            REGISTRY.register("essence_of_vitality", () -> ModItems.ESSENCE_OF_VITALITY_FACTORY.apply(
                    ResourceKey.create(Registries.ITEM, DynamicDifficulty.id("essence_of_vitality"))
            ));

    public static final DeferredHolder<Item, LevelUpItem> CRYSTAL_OF_AWAKENING = 
            REGISTRY.register("crystal_of_awakening", () -> ModItems.CRYSTAL_OF_AWAKENING_FACTORY.apply(
                    ResourceKey.create(Registries.ITEM, DynamicDifficulty.id("crystal_of_awakening"))
            ));
    
    public static void init() {
        // Populate common registry references (DeferredHolder implements Holder)
        ModItems.POTION_OF_GROWTH = POTION_OF_GROWTH;
        ModItems.ELIXIR_OF_NURTURING = ELIXIR_OF_NURTURING;
        ModItems.DRAUGHT_OF_ASCENSION = DRAUGHT_OF_ASCENSION;
        ModItems.ESSENCE_OF_VITALITY = ESSENCE_OF_VITALITY;
        ModItems.CRYSTAL_OF_AWAKENING = CRYSTAL_OF_AWAKENING;
    }
}
