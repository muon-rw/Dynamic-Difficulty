package dev.muon.dynamic_difficulty;

import dev.muon.dynamic_difficulty.api.LevelingAPI;
import dev.muon.dynamic_difficulty.attribute.ModAttributesFabric;
import dev.muon.dynamic_difficulty.compat.puffish.PuffishSkillsProviderFabric;
import dev.muon.dynamic_difficulty.config.Configs;
import dev.muon.dynamic_difficulty.data.*;
import dev.muon.dynamic_difficulty.item.ModItemsFabric;
import dev.muon.dynamic_difficulty.loot.condition.ModLootConditionsFabric;
import dev.muon.dynamic_difficulty.network.NetworkRegistration;
import dev.muon.dynamic_difficulty.platform.PlatformHelperFabric;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;

public class DynamicDifficultyFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        DynamicDifficulty.setHelper(new PlatformHelperFabric());

        DynamicDifficulty.init();

        if (Configs.SYNC.useDefaultLevelingSettings.get()) {
            FabricLoader.getInstance().getModContainer(DynamicDifficulty.MODID).ifPresent(container -> {
                ResourceManagerHelper.registerBuiltinResourcePack(
                        DynamicDifficulty.id("default"),
                        container,
                        Component.literal("Dynamic Difficulty Defaults"),
                        ResourcePackActivationType.DEFAULT_ENABLED
                );
            });
        }
        if (DynamicDifficulty.isModLoaded("puffish_skills")) {
            LevelingAPI.registerPlayerLevelProvider(new PuffishSkillsProviderFabric());
        }

        // Register attachment types early - MUST happen before networking to ensure
        // attachments are registered on clients before sync packets arrive
        EntityLevelAttachmentFabric.registerAttachments();

        ModAttributesFabric.init();
        ModItemsFabric.init();
        ModLootConditionsFabric.init();
        // Loot modifiers handled via LootTableMixin

        NetworkRegistration.register();

        registerDataReloaders();

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
