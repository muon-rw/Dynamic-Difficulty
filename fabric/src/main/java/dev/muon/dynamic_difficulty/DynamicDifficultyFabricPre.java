package dev.muon.dynamic_difficulty;

import dev.muon.dynamic_difficulty.platform.PlatformHelperFabric;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

public class DynamicDifficultyFabricPre implements PreLaunchEntrypoint {
    @Override
    public void onPreLaunch() {
        DynamicDifficulty.setHelper(new PlatformHelperFabric());
    }
}