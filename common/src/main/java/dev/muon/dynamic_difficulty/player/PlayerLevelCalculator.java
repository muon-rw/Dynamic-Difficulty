package dev.muon.dynamic_difficulty.player;

import dev.muon.dynamic_difficulty.api.PlayerLevelProvider;
import dev.muon.dynamic_difficulty.config.Configs;
import net.minecraft.server.level.ServerPlayer;

import java.util.Comparator;
import java.util.List;

public class PlayerLevelCalculator {

    public static int calculatePlayerDisplayLevel(ServerPlayer player) {
        List<PlayerLevelProvider> enabledProviders = PlayerLevelProvider.getProviders().stream()
                .filter(PlayerLevelProvider::isEnabled)
                .toList();
        
        if (enabledProviders.isEmpty()) {
            return 1;
        }
        
        PlayerLevelDisplayStrategy strategy = Configs.SYNC.playerLevelDisplayStrategy.get();
        
        return switch (strategy) {
            case HIGHEST_PRIORITY -> calculateHighestPriority(player, enabledProviders);
            case MAX -> calculateMax(player, enabledProviders);
            case SUM -> calculateSum(player, enabledProviders);
            case AVERAGE -> calculateAverage(player, enabledProviders);
            case FIRST -> calculateFirst(player, enabledProviders);
        };
    }
    
    private static int calculateHighestPriority(ServerPlayer player, List<PlayerLevelProvider> providers) {
        return providers.stream()
                .max(Comparator.comparingInt(PlayerLevelProvider::getDisplayPriority))
                .map(provider -> provider.getPlayerLevel(player))
                .orElse(1);
    }
    
    private static int calculateMax(ServerPlayer player, List<PlayerLevelProvider> providers) {
        return providers.stream()
                .mapToInt(provider -> provider.getPlayerLevel(player))
                .max()
                .orElse(1);
    }
    
    private static int calculateSum(ServerPlayer player, List<PlayerLevelProvider> providers) {
        return Math.max(1, providers.stream()
                .mapToInt(provider -> provider.getPlayerLevel(player))
                .sum());
    }
    
    private static int calculateAverage(ServerPlayer player, List<PlayerLevelProvider> providers) {
        if (providers.isEmpty()) return 1;
        
        int sum = providers.stream()
                .mapToInt(provider -> provider.getPlayerLevel(player))
                .sum();
        
        return Math.max(1, sum / providers.size());
    }
    
    private static int calculateFirst(ServerPlayer player, List<PlayerLevelProvider> providers) {
        if (providers.isEmpty()) return 1;
        return providers.get(0).getPlayerLevel(player);
    }
}

