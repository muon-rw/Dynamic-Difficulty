package dev.muon.dynamic_difficulty.util;

import dev.muon.dynamic_difficulty.DynamicDifficulty;
import dev.muon.dynamic_difficulty.api.BiomeBonus;
import dev.muon.dynamic_difficulty.api.StructureBonus;
import dev.muon.dynamic_difficulty.config.Config;
import dev.muon.dynamic_difficulty.data.BiomeLevelingSettingsReloader;
import dev.muon.dynamic_difficulty.data.DimensionsLevelingSettingsReloader;
import dev.muon.dynamic_difficulty.data.StructureLevelingSettingsReloader;
import dev.muon.dynamic_difficulty.settings.DimensionLevelingSettings;
import dev.muon.dynamic_difficulty.settings.LevelingSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Utility methods for the Dynamic Difficulty mod.
 * These methods handle general-purpose calculations and checks that aren't part of the core leveling system.
 */
public class LevelingUtils {
    private static final TagKey<EntityType<?>> PASSIVE_WHITELIST = TagKey.create(Registries.ENTITY_TYPE,
            DynamicDifficulty.loc("passive_whitelist"));
    
    // Cached config values to avoid string concatenation on hot path
    private static final Set<String> BLACKLISTED_NAMESPACES = new HashSet<>();
    private static final Set<ResourceLocation> BLACKLISTED_IDS = new HashSet<>();
    private static final Set<String> WHITELISTED_NAMESPACES = new HashSet<>();
    private static final Set<ResourceLocation> WHITELISTED_IDS = new HashSet<>();
    private static boolean configCacheInitialized = false;

    /**
     * Reloads the cached whitelist/blacklist configuration.
     * Should be called when config is reloaded.
     */
    public static void reloadConfigCache() {
        synchronized (BLACKLISTED_NAMESPACES) {
            BLACKLISTED_NAMESPACES.clear();
            BLACKLISTED_IDS.clear();
            WHITELISTED_NAMESPACES.clear();
            WHITELISTED_IDS.clear();

            for (String entry : Config.COMMON.blacklistedMobs.get()) {
                if (entry.endsWith(":*")) {
                    BLACKLISTED_NAMESPACES.add(entry.substring(0, entry.length() - 2));
                } else {
                    try {
                        BLACKLISTED_IDS.add(ResourceLocation.parse(entry));
                    } catch (Exception e) {
                        DynamicDifficulty.LOGGER.warn("Invalid blacklist entry: {}", entry, e);
                    }
                }
            }

            for (String entry : Config.COMMON.whitelistedMobs.get()) {
                if (entry.endsWith(":*")) {
                    WHITELISTED_NAMESPACES.add(entry.substring(0, entry.length() - 2));
                } else {
                    try {
                        WHITELISTED_IDS.add(ResourceLocation.parse(entry));
                    } catch (Exception e) {
                        DynamicDifficulty.LOGGER.warn("Invalid whitelist entry: {}", entry, e);
                    }
                }
            }

            configCacheInitialized = true;
        }
    }

    /**
     * Checks if an entity type can have levels applied based on configuration and entity properties
     */
    public static boolean canHaveLevel(Entity entity) {
        if (!(entity instanceof LivingEntity)) return false;
        if (entity.getType() == EntityType.PLAYER) return false;

        if (entity instanceof Animal animal && Config.COMMON.cancelLevelsForPassives.get()) {
            if (entity.getType().is(PASSIVE_WHITELIST)) {
                return true;
            }
            if (animal.getAttribute(Attributes.ATTACK_DAMAGE) == null ||
                    animal.getAttribute(Attributes.ATTACK_DAMAGE).getValue() <= 0) {
                return false;
            }
        }

        return isEntityAllowed(entity);
    }

    /**
     * Checks if an entity's level should be displayed based on configuration
     */
    public static boolean shouldShowLevel(Entity entity) {
        ResourceLocation entityId = EntityType.getKey(entity.getType());
        List<String> blacklist = Config.CLIENT.hiddenLevelEntities.get();
        return !blacklist.contains(entityId.toString()) &&
                !blacklist.contains(entityId.getNamespace() + ":*");
    }

    /**
     * Gets the structure level bonus from datapacks, checking both individual IDs and tags
     * @param structureId The resource location of the structure
     * @param structureRegistry The registry to check tags against
     * @return The level bonus for this structure
     */
    public static int getStructureLevelBonus(ResourceLocation structureId, Registry<Structure> structureRegistry) {
        return StructureLevelingSettingsReloader.getLevelBonus(structureId, structureRegistry);
    }

    /**
     * Gets the biome level bonus from datapacks, checking both individual IDs and tags
     * @param biomeId The resource location of the biome
     * @param biomeRegistry The registry to check tags against
     * @return The level bonus for this biome
     */
    public static int getBiomeLevelBonus(ResourceLocation biomeId, Registry<Biome> biomeRegistry) {
        return BiomeLevelingSettingsReloader.getLevelBonus(biomeId, biomeRegistry);
    }

    /**
     * Calculates base level from distance and depth
     */
    public static int calculateDistanceFactors(
            LivingEntity entity,
            double distanceToSpawn,
            LevelingSettings settings) {
        double distanceLevel = distanceToSpawn * settings.levelsPerDistance();
        double depthLevel = -entity.getY() * settings.levelsPerDeepness();
        return (int) (distanceLevel + depthLevel);
    }

