package dev.muon.dynamic_difficulty.data;

import dev.muon.dynamic_difficulty.DynamicDifficulty;
import dev.muon.dynamic_difficulty.settings.LocationLevelingSettings;

public class BiomeTagLevelingSettingsReloaderFabric extends LocationSettingsReloaderFabric {
    public BiomeTagLevelingSettingsReloaderFabric() {
        super(LocationLevelingSettings.BIOME_RAW_CODEC, "leveling_settings/biome_tags",
                DynamicDifficulty.id("biome_tag_leveling_settings"),
                LocationLevelingSettingsStore.BIOMES::loadTagSettings);
    }
}
