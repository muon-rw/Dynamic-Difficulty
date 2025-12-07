package dev.muon.dynamic_difficulty;

import dev.muon.dynamic_difficulty.api.LevelingAPI;
import dev.muon.dynamic_difficulty.compat.puffish.PuffishSkillsProviderNeoForge;
import dev.muon.dynamic_difficulty.compat.reskillable.ReskillableReimaginedProvider;
import dev.muon.dynamic_difficulty.config.Config;
import dev.muon.dynamic_difficulty.platform.PlatformHelperNeoForge;
import dev.muon.dynamic_difficulty.platform.PlatformRegistries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

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

        // Register all platform-specific registries and populate common references
        PlatformRegistries.registerAll(eventBus);
        
        // Register config
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.COMMON_SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, Config.CLIENT_SPEC);
    }
}