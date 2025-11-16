package dev.muon.dynamic_difficulty.compat;

import dev.muon.dynamic_difficulty.DynamicDifficulty;
import dev.muon.dynamic_difficulty.api.PlayerLevelProvider;
import net.minecraft.server.level.ServerPlayer;
import net.puffish.skillsmod.api.SkillsAPI;

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
     * Gets the total spent skill points for a player across all unlocked categories.
     * This represents the player's progression level in the Puffish Skills system.
     *
     * @param player The player to get the level for
     * @return The total number of spent skill points
     */
    @Override
    public int getPlayerLevel(ServerPlayer player) {
        return SkillsAPI.streamUnlockedCategories(player)
                .mapToInt(category -> category.getSpentPoints(player))
                .sum();
    }
    
    // The default calculateBonusLevels() implementation will average player levels
    // from the list, which is appropriate for mob scaling.
    // If you wanted custom behavior (e.g., max instead of average), you could override:
    //
    // @Override
    // public int calculateBonusLevels(List<ServerPlayer> players) {
    //     return players.stream()
    //             .mapToInt(this::getPlayerLevel)
    //             .max()
    //             .orElse(0);
    // }
}
