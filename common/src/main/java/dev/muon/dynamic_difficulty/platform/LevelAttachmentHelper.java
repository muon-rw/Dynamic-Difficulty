package dev.muon.dynamic_difficulty.platform;

import net.minecraft.world.entity.LivingEntity;

/**
 * Platform-agnostic helper for accessing entity level attachments.
 * 
 * NeoForge uses: getData/setData/getExistingDataOrNull
 * Fabric uses: getAttached/setAttached/hasAttached
 */
public interface LevelAttachmentHelper {
    
    /**
     * Gets the level of an entity.
     * @param entity The entity to get the level for
     * @return The entity's level, or 1 if not set
     */
    int getLevel(LivingEntity entity);
    
    /**
     * Sets the level of an entity.
     * @param entity The entity to set the level for
     * @param level The level to set
     */
    void setLevel(LivingEntity entity, int level);
    
    /**
     * Checks if an entity has a level explicitly set (not just the default).
     * @param entity The entity to check
     * @return true if the entity has a level set, false otherwise
     */
    boolean hasLevel(LivingEntity entity);
}
