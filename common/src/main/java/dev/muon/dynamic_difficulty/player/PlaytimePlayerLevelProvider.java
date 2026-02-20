package dev.muon.dynamic_difficulty.player;

import dev.muon.dynamic_difficulty.DynamicDifficulty;
import dev.muon.dynamic_difficulty.api.PlayerLevelProvider;
import dev.muon.dynamic_difficulty.config.Config;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;

import java.util.List;

/**
 * Uses Minecraft playtime stat for mob scaling only. {@link #getPlayerLevel} returns 0
 * (no display level); playtime contributes via {@link #calculateBonusLevels} using levelsPerPlaytimeHour.
 */
public class PlaytimePlayerLevelProvider implements PlayerLevelProvider {
    
    @Override
    public boolean isEnabled() {
        return Config.COMMON.enablePlaytimeScaling.get();
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

        Stat<ResourceLocation> playTimeStat = Stats.CUSTOM.get(Stats.PLAY_TIME);
        
        long totalPlaytimeTicks = 0;
        for (ServerPlayer player : players) {
            totalPlaytimeTicks += player.getStats().getValue(playTimeStat);
        }
        
        // Calculate average playtime: total / number of players
        double averagePlaytimeTicks = (double) totalPlaytimeTicks / players.size();
        
        // Convert average playtime to hours: ticks / (20 * 60 * 60) = ticks / 72000
        double averagePlaytimeHours = averagePlaytimeTicks / 72000.0;
        
        // Convert to levels based on config
        int bonusLevels = (int) (averagePlaytimeHours * Config.COMMON.levelsPerPlaytimeHour.get());
        
        DynamicDifficulty.LOGGER.debug("Playtime provider: {} players, {} ticks average ({} hours), {} bonus levels",
                players.size(), String.format("%.0f", averagePlaytimeTicks), String.format("%.2f", averagePlaytimeHours), bonusLevels);
        
        return bonusLevels;
    }
    
    @Override
    public int getDisplayPriority() {
        return -10; // Low; getPlayerLevel always returns 0
    }
}

