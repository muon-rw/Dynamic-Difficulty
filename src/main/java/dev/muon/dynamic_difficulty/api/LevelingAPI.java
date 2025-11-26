package dev.muon.dynamic_difficulty.api;

import dev.muon.dynamic_difficulty.DynamicDifficulty;
import dev.muon.dynamic_difficulty.LevelingSystem;
import dev.muon.dynamic_difficulty.util.PlayerLevelCalculator;
import dev.muon.dynamic_difficulty.util.LevelingUtils;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;

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
 * 
 * <p><b>Side Usage:</b>
 * <ul>
 *   <li><b>Both:</b> Methods that work on both client and server (typically read-only operations)</li>
 *   <li><b>Server:</b> Methods that require server-side logic (entity modification, world access)</li>
 *   <li><b>Client:</b> Methods that require client-side logic (currently none in this API)</li>
 * </ul>
 */
public class LevelingAPI {

    /**
     * Checks if an entity has a level assigned
     * 
     * <p><b>Side:</b> Both (client & server)
     * 
     * @param entity The entity to check
     * @return true if the entity has a level, false otherwise
     */
    public static boolean hasLevel(Entity entity) {
        return LevelingSystem.hasLevel(entity);
    }

    /**
     * Gets the current level of any living entity, including players.
     * 
     * <p><b>Side:</b> Both (client & server)
     * 
     * <p><b>IMPORTANT DISTINCTION:</b>
     * <ul>
     *   <li>For mobs/entities: Level grants attribute bonuses (health, damage, etc.)</li>
     *   <li>For players: Level is used for display (shown above head, color-coding mob difficulty)
     *       and for calculating mob level bonuses based on nearby player proximity.</li>
     * </ul>
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
     * <p><b>Side:</b> Server only
     * <p>Requires server-side world access to modify entity attributes and sync to clients.
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
     * <p><b>Side:</b> Server only
     * <p>Requires server-side world access to modify entity attributes and sync to clients.
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
     * 
     * <p><b>Side:</b> Both (client & server)
     * 
     * @param entity The entity to check
     * @return true if the entity can have levels, false otherwise
     */
    public static boolean canHaveLevel(Entity entity) {
        return LevelingUtils.canHaveLevel(entity);
    }

    /**
     * Checks if an entity's level should be displayed
     * 
     * <p><b>Side:</b> Both (client & server)
     * <p>Typically used on client-side for rendering decisions, but safe to call on server.
     * 
     * @param entity The entity to check
     * @return true if the entity's level should be shown, false otherwise
     */
    public static boolean shouldShowLevel(Entity entity) {
        return LevelingUtils.shouldShowLevel(entity);
    }

    /**
     * Calculates what level an entity should be based on various factors.
     * This includes distance, depth, structure/biome bonuses, nearby players, and random variation.
     * 
     * <p><b>Side:</b> Server only
     * <p>Requires server-side world access for structure detection, biome checks, and player proximity.
     * Also uses random number generation which should be deterministic on server.
     * 
     * @param entity The entity to calculate level for
     * @return The calculated level for the entity
     */
    public static int calculateLevelForEntity(LivingEntity entity) {
        return LevelingSystem.createLevelForEntity(entity);
    }

    /**
     * Applies level-based attribute modifiers to an entity
     * 
     * <p><b>Side:</b> Server only
     * <p>Modifies entity attributes, which must be done on the server.
     * 
     * @param entity The entity to apply attributes to
     */
    public static void applyAllLevelAttributes(LivingEntity entity) {
        LevelingSystem.applyAllLevelAttributes(entity);
    }

    /**
     * Gets the attribute modifiers for a given entity's level.
     * This returns the modifiers that would be applied, but does not apply them.
     * 
     * <p><b>Side:</b> Both (client & server)
     * <p>Read-only operation that queries leveling settings.
     * 
     * @param entity The entity to get modifiers for
     * @return Map of attributes to their modifiers
     */
    public static Map<ResourceKey<Attribute>, AttributeModifier> getLevelAttributes(LivingEntity entity) {
        return LevelingSystem.getAttributeBonuses(entity);
    }

