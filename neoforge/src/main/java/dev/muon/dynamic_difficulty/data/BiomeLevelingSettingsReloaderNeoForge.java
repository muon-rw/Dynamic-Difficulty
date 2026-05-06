package dev.muon.dynamic_difficulty.data;

import dev.muon.dynamic_difficulty.settings.LocationLevelingSettings;

public class BiomeLevelingSettingsReloaderNeoForge extends LocationSettingsReloaderNeoForge {
    public BiomeLevelingSettingsReloaderNeoForge() {
        super(LocationLevelingSettings.BIOME_RAW_CODEC, "leveling_settings/biomes",
                "Biome Leveling Settings",
                LocationLevelingSettingsStore.BIOMES::loadSettings);
    }
}
