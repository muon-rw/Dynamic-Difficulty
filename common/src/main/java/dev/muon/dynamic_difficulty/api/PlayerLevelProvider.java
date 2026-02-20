package dev.muon.dynamic_difficulty.api;

import dev.muon.dynamic_difficulty.player.PlayerLevelUpdateHandler;
import net.minecraft.server.level.ServerPlayer;
import java.util.List;
import java.util.ArrayList;

/**
 * Provides player level calculations for Dynamic Difficulty. Register via
 * {@link LevelingAPI#registerPlayerLevelProvider(PlayerLevelProvider)}.
 *
 * <p><b>Two distinct uses:</b>
 * <ul>
 *   <li><b>Display</b> ({@link #getPlayerLevel}): Shown above heads, used for mob difficulty color coding (red/yellow/green)</li>
 *   <li><b>Mob scaling</b> ({@link #calculateBonusLevels}): Bonus levels applied to nearby mobs. Can differ from display (e.g., exclude non-combat skills)</li>
 * </ul>
 * Player levels do not grant attribute bonuses to players.
 */
public interface PlayerLevelProvider {
    List<PlayerLevelProvider> providers = new ArrayList<>();

    /** Whether this provider is active and should contribute. */
    boolean isEnabled();

    /**
     * Display level for a player (name tags, mob difficulty color coding).
     * Mob scaling uses {@link #calculateBonusLevels} instead; override that to exclude
     * non-combat progression from mob difficulty.
     */
    int getPlayerLevel(ServerPlayer player);

    /**
     * Bonus levels for mob scaling from nearby players. Default: average of {@link #getPlayerLevel}.
     * Override to exclude non-combat progression or use custom aggregation (max, distance-weighted, etc.).
     * Mob difficulty color coding uses display levels, not this.
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

    /** Display priority when multiple providers exist. Higher = preferred. Default 0. */
    default int getDisplayPriority() {
        return 0;
    }

    /** Called after registration. Override to set up listeners; call {@link #requestPlayerLevelUpdate} when data changes. */
    default void onRegistered() {
        // Default: no-op, providers can override
    }

    /** Called by {@link LevelingAPI#registerPlayerLevelProvider}. Mods should use the API. */
    static void registerProvider(PlayerLevelProvider provider) {
        providers.add(provider);
        provider.onRegistered();
    }

    static List<PlayerLevelProvider> getProviders() {
        return providers;
    }

    /**
     * Triggers recalculation and sync of the player's display level. Call when provider data changes.
     * <pre>{@code
     * @SubscribeEvent
     * public void onSkillLevelUp(SkillLevelUpEvent e) {
     *     if (e.getPlayer() instanceof ServerPlayer sp) {
     *         PlayerLevelProvider.requestPlayerLevelUpdate(sp);
     *     }
     * }
     * }</pre>
     */
    static void requestPlayerLevelUpdate(ServerPlayer player) {
        PlayerLevelUpdateHandler.triggerUpdate(player);
    }
}