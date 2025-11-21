package dev.muon.dynamic_difficulty.api;

import dev.muon.dynamic_difficulty.LevelingSystem;
import dev.muon.dynamic_difficulty.util.LevelingUtils;
import dev.muon.dynamic_difficulty.util.LocationBonusUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.resources.ResourceKey;

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
     * Gets the current level of any living entity, including players.
     * 
     * IMPORTANT DISTINCTION:
     * - For mobs/entities: Level grants attribute bonuses (health, damage, etc.)
     * - For players: Level is DISPLAY ONLY (shown above head, used for color-coding mob difficulty)
     * 
     * Players do NOT receive attribute bonuses from levels. Instead, they use the
     * PlayerLevelProvider system to contribute to nearby mob difficulty scaling.
     * 
     * @param entity The entity to get the level for
     * @return The entity's level, or 1 if it has no level
     */
    public static int getLevel(LivingEntity entity) {
        return LevelingSystem.getLevel(entity);
    }

    /**
     * Sets an entity's level, updates attributes, and syncs to clients.
     * This is the proper way to change an entity's level at runtime (e.g., from items).
     * 
     * @param entity The entity to level up/down (must NOT be a player)
     * @param newLevel The new level to set (must be >= 1)
     * @throws IllegalArgumentException if entity is a player, level is invalid, or entity can't have levels
     */
    public static void setAndUpdateLevel(LivingEntity entity, int newLevel) {
        LevelingSystem.setAndUpdateLevel(entity, newLevel);
    }

    /**
     * Adds levels to an entity (can be negative to subtract).
     * Minimum level is 1. Updates attributes and syncs to clients.
     * 
     * @param entity The entity to level up/down (must NOT be a player)
     * @param levelsToAdd How many levels to add (negative to subtract)
     * @throws IllegalArgumentException if entity is a player or entity can't have levels
     */
    public static void addLevels(LivingEntity entity, int levelsToAdd) {
        LevelingSystem.addLevels(entity, levelsToAdd);
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
    public static Map<ResourceKey<Attribute>, AttributeModifier> getLevelAttributes(LivingEntity entity) {
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
     * Gets the structure bonus for an entity's current position.
     *
     * @param entity The entity to get the structure bonus for
     * @return Structure bonus info including bypassing/non-bypassing bonuses and structure ID
     */
    public static StructureBonus getStructureBonus(LivingEntity entity) {
        return LevelingSystem.getStructureBonus(entity);
    }

    /**
     * Gets the structure bonus at a specific position.
     *
     * @param level The server level
     * @param pos The block position
     * @return Structure bonus info including bypassing/non-bypassing bonuses and structure ID
     */
    public static StructureBonus getStructureBonus(ServerLevel level, BlockPos pos) {
        return LocationBonusUtils.getStructureAt(level, pos, true);
    }

    /**
     * Gets the biome bonus for an entity's current position.
     *
     * @param entity The entity to get the biome bonus for
     * @return Biome bonus info including bypassing/non-bypassing bonuses and biome ID
     */
    public static BiomeBonus getBiomeBonus(LivingEntity entity) {
        return LevelingSystem.getBiomeBonus(entity);
    }

    /**
     * Gets the biome bonus at a specific position.
     *
     * @param level The server level
     * @param pos The block position
     * @return Biome bonus info including bypassing/non-bypassing bonuses and biome ID
     */
    public static BiomeBonus getBiomeBonus(ServerLevel level, BlockPos pos) {
        return LocationBonusUtils.getBiomeAt(level, pos);
    }

    /**
     * Registers a new provider for calculating player levels
     * @param provider The provider to register
     */
    public static void registerPlayerLevelProvider(PlayerLevelProvider provider) {
        dev.muon.dynamic_difficulty.DynamicDifficulty.LOGGER.info("Registered player level provider: {}", 
            provider.getClass().getSimpleName());
        PlayerLevelProvider.registerProvider(provider);
        provider.onRegistered();
    }

    /**
     * Gets the display level for a player based on all registered providers
     * and the configured display strategy. This is used for:
     * - Displaying the player's level above their head
     * - Color-coding mob levels relative to the player
     *
     * @param player The player to get the level for
     * @return The calculated display level
     */
    public static int getPlayerDisplayLevel(net.minecraft.server.level.ServerPlayer player) {
        return dev.muon.dynamic_difficulty.player.PlayerLevelCalculator.calculatePlayerDisplayLevel(player);
    }
}