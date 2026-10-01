package dev.muon.dynamic_difficulty.util;

import dev.muon.dynamic_difficulty.DynamicDifficulty;
import dev.muon.dynamic_difficulty.api.BiomeBonus;
import dev.muon.dynamic_difficulty.api.StructureBonus;
import dev.muon.dynamic_difficulty.config.Configs;
import dev.muon.dynamic_difficulty.settings.DimensionLevelingSettings;
import dev.muon.dynamic_difficulty.settings.LevelingSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
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

public class LevelingUtils {
    private static final TagKey<EntityType<?>> PASSIVE_WHITELIST = TagKey.create(Registries.ENTITY_TYPE,
            DynamicDifficulty.loc("passive_whitelist"));

    private static final Set<String> BLACKLISTED_NAMESPACES = new HashSet<>();
    private static final Set<ResourceLocation> BLACKLISTED_IDS = new HashSet<>();
    private static final Set<String> WHITELISTED_NAMESPACES = new HashSet<>();
    private static final Set<ResourceLocation> WHITELISTED_IDS = new HashSet<>();
    private static boolean configCacheInitialized = false;

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
                        BLACKLISTED_IDS.add(ResourceLocation.parse(entry));
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
                        WHITELISTED_IDS.add(ResourceLocation.parse(entry));
                    } catch (Exception e) {
                        DynamicDifficulty.LOGGER.warn("Invalid whitelist entry: {}", entry, e);
                    }
                }
            }

            configCacheInitialized = true;
        }
    }

    public static boolean canHaveLevel(Entity entity) {
        if (!(entity instanceof LivingEntity)) return false;
        if (entity.getType() == EntityType.PLAYER) return false;

        if (entity instanceof Animal animal && Configs.SYNC.cancelLevelsForPassives.get()) {
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

    public static boolean shouldShowLevel(Entity entity) {
        ResourceLocation entityId = EntityType.getKey(entity.getType());
        List<? extends String> blacklist = Configs.CLIENT.hiddenLevelEntities.get();
        return !blacklist.contains(entityId.toString()) &&
                !blacklist.contains(entityId.getNamespace() + ":*");
    }

    public static double horizontalDistance(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    public static BlockPos getEffectiveSpawnPos(Level level, DimensionLevelingSettings dimSettings) {
        BlockPos override = dimSettings.spawnPosOverride();
        return override != null ? override : level.getSharedSpawnPos();
    }

    /** Depth applies only below {@code seaLevel} and height only above it. */
    public static int calculateDistanceFactors(
            int seaLevel,
            double yPos,
            double distanceToSpawn,
            LevelingSettings settings) {
        double distanceLevel = distanceToSpawn * settings.levelsPerDistance();

        double depthLevel = 0.0;
        double heightLevel = 0.0;

        if (yPos < seaLevel && settings.levelsPerDepth() > 0) {
            depthLevel = (seaLevel - yPos) * settings.levelsPerDepth();
        }

        float levelsPerHeight = settings.levelsPerHeight();
        if (yPos > seaLevel && levelsPerHeight > 0) {
            heightLevel = (yPos - seaLevel) * levelsPerHeight;
        }

        return (int) (distanceLevel + depthLevel + heightLevel);
    }

    private static boolean checkWhitelistBlacklist(Entity entity) {
        if (!configCacheInitialized) {
            reloadConfigCache();
        }

        ResourceLocation entityId = EntityType.getKey(entity.getType());
        String namespace = entityId.getNamespace();

        synchronized (BLACKLISTED_NAMESPACES) {
            if (BLACKLISTED_NAMESPACES.contains(namespace) || BLACKLISTED_IDS.contains(entityId)) {
                return false;
            }

            if (Configs.SYNC.whitelistedMobs.get().isEmpty()) {
                return true;
            }

            return WHITELISTED_NAMESPACES.contains(namespace) || WHITELISTED_IDS.contains(entityId);
        }
    }

    /** Excludes structure, biome and player bonuses and the per-entity random bonus. */
    public static int calculateBaseEntityLevel(ServerLevel level, BlockPos pos, LevelingSettings settings,
                                               DimensionLevelingSettings dimSettings) {
        BlockPos spawnPos = getEffectiveSpawnPos(level, dimSettings);

        int baseLevel = settings.startingLevel();
        baseLevel += calculateDistanceFactors(dimSettings.seaLevel(), pos.getY(), horizontalDistance(spawnPos, pos), settings);

        long days = level.getDayTime() / 24000L;
        baseLevel += (int) (days * settings.levelsPerDay());

        baseLevel += (int) (level.getCurrentDifficultyAt(pos).getEffectiveDifficulty() * settings.levelsPerLocalDifficulty());

        return Math.max(1, baseLevel);
    }

    /** Bonuses switched off by the settings' {@code apply_level_bonuses} count as zero. */
    public record BonusSplit(int nonBypassing, int bypassing) {}

    public static BonusSplit splitBonuses(LevelingSettings settings, StructureBonus structureBonus,
                                          BiomeBonus biomeBonus, int playerBonus) {
        int nonBypassing = 0;
        int bypassing = 0;
        if (settings.appliesStructureBonus()) {
            nonBypassing += structureBonus.nonBypassingBonus();
            bypassing += structureBonus.bypassingBonus();
        }
        if (settings.appliesBiomeBonus()) {
            nonBypassing += biomeBonus.nonBypassingBonus();
            bypassing += biomeBonus.bypassingBonus();
        }
        if (settings.appliesPlayerBonus()) {
            if (Configs.SYNC.playerLevelBypassesCap.get()) {
                bypassing += playerBonus;
            } else {
                nonBypassing += playerBonus;
            }
        }
        return new BonusSplit(nonBypassing, bypassing);
    }

    /** A {@code maxLevel} of 0 or 1 leaves the level uncapped. */
    public static int applyLevelCap(int level, int maxLevel) {
        return maxLevel > 1 ? Math.min(level, maxLevel) : level;
    }

    public static int calculateFinalLevel(int baseLevel, LevelingSettings settings,
                                          StructureBonus structureBonus, BiomeBonus biomeBonus, int playerBonus) {
        BonusSplit bonuses = splitBonuses(settings, structureBonus, biomeBonus, playerBonus);
        return Math.max(1, applyLevelCap(baseLevel + bonuses.nonBypassing(), settings.maxLevel()) + bonuses.bypassing());
    }
}
