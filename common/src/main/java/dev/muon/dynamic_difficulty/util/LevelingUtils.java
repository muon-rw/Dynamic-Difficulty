package dev.muon.dynamic_difficulty.util;

import dev.muon.dynamic_difficulty.DynamicDifficulty;
import dev.muon.dynamic_difficulty.LevelingEvents;
import dev.muon.dynamic_difficulty.api.BiomeBonus;
import dev.muon.dynamic_difficulty.api.StructureBonus;
import dev.muon.dynamic_difficulty.config.Configs;
import dev.muon.dynamic_difficulty.data.DimensionsLevelingSettingsReloader;
import dev.muon.dynamic_difficulty.settings.DimensionLevelingSettings;
import dev.muon.dynamic_difficulty.settings.LevelingSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
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
            DynamicDifficulty.id("passive_whitelist"));

    // Cached config values to avoid string concatenation on hot path
    private static final Set<String> BLACKLISTED_NAMESPACES = new HashSet<>();
    private static final Set<Identifier> BLACKLISTED_IDS = new HashSet<>();
    private static final Set<String> WHITELISTED_NAMESPACES = new HashSet<>();
    private static final Set<Identifier> WHITELISTED_IDS = new HashSet<>();
    private static boolean configCacheInitialized = false;

    /**
     * Reloads the cached whitelist/blacklist configuration.
     * @see LevelingEvents#onConfigReload()
     */
    public static void reloadConfigCache() {
        synchronized (BLACKLISTED_NAMESPACES) {
            BLACKLISTED_NAMESPACES.clear();
            BLACKLISTED_IDS.clear();
            WHITELISTED_NAMESPACES.clear();
            WHITELISTED_IDS.clear();

            for (String entry : Configs.SYNC.blacklistedMobs.get()) {
                if (entry.endsWith(":*")) {
                    BLACKLISTED_NAMESPACES.add(entry.substring(0, entry.length() - 2));
                } else {
                    try {
                        BLACKLISTED_IDS.add(Identifier.parse(entry));
                    } catch (Exception e) {
                        DynamicDifficulty.LOGGER.warn("Invalid blacklist entry: {}", entry, e);
                    }
                }
            }

            for (String entry : Configs.SYNC.whitelistedMobs.get()) {
                if (entry.endsWith(":*")) {
                    WHITELISTED_NAMESPACES.add(entry.substring(0, entry.length() - 2));
                } else {
                    try {
                        WHITELISTED_IDS.add(Identifier.parse(entry));
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

        if (entity instanceof Animal animal && Configs.SYNC.cancelLevelsForPassives.get()) {
            if (entity.getType().builtInRegistryHolder().is(PASSIVE_WHITELIST)) {
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
        Identifier entityId = EntityType.getKey(entity.getType());
        List<? extends String> blacklist = Configs.CLIENT.hiddenLevelEntities.get();
        return !blacklist.contains(entityId.toString()) &&
                !blacklist.contains(entityId.getNamespace() + ":*");
    }

    /**
     * 2D horizontal distance between two block positions, ignoring Y.
     */
    public static double horizontalDistance(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /**
     * Resolves the effective spawn position for distance-based scaling, honouring
     * the dimension's {@code spawnPosOverride} and falling back to world spawn.
     */
    public static BlockPos getEffectiveSpawnPos(Level level, DimensionLevelingSettings dimSettings) {
        BlockPos override = dimSettings.spawnPosOverride();
        return override != null ? override : level.getRespawnData().pos();
    }

    /**
     * Calculates base level from distance, depth, and height for a specific Y coordinate.
     * Deepness scaling only applies when Y &lt; seaLevel (default 64).
     * Height scaling applies when Y &gt; seaLevel and levelsPerHeight &gt; 0.
     */
    public static int calculateDistanceFactors(
            int seaLevel,
            double yPos,
            double distanceToSpawn,
            LevelingSettings settings) {
        double distanceLevel = distanceToSpawn * settings.levelsPerDistance();

        double depthLevel = 0.0;
        double heightLevel = 0.0;

        if (yPos < seaLevel && settings.levelsPerDeepness() > 0) {
            depthLevel = (seaLevel - yPos) * settings.levelsPerDeepness();
        }

        float levelsPerHeight = settings.levelsPerHeight();
        if (yPos > seaLevel && levelsPerHeight > 0) {
            heightLevel = (yPos - seaLevel) * levelsPerHeight;
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

        Identifier entityId = EntityType.getKey(entity.getType());
        String namespace = entityId.getNamespace();

        // Check blacklist using cached sets (no string concatenation)
        synchronized (BLACKLISTED_NAMESPACES) {
            if (BLACKLISTED_NAMESPACES.contains(namespace) || BLACKLISTED_IDS.contains(entityId)) {
                return false;
            }

            // Check whitelist - if empty in config, allow all
            if (Configs.SYNC.whitelistedMobs.get().isEmpty()) {
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
     * Calculates the base entity level at a given position in a specific level.
     * This includes starting level, distance factors, day scaling, and local difficulty.
     * Random bonus is excluded — it's per-entity and non-deterministic.
     *
     * <p>This is the base level before:
     * <ul>
     *   <li>Player-based scaling (nearby player level bonuses)</li>
     *   <li>Structure bonuses</li>
     *   <li>Biome bonuses</li>
     *   <li>Random variation</li>
     * </ul>
     */
    public static int calculateBaseEntityLevel(ServerLevel level, BlockPos pos) {
        DimensionLevelingSettings dimSettings = DimensionsLevelingSettingsReloader.get(
                level.dimension(),
                level.registryAccess().lookupOrThrow(Registries.DIMENSION));

        BlockPos spawnPos = getEffectiveSpawnPos(level, dimSettings);
        double distanceToSpawn = horizontalDistance(spawnPos, pos);

        int baseLevel = dimSettings.startingLevel();
        baseLevel += calculateDistanceFactors(dimSettings.seaLevel(), pos.getY(), distanceToSpawn, dimSettings);

        long days = level.getOverworldClockTime() / 24000L;
        baseLevel += (int) (days * dimSettings.levelsPerDay());

        net.minecraft.world.DifficultyInstance difficulty = level.getCurrentDifficultyAt(pos);
        baseLevel += (int) (difficulty.getEffectiveDifficulty() * dimSettings.levelsPerLocalDifficulty());

        return Math.max(1, baseLevel);
    }

    /**
     * Calculates the final displayed level (without player bonus) for client display.
     * Accounts for max level cap and bypassing bonuses.
     */
    public static int calculateDisplayedLevel(ServerLevel level, int baseLevel,
                                               StructureBonus structureBonus, BiomeBonus biomeBonus) {
        DimensionLevelingSettings dimensionSettings = DimensionsLevelingSettingsReloader.get(
                level.dimension(),
                level.registryAccess().lookupOrThrow(Registries.DIMENSION));
        return calculateFinalDisplayLevel(baseLevel, structureBonus, biomeBonus, 0, dimensionSettings);
    }
}