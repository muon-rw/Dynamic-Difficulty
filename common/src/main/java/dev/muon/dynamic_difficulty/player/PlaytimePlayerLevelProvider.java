package dev.muon.dynamic_difficulty.player;

import dev.muon.dynamic_difficulty.DynamicDifficulty;
import dev.muon.dynamic_difficulty.api.PlayerLevelProvider;
import dev.muon.dynamic_difficulty.config.Configs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;

import java.util.List;

public class PlaytimePlayerLevelProvider implements PlayerLevelProvider {

    private static final double TICKS_PER_HOUR = 20 * 60 * 60;

    @Override
    public boolean isEnabled() {
        return Configs.SYNC.enablePlaytimeScaling.get();
    }
    
    @Override
    public int getPlayerLevel(ServerPlayer player) {
        // Playtime must not affect the displayed level (shown above head); it only feeds mob scaling via calculateBonusLevels().
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
        
        double averagePlaytimeTicks = (double) totalPlaytimeTicks / players.size();

        double averagePlaytimeHours = averagePlaytimeTicks / TICKS_PER_HOUR;

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

