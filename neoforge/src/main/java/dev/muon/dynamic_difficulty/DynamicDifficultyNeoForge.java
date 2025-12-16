package dev.muon.dynamic_difficulty;

import dev.muon.dynamic_difficulty.api.LevelingAPI;
import dev.muon.dynamic_difficulty.attribute.ModAttributesNeoForge;
import dev.muon.dynamic_difficulty.compat.dungeon_difficulty.DungeonDifficultyAttachmentNeoForge;
import dev.muon.dynamic_difficulty.compat.puffish.PuffishSkillsProviderNeoForge;
import dev.muon.dynamic_difficulty.compat.reskillable.ReskillableReimaginedProvider;
import dev.muon.dynamic_difficulty.config.Config;
import dev.muon.dynamic_difficulty.item.ModItemsNeoForge;
import dev.muon.dynamic_difficulty.loot.condition.ModLootConditionsNeoForge;
import dev.muon.dynamic_difficulty.loot.modifier.ModLootModifiersNeoForge;
import dev.muon.dynamic_difficulty.platform.PlatformHelperNeoForge;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.event.AddPackFindersEvent;

@Mod(DynamicDifficulty.MODID)
public class DynamicDifficultyNeoForge {

    public DynamicDifficultyNeoForge(IEventBus eventBus, ModContainer modContainer) {
        // Initialize platform helper first
        DynamicDifficulty.setHelper(new PlatformHelperNeoForge());

        // Initialize common mod code
        DynamicDifficulty.init();

        // Compat providers
        if (DynamicDifficulty.isModLoaded("puffish_skills")) {
            LevelingAPI.registerPlayerLevelProvider(new PuffishSkillsProviderNeoForge());
        }
        if (DynamicDifficulty.isModLoaded("reskillable")) {
            LevelingAPI.registerPlayerLevelProvider(new ReskillableReimaginedProvider());
        }
        if (DynamicDifficulty.isModLoaded("dungeon_difficulty")) {
            DungeonDifficultyAttachmentNeoForge.REGISTRY.register(eventBus);
        }

        // Registries
        ModAttributesNeoForge.REGISTRY.register(eventBus);
        ModItemsNeoForge.REGISTRY.register(eventBus);
        ModLootConditionsNeoForge.REGISTRY.register(eventBus);
        ModLootModifiersNeoForge.REGISTRY.register(eventBus);
        EntityLevelAttachmentNeoForge.REGISTRY.register(eventBus);

        ModAttributesNeoForge.init();
        ModItemsNeoForge.init();
        ModLootConditionsNeoForge.init();
        
        // Built-in datapack
        eventBus.addListener(this::addPackFinders);
        
        // Register config
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.COMMON_SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, Config.CLIENT_SPEC);
    }

    
    private void addPackFinders(AddPackFindersEvent event) {
        if (Config.COMMON.useDefaultLevelingSettings.get()) {
            event.addPackFinders(
                    DynamicDifficulty.loc("resourcepacks/default"),
                    PackType.SERVER_DATA,
                    Component.literal("Dynamic Difficulty Defaults"),
                    PackSource.BUILT_IN,
                    false,
                    Pack.Position.BOTTOM
            );
        }
    }

}