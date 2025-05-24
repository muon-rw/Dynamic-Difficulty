package dev.muon.dynamic_difficulty.api;

import net.minecraft.world.entity.player.Player;
import java.util.List;
import java.util.ArrayList;

/**
 * Interface for mods to provide their own player level calculations to Dynamic Difficulty.
 * Implement this interface and register it via {@link LevelingAPI#registerPlayerLevelProvider(PlayerLevelProvider)}
 * to contribute to the overall player level considered by Dynamic Difficulty when scaling mobs.
 */
public interface PlayerLevelProvider {
    List<PlayerLevelProvider> providers = new ArrayList<>();

    /**
     * Determines if this provider is active and should contribute to level calculations.
     * For example, return false if a required mod is not loaded or a config option disables this provider.
     *
     * @return true if this provider is enabled, false otherwise.
     */
    boolean isEnabled();

    /**
     * Calculates the bonus levels based on the provided list of nearby players.
     * This method should aggregate levels from the players according to the provider's logic
     * and return a single integer representing the level contribution.
     *
     * @param players A list of players near the entity being leveled.
     * @return The calculated level bonus based on the players.
     */
    int calculateBonusLevels(List<Player> players);

    /**
     * Registers a player level provider.
     * This method is called by {@link LevelingAPI#registerPlayerLevelProvider(PlayerLevelProvider)}.
     * Mod authors should use the LevelingAPI method to register their providers.
     *
     * @param provider The provider instance to register.
     */
    static void registerProvider(PlayerLevelProvider provider) {
        providers.add(provider);
    }

    /**
     * Retrieves all registered player level providers.
     * This is used internally by Dynamic Difficulty to aggregate levels from all enabled providers.
     *
     * @return A list of all registered {@link PlayerLevelProvider} instances.
     */
    static List<PlayerLevelProvider> getProviders() {
        return providers;
    }
}