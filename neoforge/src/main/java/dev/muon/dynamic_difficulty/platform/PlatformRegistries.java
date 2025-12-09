package dev.muon.dynamic_difficulty.platform;

import dev.muon.dynamic_difficulty.attribute.ModAttributesNeoForge;
import dev.muon.dynamic_difficulty.compat.dungeon_difficulty.DungeonDifficultyAttachmentNeoForge;
import dev.muon.dynamic_difficulty.item.ModItemsNeoForge;
import dev.muon.dynamic_difficulty.loot.condition.ModLootConditionsNeoForge;
import dev.muon.dynamic_difficulty.loot.modifier.ModLootModifiersNeoForge;
import dev.muon.dynamic_difficulty.EntityLevelAttachmentNeoForge;
import net.neoforged.bus.api.IEventBus;

/**
 * Platform-specific registry initialization for NeoForge.
 */
public class PlatformRegistries {
    
    public static void registerAll(IEventBus eventBus) {
        // Register deferred registries
        ModAttributesNeoForge.REGISTRY.register(eventBus);
        ModItemsNeoForge.REGISTRY.register(eventBus);
        ModLootConditionsNeoForge.REGISTRY.register(eventBus);
        ModLootModifiersNeoForge.REGISTRY.register(eventBus);
        EntityLevelAttachmentNeoForge.REGISTRY.register(eventBus);
        DungeonDifficultyAttachmentNeoForge.REGISTRY.register(eventBus);
        
        // Populate common references (DeferredHolder implements Holder, so this works immediately)
        ModAttributesNeoForge.init();
        ModItemsNeoForge.init();
        ModLootConditionsNeoForge.init();
    }
}
