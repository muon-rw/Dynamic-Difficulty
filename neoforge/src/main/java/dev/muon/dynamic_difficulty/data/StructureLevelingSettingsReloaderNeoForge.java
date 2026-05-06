package dev.muon.dynamic_difficulty.data;

import dev.muon.dynamic_difficulty.settings.LocationLevelingSettings;

public class StructureLevelingSettingsReloaderNeoForge extends LocationSettingsReloaderNeoForge {
    public StructureLevelingSettingsReloaderNeoForge() {
        super(LocationLevelingSettings.STRUCTURE_RAW_CODEC, "leveling_settings/structures",
                "Structure Leveling Settings",
                LocationLevelingSettingsStore.STRUCTURES::loadSettings);
    }
}
