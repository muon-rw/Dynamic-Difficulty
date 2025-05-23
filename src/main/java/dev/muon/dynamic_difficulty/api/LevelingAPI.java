package dev.muon.dynamic_difficulty.api;

import dev.muon.dynamic_difficulty.leveling.LevelingSystem;
import dev.muon.dynamic_difficulty.util.LevelingUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.Map;

/**
 * Public API for Dynamic Difficulty mod functionality.
 * This is the main entry point for other mods to interact with the leveling system.
 *
 * The leveling system provides automatic level scaling for entities based on:
 * - Distance from world spawn point
 * - Depth below surface
 * - Nearby structure bonuses
 * - Nearby player levels
 * - Random variation
 */
public class LevelingAPI {

    /**
     * Checks if an entity has a level assigned
     * @param entity The entity to check
     * @return true if the entity has a level, false otherwise
     */
    public static boolean hasLevel(Entity entity) {
        return LevelingSystem.hasLevel(entity);
    }

    /**
     * Gets the current level of an entity
     * @param entity The entity to get the level for
     * @return The entity's level, or 0 if it has no level
     */
    public static int getLevel(LivingEntity entity) {
        return LevelingSystem.getLevel(entity);
    }

    /**
     * Sets an entity's level and applies appropriate modifiers
     * @param entity The entity to set the level for
     * @param level The level to set (must be >= 0)
     * @throws IllegalArgumentException if level is negative
     */
    public static void setLevel(LivingEntity entity, int level) {
        if (level < 0) {
            throw new IllegalArgumentException("Level cannot be negative");
        }
        LevelingSystem.setLevel(entity, level);
    }

    /**
     * Checks if an entity type can have levels applied
     * @param entity The entity to check
     * @return true if the entity can have levels, false otherwise
     */
    public static boolean canHaveLevel(Entity entity) {
        return LevelingUtils.canHaveLevel(entity);
    }

    /**
     * Checks if an entity's level should be displayed
     * @param entity The entity to check
     * @return true if the entity's level should be shown, false otherwise
     */
    public static boolean shouldShowLevel(Entity entity) {
        return LevelingUtils.shouldShowLevel(entity);
    }

    /**
     * Calculates what level an entity should be based on various factors
     * @param entity The entity to calculate level for
     * @return The calculated level for the entity
     */
    public static int calculateLevelForEntity(LivingEntity entity) {
        return LevelingSystem.createLevelForEntity(entity);
    }

    /**
     * Applies level-based attribute modifiers to an entity
     * @param entity The entity to apply attributes to
     */
    public static void applyAllLevelAttributes(LivingEntity entity) {
        LevelingSystem.applyAllLevelAttributes(entity);
    }

    /**
     * Gets the attribute modifiers for a given entity's level
     * @param entity The entity to get modifiers for
     * @return Map of attributes to their modifiers
     */
    public static Map<Attribute, AttributeModifier> getLevelAttributes(LivingEntity entity) {
        return LevelingSystem.getAttributeBonuses(entity);
    }

    /**
     * Gets the level contribution from nearby players
     * @param level The server level to check in
     * @param entity The entity to calculate nearby player levels for
     * @return The total level contribution from nearby players
     */
    public static int getLevelsFromNearbyPlayers(ServerLevel level, LivingEntity entity) {
        return LevelingSystem.getLevelsFromNearbyPlayers(level, entity);
    }

    /**
     * Gets the structure level bonus for an entity's current position.
     *
     * @param entity The entity to get the structure bonus for
     * @return The highest level bonus from any structure at the entity's position
     */
    public static int getStructureLevelBonus(LivingEntity entity) {
        return LevelingSystem.getStructureLevelBonus(entity);
    }

    /**
     * Gets the structure level bonus for a given structure.
     *
     * @param structureId The structure's ResourceLocation
     * @return The level bonus configured for the structure
     */
    public static int getStructureLevelBonus(ResourceLocation structureId) {
        return LevelingUtils.getStructureLevelBonus(structureId);
    }

    /**
     * Registers a new provider for calculating player levels
     * @param provider The provider to register
     * @since 1.0.0
     */
    public static void registerPlayerLevelProvider(PlayerLevelProvider provider) {
        PlayerLevelProvider.registerProvider(provider);
    }
}