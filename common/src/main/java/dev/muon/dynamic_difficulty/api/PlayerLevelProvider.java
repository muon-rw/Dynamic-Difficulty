package dev.muon.dynamic_difficulty.api;

import dev.muon.dynamic_difficulty.player.PlayerLevelUpdateHandler;
import net.minecraft.server.level.ServerPlayer;
import java.util.List;
import java.util.ArrayList;

/**
 * Interface for mods to provide their own player level calculations to Dynamic Difficulty.
 * Implement this interface and register it via {@link LevelingAPI#registerPlayerLevelProvider(PlayerLevelProvider)}
 * to contribute to the overall player level considered by Dynamic Difficulty when scaling mobs.
 * 
 * IMPORTANT: Player levels from this system are used for TWO distinct purposes:
 * 
 * 1. DISPLAY LEVEL (via {@link #getPlayerLevel(ServerPlayer)}):
 *    - Shown above player heads (name tags)
 *    - Used to color-code mob difficulty indicators (red/yellow/green) relative to player level
 *    - This is the "display level" that players see and use for visual feedback
 *    - NOTE: Mob difficulty color coding uses display levels, NOT mob scaling levels!
 * 
 * 2. MOB SCALING LEVEL (via {@link #calculateBonusLevels(List)}):
 *    - Determines how many bonus levels nearby mobs receive based on player proximity
 *    - This can differ from display level if providers override calculateBonusLevels()
 *    - For example, a provider might exclude certain skill trees from mob scaling while
 *      including them in display level (e.g., non-combat trees shouldn't make mobs harder)
 * 
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
     * This method is used for DISPLAY purposes:
     * - Displaying the player's level above their head (name tags)
     * - Color-coding mob difficulty indicators (red/yellow/green) relative to the player's level
     * 
     * IMPORTANT: This is NOT directly used for mob scaling. Mob scaling uses
     * {@link #calculateBonusLevels(List)} instead, which by default averages this method's
     * results but can be overridden to exclude certain progression from mob difficulty.
     * 
     * For example, a skill system provider might:
     * - Include ALL skill trees in getPlayerLevel() (for display)
     * - Exclude non-combat trees in calculateBonusLevels() (for mob scaling)
     * 
     * This allows players to see their full progression while preventing non-combat
     * skills from making mobs harder.
     *
     * @param player The player to get the level for
     * @return The player's display level from this provider (used for UI and color coding)
     */
    int getPlayerLevel(ServerPlayer player);

    /**
     * Calculates the bonus levels for mob scaling based on the provided list of nearby players.
     * 
     * This method determines how many bonus levels nearby mobs receive based on player proximity.
     * It is SEPARATE from display level calculation and can return different values.
     * 
     * By default, this averages the levels of all nearby players using getPlayerLevel().
     * Providers can override this method to:
     * - Exclude certain progression from mob scaling (e.g., non-combat skill trees)
     * - Implement custom aggregation logic (e.g., use maximum level, sum levels, distance-based weighting)
     * - Apply different filtering than what's shown in display level
     * 
     * EXAMPLE: A skill system provider might:
     * - getPlayerLevel() returns total of ALL skill trees (for display)
     * - calculateBonusLevels() returns total of ONLY combat skill trees (for mob scaling)
     * 
     * This allows players to see their full progression while preventing non-combat
     * skills from affecting mob difficulty.
     * 
     * NOTE: Mob difficulty color coding (red/yellow/green) uses display levels from
     * getPlayerLevel(), NOT the values returned by this method. This method only affects
     * the actual bonus levels applied to mobs.
     *
     * @param players A list of players near the entity being leveled.
     * @return The calculated level bonus based on the players (used for mob scaling only)
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
        provider.onRegistered();
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
     * Sums {@link #calculateBonusLevels(List)} across all enabled providers.
     */
    static int sumBonusLevels(List<ServerPlayer> nearbyPlayers) {
        int total = 0;
        for (PlayerLevelProvider provider : providers) {
            if (provider.isEnabled()) {
                total += provider.calculateBonusLevels(nearbyPlayers);
            }
        }
        return total;
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