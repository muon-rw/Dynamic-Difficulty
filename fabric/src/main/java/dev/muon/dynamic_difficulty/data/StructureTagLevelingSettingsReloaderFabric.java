package dev.muon.dynamic_difficulty.data;

import dev.muon.dynamic_difficulty.DynamicDifficulty;
import dev.muon.dynamic_difficulty.settings.LocationLevelingSettings;

public class StructureTagLevelingSettingsReloaderFabric extends LocationSettingsReloaderFabric {
    public StructureTagLevelingSettingsReloaderFabric() {
        super(LocationLevelingSettings.STRUCTURE_RAW_CODEC, "leveling_settings/structure_tags",
                DynamicDifficulty.id("structure_tag_leveling_settings"),
                LocationLevelingSettingsStore.STRUCTURES::loadTagSettings);
    }
}
