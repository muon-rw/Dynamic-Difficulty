package dev.muon.dynamic_difficulty.data;

import dev.muon.dynamic_difficulty.DynamicDifficulty;
import dev.muon.dynamic_difficulty.settings.LocationLevelingSettings;

public class StructureLevelingSettingsReloaderFabric extends LocationSettingsReloaderFabric {
    public StructureLevelingSettingsReloaderFabric() {
        super(LocationLevelingSettings.STRUCTURE_RAW_CODEC, "leveling_settings/structures",
                DynamicDifficulty.id("structure_leveling_settings"),
                LocationLevelingSettingsStore.STRUCTURES::loadSettings);
    }
}
