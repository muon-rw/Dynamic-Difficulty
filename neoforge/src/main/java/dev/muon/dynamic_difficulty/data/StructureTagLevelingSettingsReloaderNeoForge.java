package dev.muon.dynamic_difficulty.data;

import dev.muon.dynamic_difficulty.settings.LocationLevelingSettings;

public class StructureTagLevelingSettingsReloaderNeoForge extends LocationSettingsReloaderNeoForge {
    public StructureTagLevelingSettingsReloaderNeoForge() {
        super(LocationLevelingSettings.STRUCTURE_RAW_CODEC, "leveling_settings/structure_tags",
                "Structure Tag Leveling Settings",
                LocationLevelingSettingsStore.STRUCTURES::loadTagSettings);
    }
}