    /**
     * Gets the level contribution from nearby players
     * 
     * <p><b>Side:</b> Server only
     * <p>Requires ServerLevel parameter to search for nearby players and access player level data.
     * 
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
     * <p><b>Side:</b> Server only
     * <p>Requires server-side world access to check structure manager for structures at position.
     *
     * @param entity The entity to get the structure bonus for
     * @return The highest level bonus from any structure at the entity's position
     */
    public static int getStructureLevelBonus(LivingEntity entity) {
        return LevelingSystem.getStructureLevelBonus(entity);
    }

    /**
     * Gets the structure level bonus for a given structure from datapack settings.
     * 
     * <p><b>Side:</b> Both (client & server)
     * <p>Read-only operation that queries datapack settings. Requires a registry to check structure tags.
     *
     * @param structureId The structure's ResourceLocation
     * @param structureRegistry The structure registry (can be obtained from world.registryAccess())
     * @return The level bonus configured for the structure
     */
    public static int getStructureLevelBonus(ResourceLocation structureId, Registry<Structure> structureRegistry) {
        return LevelingUtils.getStructureLevelBonus(structureId, structureRegistry);
    }

    /**
     * Gets the biome level bonus for an entity's current position.
     * 
     * <p><b>Side:</b> Server only
     * <p>Requires server-side world access to get biome at entity's position.
     *
     * @param entity The entity to get the biome bonus for
     * @return The level bonus from the biome at the entity's position
     */
    public static int getBiomeLevelBonus(LivingEntity entity) {
        return LevelingSystem.getBiomeLevelBonus(entity);
    }

    /**
     * Gets the biome level bonus for a given biome from datapack settings.
     * 
     * <p><b>Side:</b> Both (client & server)
     * <p>Read-only operation that queries datapack settings. Requires a registry to check biome tags.
     *
     * @param biomeId The biome's ResourceLocation
     * @param biomeRegistry The biome registry (can be obtained from world.registryAccess())
     * @return The level bonus configured for the biome
     */
    public static int getBiomeLevelBonus(ResourceLocation biomeId, Registry<Biome> biomeRegistry) {
        return LevelingUtils.getBiomeLevelBonus(biomeId, biomeRegistry);
    }

    /**
     * Registers a new provider for calculating player levels.
     * Should be called during mod initialization (e.g., in FMLCommonSetupEvent).
     * 
     * <p><b>Side:</b> Both (typically called during common setup)
     * <p>Registration is shared between client and server, but providers are typically
     * registered during mod initialization before side distinction matters.
     * 
     * @param provider The provider to register
     */
    public static void registerPlayerLevelProvider(PlayerLevelProvider provider) {
        DynamicDifficulty.LOGGER.info("Registered player level provider: {}",
            provider.getClass().getSimpleName());
        PlayerLevelProvider.registerProvider(provider);
    }

    /**
     * Gets the display level for a player based on all registered providers
     * and the configured display strategy. This aggregated level is used for:
     * - Displaying the player's level above their head
     * - Color-coding mob levels relative to the player
     * 
     * <p><b>Side:</b> Server only
     * <p>Requires ServerPlayer parameter to access player data and calculate from providers.
     * The calculated level is synced to clients automatically.
     * 
     * <p><b>Note:</b> The display level returned by this method may not equal the player bonus
     * applied to mob scaling. For mob scaling, providers use calculateBonusLevels(),
     * which defaults to averaging player levels but can be overridden by providers
     * to implement custom aggregation logic (e.g., distance-based weighting, maximum level, etc.).
     * Providers using the default averaging behavior will have identical display and scaling values.
     * The display strategy only affects what level is shown to players, not how mobs are scaled.
     *
     * @param player The player to get the level for
     * @return The calculated display level
     */
    public static int getPlayerDisplayLevel(ServerPlayer player) {
        return PlayerLevelCalculator.calculatePlayerDisplayLevel(player);
    }
}