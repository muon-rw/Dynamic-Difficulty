package dev.muon.dynamic_difficulty.util;

import dev.muon.dynamic_difficulty.DynamicDifficulty;
import dev.muon.dynamic_difficulty.api.BiomeBonus;
import dev.muon.dynamic_difficulty.api.StructureBonus;
import dev.muon.dynamic_difficulty.config.Config;
import dev.muon.dynamic_difficulty.data.DimensionsLevelingSettingsReloader;
import dev.muon.dynamic_difficulty.settings.DimensionLevelingSettings;
import dev.muon.dynamic_difficulty.settings.LevelingSettings;
import net.minecraft.core.BlockPos;
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
            var attackDamage = animal.getAttribute(Attributes.ATTACK_DAMAGE);
            if (attackDamage == null || attackDamage.getValue() <= 0) {
                return false;
            }
        }

        return checkWhitelistBlacklist(entity);
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
     * Calculates base level from distance, depth, and height.
     * Deepness scaling only applies when Y < seaLevel (default 64).
     * Height scaling applies when Y > seaLevel and levelsPerHeight > 0.
     */
    public static int calculateDistanceFactors(
            LivingEntity entity,
            double distanceToSpawn,
            LevelingSettings settings) {
        return calculateDistanceFactors(entity.level().dimension(), entity.getY(), distanceToSpawn, settings);
    }
    
    /**
     * Calculates base level from distance, depth, and height for a specific Y coordinate.
     * Deepness scaling only applies when Y < seaLevel (default 64).
     * Height scaling applies when Y > seaLevel and levelsPerHeight > 0.
     */
    public static int calculateDistanceFactors(
            ResourceKey<Level> dimension,
            double yPos,
            double distanceToSpawn,
            LevelingSettings settings) {
        double distanceLevel = distanceToSpawn * settings.levelsPerDistance();
        
        // Get dimension settings for sea level and height scaling
        DimensionLevelingSettings dimSettings = DimensionsLevelingSettingsReloader.get(dimension);
        int seaLevel = dimSettings.seaLevel();
        float levelsPerHeight = dimSettings.levelsPerHeight();
        
        double depthLevel = 0.0;
        double heightLevel = 0.0;
        
        // Deepness scaling: only applies when below sea level
        if (yPos < seaLevel && settings.levelsPerDeepness() > 0) {
            double depthBelowSea = seaLevel - yPos;
            depthLevel = depthBelowSea * settings.levelsPerDeepness();
        }
        
        // Height scaling: only applies when above sea level
        if (yPos > seaLevel && levelsPerHeight > 0) {
            double heightAboveSea = yPos - seaLevel;
            heightLevel = heightAboveSea * levelsPerHeight;
        }
        
        return (int) (distanceLevel + depthLevel + heightLevel);
    }

    /**
     * Checks if an entity is allowed to have levels based on whitelist/blacklist configuration.
     * Uses cached config values to avoid string concatenation on hot path.
     */
    private static boolean checkWhitelistBlacklist(Entity entity) {
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

            // Check whitelist - if empty in config, allow all
            if (Config.COMMON.whitelistedMobs.get().isEmpty()) {
                return true;
            }

            return WHITELISTED_NAMESPACES.contains(namespace) || WHITELISTED_IDS.contains(entityId);
        }
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

    /**
     * Calculates the base entity level at a given position.
     * This includes starting level, distance factors, and day scaling.
     * Note: Random bonus is excluded as it's per-entity and non-deterministic.
     * 
     * @param player The player (used for dimension access)
     * @param pos The position to calculate level for
     * @return The base level at this position
     */
    public static int calculateBaseEntityLevel(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        // Get dimension-specific settings (or fall back to global config)
        ResourceKey<Level> dimension = level.dimension();
        DimensionLevelingSettings dimSettings = DimensionsLevelingSettingsReloader.get(dimension, level.registryAccess().registryOrThrow(Registries.DIMENSION));

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
        // Use BlockPos Y coordinate for position-based calculation
        int distanceBonus = calculateDistanceFactors(dimension, pos.getY(), distanceToSpawn, dimSettings);
        baseLevel += distanceBonus;

        // Day scaling (global config, not dimension-specific)
        long days = level.getDayTime() / 24000L;
        baseLevel += (int) (days * Config.COMMON.levelsPerDay.get());

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
        var dimensionSettings = DimensionsLevelingSettingsReloader.get(level.dimension(), level.registryAccess().registryOrThrow(Registries.DIMENSION));
        
        return calculateFinalDisplayLevel(baseLevel, structureBonus, biomeBonus, 0, dimensionSettings);
    }
}