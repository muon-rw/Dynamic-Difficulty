package dev.muon.dynamic_difficulty.data;

import dev.muon.dynamic_difficulty.settings.LocationLevelingSettings;

public class BiomeTagLevelingSettingsReloaderNeoForge extends LocationSettingsReloaderNeoForge {
    public BiomeTagLevelingSettingsReloaderNeoForge() {
        super(LocationLevelingSettings.BIOME_RAW_CODEC, "leveling_settings/biome_tags",
                "Biome Tag Leveling Settings",
                LocationLevelingSettingsStore.BIOMES::loadTagSettings);
    }
}
