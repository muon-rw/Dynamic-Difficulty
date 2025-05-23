package dev.muon.dynamic_difficulty.api;

import net.minecraft.world.entity.player.Player;
import java.util.List;
import java.util.ArrayList;

/**
 * Interface for providing player level calculations from different sources.
 * This system allows mods to contribute their own player level calculations
 * to the Dynamic Difficulty mod's mob scaling system.
 *
 * Usage:
 * 1. To add your own leveling system:
 *    - Implement this interface
 *    - Call {@link #registerProvider(PlayerLevelProvider)} during mod initialization
 *    Example:
 *    {@code
 *    public class MyLevelProvider implements PlayerLevelProvider {
 *        @Override
 *        public boolean isEnabled() {
 *            return true; // or check if your mod/system is active
 *        }
 *
 *        @Override
 *        public int calculateBonusLevels(List<Player> players) {
 *            // Calculate and return level contribution
 *        }
 *    }
 *
 *    // During mod init:
 *    PlayerLevelProvider.registerProvider(new MyLevelProvider());
 *    }
 *
 * 2. To use existing providers:
 *    {@code
 *    int totalLevels = PlayerLevelProvider.getProviders().stream()
 *        .filter(PlayerLevelProvider::isEnabled)
 *        .mapToInt(provider -> provider.calculateLevels(players))
 *        .sum();
 *    }
 */
public interface PlayerLevelProvider {
    List<PlayerLevelProvider> providers = new ArrayList<>();

    /**
     * Checks if this provider should be used for calculations.
     * Implementations should return false if their mod is not loaded
     * or if their leveling system is disabled.
     *
     * @return whether this provider should be used for calculations
     */
    boolean isEnabled();

    /**
     * Calculate the level contribution from a list of players.
     * This method should aggregate the levels from all provided players
     * and return a single value representing their combined contribution.
     *
     * @param players The list of nearby players to consider
     * @return The combined level contribution from these players
     */
    int calculateBonusLevels(List<Player> players);

    /**
     * Registers a new provider to contribute to level calculations.
     * Call this during your mod's initialization phase.
     *
     * @param provider The provider to register
     */
    static void registerProvider(PlayerLevelProvider provider) {
        providers.add(provider);
    }

    /**
     * Gets all registered level providers.
     * Typically used to aggregate levels from all enabled providers.
     *
     * @return List of all registered providers
     */
    static List<PlayerLevelProvider> getProviders() {
        return providers;
    }
}