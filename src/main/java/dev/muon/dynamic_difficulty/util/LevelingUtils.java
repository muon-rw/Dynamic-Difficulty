package dev.muon.dynamic_difficulty.util;

import dev.muon.dynamic_difficulty.DynamicDifficulty;
import dev.muon.dynamic_difficulty.config.Config;
import dev.muon.dynamic_difficulty.data.BiomeLevelingSettingsReloader;
import dev.muon.dynamic_difficulty.data.StructureLevelingSettingsReloader;
import dev.muon.dynamic_difficulty.settings.LevelingSettings;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.core.Registry;

import java.util.List;

/**
 * Utility methods for the Dynamic Difficulty mod.
 * These methods handle general-purpose calculations and checks that aren't part of the core leveling system.
 */
public class LevelingUtils {
    private static final TagKey<EntityType<?>> PASSIVE_WHITELIST = TagKey.create(Registries.ENTITY_TYPE,
            DynamicDifficulty.id("passive_whitelist"));

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
    public static int getBiomeLevelBonus(ResourceLocation biomeId, Registry<net.minecraft.world.level.biome.Biome> biomeRegistry) {
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
     * Checks if an entity is allowed to have levels based on whitelist/blacklist configuration
     */
    private static boolean checkWhitelistBlacklist(Entity entity) {
        ResourceLocation entityId = EntityType.getKey(entity.getType());
        String namespace = entityId.getNamespace();

        List<String> blacklist = Config.COMMON.blacklistedMobs.get();
        if (blacklist.contains(namespace + ":*") || blacklist.contains(entityId.toString())) {
            return false;
        }

        List<String> whitelist = Config.COMMON.whitelistedMobs.get();
        if (whitelist.isEmpty()) return true;
        return whitelist.contains(namespace + ":*") || whitelist.contains(entityId.toString());
    }
}