package dev.muon.dynamic_difficulty;

import com.mojang.logging.LogUtils;
import dev.muon.dynamic_difficulty.api.LevelingAPI;
import dev.muon.dynamic_difficulty.player.PlayerLevelUpdateHandler;
import dev.muon.dynamic_difficulty.player.PlaytimePlayerLevelProvider;
import dev.muon.dynamic_difficulty.platform.PlatformHelper;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;

public class DynamicDifficulty {
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final String MODID = "dynamic_difficulty";

    private static PlatformHelper helper;

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(DynamicDifficulty.MODID, path);
    }

    public static void init() {
        // Register player level update callback
        PlayerLevelUpdateHandler.registerCallback(PlayerLevelUpdateHandler::updatePlayerLevel);

        // Register built-in playtime provider (always available)
        LevelingAPI.registerPlayerLevelProvider(new PlaytimePlayerLevelProvider());
        
        // Mod-specific providers are registered in platform-specific init code
    }

    public static boolean isModLoaded(String modId) {
        if (helper != null) {
            return helper.isModLoaded(modId);
    }
        return false;
    }

    public static PlatformHelper getHelper() {
        return helper;
    }

    public static void setHelper(PlatformHelper helper) {
        DynamicDifficulty.helper = helper;
    }
}
