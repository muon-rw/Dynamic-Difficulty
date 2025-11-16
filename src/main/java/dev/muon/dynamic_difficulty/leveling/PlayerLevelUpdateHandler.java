package dev.muon.dynamic_difficulty.leveling;

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
     * Default implementation: recalculates and syncs if changed.
     */
    public static void handlePlayerLevelUpdate(ServerPlayer player) {
        int currentLevel = LevelingSystem.getLevel(player);
        int newLevel = LevelingAPI.getPlayerDisplayLevel(player);
        
        if (currentLevel != newLevel) {
            LevelingSystem.setLevelTag(player, newLevel);
            NetworkDispatcher.syncLevelToAllPlayers(player);
        }
    }
}

