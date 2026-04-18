package dev.muon.dynamic_difficulty.player;

import dev.muon.dynamic_difficulty.DynamicDifficulty;
import dev.muon.dynamic_difficulty.api.PlayerLevelProvider;
import dev.muon.dynamic_difficulty.config.Configs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;

import java.util.List;

/**
 * Built-in player level provider that uses Minecraft's built-in playtime statistic.
 * Playtime is tracked automatically by Minecraft and contributes to mob level bonuses.
 * 
 * IMPORTANT: This provider does NOT affect the player's displayed level (shown above head).
 * It only affects mob scaling via calculateBonusLevels() - averaging playtime from all
 * nearby players and converting it to levels based on the configured levelsPerPlaytimeHour value.
 */
public class PlaytimePlayerLevelProvider implements PlayerLevelProvider {
    
    @Override
    public boolean isEnabled() {
        return Configs.SYNC.enablePlaytimeScaling.get();
    }
    
    @Override
    public int getPlayerLevel(ServerPlayer player) {
        // Playtime should NOT affect the player's displayed level (shown above head)
        // It only contributes to mob scaling via calculateBonusLevels()
        return 0;
    }
    
    @Override
    public int calculateBonusLevels(List<ServerPlayer> players) {
        if (players.isEmpty()) {
            return 0;
        }

        Stat<Identifier> playTimeStat = Stats.CUSTOM.get(Stats.PLAY_TIME);
        
        long totalPlaytimeTicks = 0;
        for (ServerPlayer player : players) {
            totalPlaytimeTicks += player.getStats().getValue(playTimeStat);
        }
        
        // Calculate average playtime: total / number of players
        double averagePlaytimeTicks = (double) totalPlaytimeTicks / players.size();
        
        // Convert average playtime to hours: ticks / (20 * 60 * 60) = ticks / 72000
        double averagePlaytimeHours = averagePlaytimeTicks / 72000.0;
        
        // Convert to levels based on config
        int bonusLevels = (int) (averagePlaytimeHours * Configs.SYNC.levelsPerPlaytimeHour.get());
        
        DynamicDifficulty.LOGGER.debug("Playtime provider: {} players, {} ticks average ({} hours), {} bonus levels",
                players.size(), String.format("%.0f", averagePlaytimeTicks), String.format("%.2f", averagePlaytimeHours), bonusLevels);
        
        return bonusLevels;
    }
    
    @Override
    public int getDisplayPriority() {
        // Doesn't really matter, since getPlayerLevel here always returns 0
        return -10;
    }
}

