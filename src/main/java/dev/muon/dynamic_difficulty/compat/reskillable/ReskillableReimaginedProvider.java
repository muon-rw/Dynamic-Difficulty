package dev.muon.dynamic_difficulty.compat.reskillable;

import dev.muon.dynamic_difficulty.DynamicDifficulty;
import dev.muon.dynamic_difficulty.api.PlayerLevelProvider;
import dev.muon.dynamic_difficulty.config.Config;
import net.bandit.reskillable.common.capabilities.SkillModel;
import net.bandit.reskillable.common.commands.skills.Skill;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ReskillableReimaginedProvider implements PlayerLevelProvider {
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
        return 20;
    }

    /**
     * Gets the total skill level for a player across ALL skills.
     * This represents the player's progression level in the Reskillable system.
     * 
     * This is used for DISPLAY purposes (shown above player's head, color-coding mob difficulty).
     * All skills are included regardless of blacklist configuration.
     * 
     * Note: Reskillable skills start at level 1 (not 0), so we subtract 1 from each skill level
     * to get the actual progression above the base level.
     *
     * @param player The player to get the level for
     * @return The sum of all skill levels minus the base level offset
     */
    @Override
    public int getPlayerLevel(ServerPlayer player) {
        SkillModel skillModel = SkillModel.get(player);
        if (skillModel == null) {
            return 0;
        }
        
        return Stream.of(Skill.values())
                .mapToInt(skill -> skillModel.getSkillLevel(skill) - 1) // -1 Because skills start at level 1, not 0
                .sum();
    }
    
    /**
     * Calculates bonus levels for mob scaling based on nearby players.
     * This excludes blacklisted skills from the calculation, so that certain
     * skills (e.g., non-combat skills) don't contribute to mob difficulty scaling.
     * 
     * The default implementation averages levels, but we override to apply blacklist filtering.
     * 
     * Note: Reskillable skills start at level 1 (not 0), so we subtract 1 from each skill level
     * to get the actual progression above the base level.
     *
     * @param players A list of players near the entity being leveled
     * @return The calculated level bonus based on the players (excluding blacklisted skills)
     */
    @Override
    public int calculateBonusLevels(List<ServerPlayer> players) {
        if (players.isEmpty()) {
            return 0;
        }
        
        Set<Skill> blacklist = Config.COMMON.reskillableSkillBlacklist.get().stream()
                .map(skillName -> {
                    try {
                        return Skill.valueOf(skillName.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        String validSkills = Stream.of(Skill.values())
                                .map(Skill::name)
                                .collect(Collectors.joining(", "));
                        DynamicDifficulty.LOGGER.warn("Invalid Reskillable skill name in blacklist: '{}'. Valid skills are: {}", skillName, validSkills);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        
        int totalLevel = 0;
        for (ServerPlayer player : players) {
            SkillModel skillModel = SkillModel.get(player);
            if (skillModel == null) {
                continue;
            }
            
            // Calculate level excluding blacklisted skills for mob scaling
            int playerLevel = Stream.of(Skill.values())
                    .filter(skill -> !blacklist.contains(skill))
                    .mapToInt(skill -> skillModel.getSkillLevel(skill) - 1) // -1 Because skills `getSkillLevel` is not zero-indexed
                    .sum();
            totalLevel += playerLevel;
        }
        
        return totalLevel / players.size();
    }
}