    /**
     * Checks if an entity is allowed to have levels based on whitelist/blacklist configuration.
     * Uses cached config values to avoid string concatenation on hot path.
     */
    private static boolean isEntityAllowed(Entity entity) {
        // Initialize cache on first use if not already done
        if (!configCacheInitialized) {
            reloadConfigCache();
        }

        ResourceLocation entityId = EntityType.getKey(entity.getType());
        String namespace = entityId.getNamespace();

        // Check blacklist using cached sets (no string concatenation)
        synchronized (BLACKLISTED_NAMESPACES) {
            if (BLACKLISTED_NAMESPACES.contains(namespace) || BLACKLISTED_IDS.contains(entityId)) {
                return false;
            }
        }

        // Check whitelist using cached sets
        synchronized (WHITELISTED_NAMESPACES) {
            if (WHITELISTED_NAMESPACES.isEmpty() && WHITELISTED_IDS.isEmpty()) {
                return true; // Empty whitelist means all allowed
            }
            return WHITELISTED_NAMESPACES.contains(namespace) || WHITELISTED_IDS.contains(entityId);
        }
    }

    /**
     * Calculates the base entity level at a given position.
     * This includes starting level, distance factors, day scaling, and local difficulty.
     * Note: Random bonus is excluded as it's per-entity and non-deterministic.
     * 
     * @param player The player (used for dimension access)
     * @param pos The position to calculate level for
     * @return The base level at this position
     */
    public static int calculateBaseEntityLevel(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        ResourceKey<Level> dimension = level.dimension();
        DimensionLevelingSettings dimSettings = DimensionsLevelingSettingsReloader.get(dimension);

        // Get spawn position (may be overridden by dimension settings)
        BlockPos spawnPos = dimSettings.spawnPosOverride() != null ?
                dimSettings.spawnPosOverride() : level.getSharedSpawnPos();
        // Use 2D horizontal distance (ignore Y) for distance-based scaling
        double dx = spawnPos.getX() - pos.getX();
        double dz = spawnPos.getZ() - pos.getZ();
        double distanceToSpawn = Math.sqrt(dx * dx + dz * dz);

        // Starting level from dimension settings
        int baseLevel = dimSettings.startingLevel();

        // Distance and depth factors (using dimension-specific settings)
        int distanceBonus = calculateDistanceFactors(player, distanceToSpawn, dimSettings);
        baseLevel += distanceBonus;

        // Day scaling (from dimension settings, which fall back to config)
        long days = level.getDayTime() / 24000L;
        baseLevel += (int) (days * dimSettings.levelsPerDay());

        // Local difficulty scaling (from dimension settings, which fall back to config)
        net.minecraft.world.DifficultyInstance difficulty = level.getCurrentDifficultyAt(pos);
        float effectiveDifficulty = difficulty.getEffectiveDifficulty();
        baseLevel += (int) (effectiveDifficulty * dimSettings.levelsPerLocalDifficulty());

        // Note: Random bonus is excluded as it's per-entity and non-deterministic

        return Math.max(1, baseLevel);
    }

    /**
     * Calculates the final displayed level (without player bonus) for client display.
     * This accounts for max level cap and bypassing bonuses.
     * 
     * @param player The player (used for dimension/registry access)
     * @param baseLevel The base level
     * @param structureBonus The structure bonus info
     * @param biomeBonus The biome bonus info
     * @return The final displayed level without player bonus
     */
    public static int calculateDisplayedLevel(ServerPlayer player, int baseLevel,
                                               StructureBonus structureBonus, BiomeBonus biomeBonus) {
        ServerLevel level = player.serverLevel();
        DimensionLevelingSettings dimensionSettings = DimensionsLevelingSettingsReloader.get(level.dimension());
        
        return calculateFinalDisplayLevel(baseLevel, structureBonus, biomeBonus, 0, dimensionSettings);
    }

    /**
     * Calculates the final displayed level considering max level cap and bypassing bonuses.
     * This matches the logic in LevelingSystem.createLevelForEntity() for display purposes.
     * 
     * @param baseLevel The base level (starting level + distance + day bonuses)
     * @param structureBonus The structure bonus info
     * @param biomeBonus The biome bonus info
     * @param playerBonus The player bonus (always bypasses cap)
     * @param dimensionSettings The dimension leveling settings
     * @return The final level after applying max level cap and bypassing bonuses
     */
    public static int calculateFinalDisplayLevel(
            int baseLevel,
            StructureBonus structureBonus,
            BiomeBonus biomeBonus,
            int playerBonus,
            DimensionLevelingSettings dimensionSettings) {
        
        // Apply non-bypassing bonuses (before cap)
        int nonBypassingBonuses = structureBonus.nonBypassingBonus() + biomeBonus.nonBypassingBonus();
        int levelWithNonBypassing = baseLevel + nonBypassingBonuses;
        
        // Apply max level cap
        int maxLevel = dimensionSettings.maxLevel();
        if (maxLevel > 1) {
            levelWithNonBypassing = Math.min(levelWithNonBypassing, maxLevel);
        }
        
        // Apply bypassing bonuses (after cap) - player bonus always bypasses
        int bypassingBonuses = structureBonus.bypassingBonus() + biomeBonus.bypassingBonus() + playerBonus;
        
        // Final level
        return Math.max(1, levelWithNonBypassing + bypassingBonuses);
    }
}