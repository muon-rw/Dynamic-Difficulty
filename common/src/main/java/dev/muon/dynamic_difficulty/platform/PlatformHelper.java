package dev.muon.dynamic_difficulty.platform;

import dev.muon.dynamic_difficulty.compat.dungeon_difficulty.DungeonDifficultyAttachmentHelper;

public interface PlatformHelper {

    /**
     * Gets the current platform
     *
     * @return An enum value representing the current platform.
     */
    Platform getPlatform();

    /**
     * Checks if a mod with the given id is loaded.
     *
     * @param modId The mod to check if it is loaded.
     * @return True if the mod is loaded, false otherwise.
     */
    boolean isModLoaded(String modId);

    /**
     * Check if the game is currently in a development environment.
     *
     * @return True if in a development environment, false otherwise.
     */
    boolean isDevelopmentEnvironment();

    /**
     * Gets the platform-specific helper for entity level attachments.
     *
     * @return The level attachment helper for this platform.
     */
    LevelAttachmentHelper getLevelAttachmentHelper();

    /**
     * Gets the platform-specific helper for network operations.
     *
     * @return The network helper for this platform.
     */
    NetworkHelper getNetworkHelper();

    /**
     * Gets the platform-specific helper for Dungeon Difficulty data attachments.
     *
     * @return The Dungeon Difficulty attachment helper for this platform.
     */
    DungeonDifficultyAttachmentHelper getDungeonDifficultyAttachmentHelper();
}