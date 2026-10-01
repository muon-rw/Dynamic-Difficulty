package dev.muon.dynamic_difficulty;

import dev.muon.dynamic_difficulty.api.LevelingAPI;
import dev.muon.dynamic_difficulty.attribute.ModAttributesFabric;
import dev.muon.dynamic_difficulty.compat.puffish.PuffishSkillsProviderFabric;
import dev.muon.dynamic_difficulty.config.Configs;
import dev.muon.dynamic_difficulty.data.LevelingSettingsReloaderFabric;
import dev.muon.dynamic_difficulty.data.LevelingSettingsSource;
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
                        DynamicDifficulty.loc("default"),
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
        EntityLevelAttachmentFabric.init();
        if (DynamicDifficulty.isModLoaded("dungeon_difficulty")) {
            dev.muon.dynamic_difficulty.compat.dungeon_difficulty.DungeonDifficultyAttachmentFabric.init();
        }

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
        LevelingSettingsSource.ALL.forEach(source ->
                serverData.registerReloadListener(new LevelingSettingsReloaderFabric(source)));
    }
}
