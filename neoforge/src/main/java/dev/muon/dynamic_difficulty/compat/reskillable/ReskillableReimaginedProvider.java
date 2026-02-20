package dev.muon.dynamic_difficulty.compat.reskillable;

import dev.muon.dynamic_difficulty.DynamicDifficulty;
import dev.muon.dynamic_difficulty.api.PlayerLevelProvider;
import dev.muon.dynamic_difficulty.config.Config;
import net.bandit.reskillable.common.capabilities.SkillModel;
import net.bandit.reskillable.common.skills.Skill;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ReskillableReimaginedProvider implements PlayerLevelProvider {
    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public int getDisplayPriority() {
        return 20;
    }

    /** Sum of all skill levels minus 1 (Reskillable skills start at 1, not 0). All skills included for display. */
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
    
    /** Average of nearby players' levels, excluding blacklisted skills. Skills start at 1, so -1 per skill. */
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
