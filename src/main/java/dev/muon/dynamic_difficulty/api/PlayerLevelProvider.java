package dev.muon.dynamic_difficulty.api;

import dev.muon.dynamic_difficulty.leveling.PlayerLevelUpdateHandler;
import net.minecraft.server.level.ServerPlayer;
import java.util.List;
import java.util.ArrayList;

/**
 * Interface for mods to provide their own player level calculations to Dynamic Difficulty.
 * Implement this interface and register it via {@link LevelingAPI#registerPlayerLevelProvider(PlayerLevelProvider)}
 * to contribute to the overall player level considered by Dynamic Difficulty when scaling mobs.
 * 
 * IMPORTANT: Player levels from this system are used for TWO purposes:
 * 1. DISPLAY: Shown above player heads and used to color-code mob difficulty (red/yellow/green)
 * 2. MOB SCALING: Nearby mobs get bonus levels based on player levels (via calculateBonusLevels)
 * 
 * Player levels do NOT grant attribute bonuses to players themselves. Players do not gain
 * health, damage, or other combat bonuses from their level. This system only affects how
 * difficult nearby mobs become and how levels are displayed for informational purposes.
 */
public interface PlayerLevelProvider {
    List<PlayerLevelProvider> providers = new ArrayList<>();

    /**
     * Determines if this provider is active and should contribute to level calculations.
     * For example, return false if a config option disables this provider.
     *
     * @return true if this provider is enabled, false otherwise.
     */
    boolean isEnabled();

    /**
     * Gets the level for a single player from this provider's system.
     * This is the core method that providers must implement.
     * 
     * This is used for:
     * - Displaying the player's level above their head
     * - Color-coding mob levels relative to the player
     * - Contributing to mob level scaling (via calculateBonusLevels)
     *
     * @param player The player to get the level for
     * @return The player's level from this provider
     */
    int getPlayerLevel(ServerPlayer player);

    /**
     * Calculates the bonus levels for mob scaling based on the provided list of nearby players.
     * 
     * By default, this averages the levels of all nearby players using getPlayerLevel().
     * Providers can override this method to implement custom aggregation logic
     * (e.g., use maximum level, sum levels, or apply distance-based weighting).
     *
     * @param players A list of players near the entity being leveled.
     * @return The calculated level bonus based on the players.
     */
    default int calculateBonusLevels(List<ServerPlayer> players) {
        if (players.isEmpty()) {
            return 0;
        }
        
        int totalLevel = 0;
        for (ServerPlayer player : players) {
            totalLevel += getPlayerLevel(player);
        }
        
        return totalLevel / players.size();
    }

    /**
     * The priority of this provider for display purposes when multiple providers are registered.
     * Higher priority providers will be used preferentially based on the configured display strategy.
     * Default priority is 0. Negative values are allowed.
     *
     * @return The priority value (higher = more important)
     */
    default int getDisplayPriority() {
        return 0;
    }

    /**
     * Called after this provider is registered. Override this to set up event listeners
     * or other initialization that requires triggering player level updates.
     * 
     * Use {@link #requestPlayerLevelUpdate(ServerPlayer)} within your event listeners
     * to trigger recalculation when player data changes.
     */
    default void onRegistered() {
        // Default: no-op, providers can override
    }

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

    /**
     * Requests that the specified player's display level be recalculated and synced to clients.
     * Providers should call this method when they detect a change that would affect the player's level.
     * 
     * Example usage in a provider:
     * <pre>{@code
     * @SubscribeEvent
     * public void onSkillLevelUp(SkillLevelUpEvent event) {
     *     if (event.getPlayer() instanceof ServerPlayer serverPlayer) {
     *         PlayerLevelProvider.requestPlayerLevelUpdate(serverPlayer);
     *     }
     * }
     * }</pre>
     *
     * @param player The player whose level should be recalculated
     */
    static void requestPlayerLevelUpdate(ServerPlayer player) {
        PlayerLevelUpdateHandler.triggerUpdate(player);
    }
}