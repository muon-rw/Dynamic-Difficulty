package dev.muon.dynamic_difficulty.player;

import dev.muon.dynamic_difficulty.DynamicDifficulty;
import dev.muon.dynamic_difficulty.LevelingSystem;
import dev.muon.dynamic_difficulty.api.LevelingAPI;
import dev.muon.dynamic_difficulty.network.NetworkDispatcher;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Internal handler for player level update callbacks.
 * This is not part of the public API - providers should use 
 * PlayerLevelProvider.requestPlayerLevelUpdate() to trigger updates.
 */
public class PlayerLevelUpdateHandler {
    private static final List<Consumer<ServerPlayer>> callbacks = new ArrayList<>();
    
    /**
     * Register an internal callback for when player level updates are requested.
     * This should only be called by Dynamic Difficulty's initialization code.
     */
    public static void registerCallback(Consumer<ServerPlayer> callback) {
        callbacks.add(callback);
    }
    
    /**
     * Trigger all registered callbacks for a player level update.
     * Called by PlayerLevelProvider.requestPlayerLevelUpdate().
     */
    public static void triggerUpdate(ServerPlayer player) {
        callbacks.forEach(callback -> callback.accept(player));
    }


    /**
     * Recalculates and syncs player level if it has changed
     */
    public static void updatePlayerLevel(ServerPlayer player) {
        int currentLevel = LevelingAPI.getLevel(player);
        int newLevel = LevelingAPI.getPlayerDisplayLevel(player);

        if (currentLevel != newLevel) {
            LevelingSystem.setLevelAttachment(player, newLevel);
            NetworkDispatcher.syncLevelToAllPlayers(player);
        }
    }

    /**
     * Calculates a player's display level from registered providers and syncs it to all clients.
     * This is called automatically on common player events (login, respawn, dimension change, clone, death, join level).
     * Providers can also trigger updates manually via PlayerLevelProvider.requestPlayerLevelUpdate().
     *
     * Note: The calculated level is used for display purposes. Player levels also contribute
     * to mob scaling via PlayerLevelProvider.calculateBonusLevels() when mobs spawn nearby.
     */
    public static void calculateAndSyncPlayerLevel(ServerPlayer player) {
        int playerLevel = LevelingAPI.getPlayerDisplayLevel(player);
        LevelingSystem.setLevelAttachment(player, playerLevel);
        DynamicDifficulty.LOGGER.debug("Syncing player {} level ({}) to clients",
                player.getName().getString(), playerLevel);
        NetworkDispatcher.syncLevelToAllPlayers(player);
    }

}

