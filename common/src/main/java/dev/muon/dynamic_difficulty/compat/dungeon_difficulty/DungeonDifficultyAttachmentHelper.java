package dev.muon.dynamic_difficulty.compat.dungeon_difficulty;

import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Platform-agnostic helper for accessing Dungeon Difficulty data attachments.
 */
public interface DungeonDifficultyAttachmentHelper {
    
    /**
     * Gets the Dungeon Difficulty data for an entity.
     * @param entity The entity to get data for
     * @return The difficulty data, or null if not set
     */
    @Nullable
    DungeonDifficultyData getData(LivingEntity entity);
    
    /**
     * Sets the Dungeon Difficulty data for an entity.
     * @param entity The entity to set data for
     * @param data The difficulty data to set
     */
    void setData(LivingEntity entity, DungeonDifficultyData data);
    
    /**
     * Checks if an entity has Dungeon Difficulty data set.
     * @param entity The entity to check
     * @return true if the entity has data set, false otherwise
     */
    boolean hasData(LivingEntity entity);
}

