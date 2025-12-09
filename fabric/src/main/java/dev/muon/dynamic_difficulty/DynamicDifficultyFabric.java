package dev.muon.dynamic_difficulty;

import dev.muon.dynamic_difficulty.api.LevelingAPI;
import dev.muon.dynamic_difficulty.attribute.ModAttributesFabric;
import dev.muon.dynamic_difficulty.compat.puffish.PuffishSkillsProviderFabric;
import dev.muon.dynamic_difficulty.config.Config;
import dev.muon.dynamic_difficulty.data.*;
import dev.muon.dynamic_difficulty.item.ModItemsFabric;
import dev.muon.dynamic_difficulty.loot.condition.ModLootConditionsFabric;
import dev.muon.dynamic_difficulty.network.NetworkRegistration;
import dev.muon.dynamic_difficulty.platform.PlatformHelperFabric;
import fuzs.forgeconfigapiport.fabric.api.neoforge.v4.NeoForgeConfigRegistry;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.server.packs.PackType;
import net.neoforged.fml.config.ModConfig;

public class DynamicDifficultyFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        // Initialize platform helper first
        DynamicDifficulty.setHelper(new PlatformHelperFabric());

        NeoForgeConfigRegistry.INSTANCE.register(DynamicDifficulty.MODID, ModConfig.Type.COMMON, Config.COMMON_SPEC);
        NeoForgeConfigRegistry.INSTANCE.register(DynamicDifficulty.MODID, ModConfig.Type.CLIENT, Config.CLIENT_SPEC);
        // Register mod-specific providers
        if (DynamicDifficulty.isModLoaded("puffish_skills")) {
            LevelingAPI.registerPlayerLevelProvider(new PuffishSkillsProviderFabric());
        }

        // Register attachment types early - MUST happen before networking to ensure
        // attachments are registered on clients before sync packets arrive
        EntityLevelAttachmentFabric.init();
        if (DynamicDifficulty.isModLoaded("dungeon_difficulty")) {
            dev.muon.dynamic_difficulty.compat.dungeon_difficulty.DungeonDifficultyAttachmentFabric.init();
        }
        
        ModAttributesFabric.init();
        ModItemsFabric.init();
        ModLootConditionsFabric.init();
        // Loot modifiers handled via LootTableMixin

        NetworkRegistration.register();
        
        // Register data reloaders
        registerDataReloaders();
        
        // Register event handlers
        LevelingEventsFabric.init();
    }
    
    private void registerDataReloaders() {
        var serverData = ResourceManagerHelper.get(PackType.SERVER_DATA);
        
        serverData.registerReloadListener(new BiomeLevelingSettingsReloaderFabric());
        serverData.registerReloadListener(new BiomeTagLevelingSettingsReloaderFabric());
        serverData.registerReloadListener(new DimensionsLevelingSettingsReloaderFabric());
        serverData.registerReloadListener(new DimensionTagLevelingSettingsReloaderFabric());
        serverData.registerReloadListener(new EntityLevelingSettingsReloaderFabric());
        serverData.registerReloadListener(new EntityTagLevelingSettingsReloaderFabric());
        serverData.registerReloadListener(new StructureLevelingSettingsReloaderFabric());
        serverData.registerReloadListener(new StructureTagLevelingSettingsReloaderFabric());
    }
}
