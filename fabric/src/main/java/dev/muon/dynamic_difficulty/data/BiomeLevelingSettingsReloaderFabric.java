package dev.muon.dynamic_difficulty.data;

import dev.muon.dynamic_difficulty.DynamicDifficulty;
import dev.muon.dynamic_difficulty.settings.LocationLevelingSettings;

public class BiomeLevelingSettingsReloaderFabric extends LocationSettingsReloaderFabric {
    public BiomeLevelingSettingsReloaderFabric() {
        super(LocationLevelingSettings.BIOME_RAW_CODEC, "leveling_settings/biomes",
                DynamicDifficulty.id("biome_leveling_settings"),
                LocationLevelingSettingsStore.BIOMES::loadSettings);
    }
}
