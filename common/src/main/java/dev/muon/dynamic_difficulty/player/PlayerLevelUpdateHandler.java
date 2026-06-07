package dev.muon.dynamic_difficulty.player;

import dev.muon.dynamic_difficulty.DynamicDifficulty;
import dev.muon.dynamic_difficulty.LevelingSystem;
import dev.muon.dynamic_difficulty.api.LevelingAPI;
import dev.muon.dynamic_difficulty.network.NetworkDispatcher;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

// Not public API; providers use PlayerLevelProvider.requestPlayerLevelUpdate() to trigger updates.
public class PlayerLevelUpdateHandler {
    private static final List<Consumer<ServerPlayer>> callbacks = new ArrayList<>();

    public static void registerCallback(Consumer<ServerPlayer> callback) {
        callbacks.add(callback);
    }

    public static void triggerUpdate(ServerPlayer player) {
        callbacks.forEach(callback -> callback.accept(player));
    }


    public static void updatePlayerLevel(ServerPlayer player) {
        int currentLevel = LevelingAPI.getLevel(player);
        int newLevel = LevelingAPI.getPlayerDisplayLevel(player);

        if (currentLevel != newLevel) {
            LevelingSystem.setLevelAttachment(player, newLevel);
            NetworkDispatcher.syncLevelToAllPlayers(player);
        }
    }

    public static void calculateAndSyncPlayerLevel(ServerPlayer player) {
        int playerLevel = LevelingAPI.getPlayerDisplayLevel(player);
        LevelingSystem.setLevelAttachment(player, playerLevel);
        DynamicDifficulty.LOGGER.debug("Syncing player {} level ({}) to clients",
                player.getName().getString(), playerLevel);
        NetworkDispatcher.syncLevelToAllPlayers(player);
    }

}

