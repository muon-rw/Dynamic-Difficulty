package dev.muon.dynamic_difficulty.compat.puffish;

import dev.muon.dynamic_difficulty.api.PlayerLevelProvider;
import dev.muon.dynamic_difficulty.config.Config;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.puffish.skillsmod.api.SkillsAPI;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class PuffishSkillsProvider implements PlayerLevelProvider {
    /**
     * Determines if this provider is active and should contribute to level calculations.
     * For example, return false if a config option disables this provider.
     *
     * @return true if this provider is enabled, false otherwise.
     */
    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public int getDisplayPriority() {
        return 10;
    }

    /**
     * Gets the total spent skill points for a player across ALL unlocked categories.
     * This represents the player's progression level in the Puffish Skills system.
     * 
     * This is used for DISPLAY purposes (shown above player's head, color-coding mob difficulty).
     * All trees are included regardless of blacklist configuration.
     *
     * @param player The player to get the level for
     * @return The total number of spent skill points from all categories
     */
    @Override
    public int getPlayerLevel(ServerPlayer player) {
        return SkillsAPI.streamUnlockedCategories(player)
                .mapToInt(category -> category.getSpentPoints(player))
                .sum();
    }
    
    /**
     * Calculates bonus levels for mob scaling based on nearby players.
     * This excludes blacklisted skill trees from the calculation, so that certain
     * trees (e.g., non-combat trees) don't contribute to mob difficulty scaling.
     * 
     * The default implementation averages levels, but we override to apply blacklist filtering.
     *
     * @param players A list of players near the entity being leveled
     * @return The calculated level bonus based on the players (excluding blacklisted trees)
     */
    @Override
    public int calculateBonusLevels(List<ServerPlayer> players) {
        if (players.isEmpty()) {
            return 0;
        }
        
        Set<ResourceLocation> blacklist = Config.COMMON.puffishSkillsTreeBlacklist.get().stream()
                .map(ResourceLocation::tryParse)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        
        int totalLevel = 0;
        for (ServerPlayer player : players) {
            // Calculate level excluding blacklisted trees for mob scaling
            int playerLevel = SkillsAPI.streamUnlockedCategories(player)
                    .filter(category -> !blacklist.contains(category.getId()))
                    .mapToInt(category -> category.getSpentPoints(player))
                    .sum();
            totalLevel += playerLevel;
        }
        
        return totalLevel / players.size();
    }
}

