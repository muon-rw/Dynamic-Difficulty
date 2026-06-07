package dev.muon.dynamic_difficulty.compat.puffish;

import dev.muon.dynamic_difficulty.api.PlayerLevelProvider;
import dev.muon.dynamic_difficulty.config.Configs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.puffish.skillsmod.api.SkillsAPI;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class PuffishSkillsProviderFabric implements PlayerLevelProvider {
    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public int getDisplayPriority() {
        return 10;
    }

    @Override
    public int getPlayerLevel(ServerPlayer player) {
        return SkillsAPI.streamUnlockedCategories(player)
                .mapToInt(category -> category.getSpentPoints(player))
                .sum();
    }
    
    @Override
    public int calculateBonusLevels(List<ServerPlayer> players) {
        if (players.isEmpty()) {
            return 0;
        }
        
        Set<Identifier> blacklist = Configs.SYNC.puffishSkillsTreeBlacklist.get().stream()
                .map(Identifier::tryParse)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        
        int totalLevel = 0;
        for (ServerPlayer player : players) {
            int playerLevel = SkillsAPI.streamUnlockedCategories(player)
                    .filter(category -> !blacklist.contains(category.getId()))
                    .mapToInt(category -> category.getSpentPoints(player))
                    .sum();
            totalLevel += playerLevel;
        }
        
        return totalLevel / players.size();
    }
}
