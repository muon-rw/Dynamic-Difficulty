package dev.muon.dynamic_difficulty.api;

import dev.muon.dynamic_difficulty.DynamicDifficulty;
import dev.muon.dynamic_difficulty.LevelingSystem;
import dev.muon.dynamic_difficulty.player.PlayerLevelCalculator;
import dev.muon.dynamic_difficulty.settings.LocationLevelingSettings;
import dev.muon.dynamic_difficulty.util.LevelingUtils;
import dev.muon.dynamic_difficulty.util.LocationBonusUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Entry point for mod interaction with Dynamic Difficulty leveling.
 * Scaling factors: spawn distance, depth, structures, biomes, nearby players, random variation.
 *
 * <p><b>Side usage:</b> Both = client &amp; server. Server = entity/world modification, sync.
 *
 * @since 1.0.0
 */
public class LevelingAPI {

    /** <b>Side:</b> Both */
    public static boolean hasLevel(@NotNull Entity entity) {
        return LevelingSystem.hasLevel(entity);
    }

    /**
     * Entity level. Mobs get attribute bonuses; players use level for display and nearby-mob scaling only.
     * <p><b>Side:</b> Both
     */
    public static int getLevel(@NotNull LivingEntity entity) {
        return LevelingSystem.getLevel(entity);
    }

    /**
     * Sets entity level, updates attributes, syncs to clients. Not for players.
     * <p><b>Side:</b> Server only
     */
    public static void setAndUpdateLevel(@NotNull LivingEntity entity, int newLevel) {
        LevelingSystem.setAndUpdateLevel(entity, newLevel);
    }

    /**
     * Adds levels (negative to subtract). Min 1. Updates attributes, syncs. Not for players.
     * <p><b>Side:</b> Server only
     */
    public static void addLevels(@NotNull LivingEntity entity, int levelsToAdd) {
        LevelingSystem.addLevels(entity, levelsToAdd);
    }

    /** <b>Side:</b> Both */
    public static boolean canHaveLevel(@NotNull Entity entity) {
        return LevelingUtils.canHaveLevel(entity);
    }

    /** <b>Side:</b> Both */
    public static boolean shouldShowLevel(@NotNull Entity entity) {
        return LevelingUtils.shouldShowLevel(entity);
    }

    /**
     * Calculates level from distance, depth, structures, biomes, nearby players, random variation.
     * <p><b>Side:</b> Server only
     */
    public static int calculateLevelForEntity(@NotNull LivingEntity entity) {
        return LevelingSystem.calculateLevelForEntity(entity);
    }

    /** <b>Side:</b> Server only */
    public static void applyAllLevelAttributes(@NotNull LivingEntity entity) {
        LevelingSystem.applyAllLevelAttributes(entity);
    }

    /** Modifiers for entity's level (read-only, does not apply). <b>Side:</b> Both */
    @NotNull
    public static Map<ResourceKey<Attribute>, AttributeModifier> getLevelAttributes(@NotNull LivingEntity entity) {
        return LevelingSystem.getAttributeBonuses(entity);
    }

    /** <b>Side:</b> Server only */
    public static int getLevelsFromNearbyPlayers(@NotNull ServerLevel level, @NotNull LivingEntity entity) {
        return LevelingSystem.getLevelsFromNearbyPlayers(level, entity);
    }

    /**
     * Base level at entity's position (dimension, spawn distance, depth, day, local difficulty, structures, biomes),
     * after the level cap. Excludes: player scaling, random variation, entity overrides. Returns 1 on client.
     * <p><b>Side:</b> Server only
     */
    public static int getLevelAt(@NotNull LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel serverLevel)) {
            return 1;
        }
        return getLevelAt(serverLevel, entity.blockPosition());
    }

    /**
     * Base level at position. Same factors as {@link #getLevelAt(LivingEntity)}.
     * <p><b>Side:</b> Server only
     */
    public static int getLevelAt(@NotNull ServerLevel level, @NotNull BlockPos pos) {
        LocationBonusUtils.ResolvedLocation location = LocationBonusUtils.resolveLocation(level, pos);
        int baseLevel = LevelingUtils.calculateBaseEntityLevel(level, pos, location.settings(), location.dimension());
        return LevelingUtils.calculateFinalLevel(baseLevel, location.settings(),
                location.structureBonus(), location.biomeBonus(), 0);
    }

    /** {@link StructureBonus#EMPTY} if called on client. <b>Side:</b> Server only */
    @NotNull
    public static StructureBonus getStructureBonus(@NotNull LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel serverLevel)) return StructureBonus.EMPTY;
        return LocationBonusUtils.getStructureAt(serverLevel, entity.blockPosition(), true);
    }

    /** <b>Side:</b> Server only */
    @NotNull
    public static StructureBonus getStructureBonus(@NotNull ServerLevel level, @NotNull BlockPos pos) {
        return LocationBonusUtils.getStructureAt(level, pos, true);
    }

    /** {@link BiomeBonus#EMPTY} if called on client. <b>Side:</b> Server only */
    @NotNull
    public static BiomeBonus getBiomeBonus(@NotNull LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel serverLevel)) return BiomeBonus.EMPTY;
        return LocationBonusUtils.getBiomeAt(serverLevel, entity.blockPosition());
    }

    /** <b>Side:</b> Server only */
    @NotNull
    public static BiomeBonus getBiomeBonus(@NotNull ServerLevel level, @NotNull BlockPos pos) {
        return LocationBonusUtils.getBiomeAt(level, pos);
    }

    /** Register during mod init (e.g. FMLCommonSetupEvent). <b>Side:</b> Both */
    public static void registerPlayerLevelProvider(@NotNull PlayerLevelProvider provider) {
        DynamicDifficulty.LOGGER.info("Registered player level provider: {}",
            provider.getClass().getSimpleName());
        PlayerLevelProvider.registerProvider(provider);
    }

    /**
     * Aggregated display level (name tags, mob difficulty color). May differ from mob scaling
     * bonus; providers use {@link PlayerLevelProvider#calculateBonusLevels} for that.
     * <p><b>Side:</b> Server only
     */
    public static int getPlayerDisplayLevel(@NotNull ServerPlayer player) {
        return PlayerLevelCalculator.calculatePlayerDisplayLevel(player);
    }

    /**
     * Structure settings at position, merged per field (highest wins) across overlapping structures and their tags.
     * Null if none apply.
     * <p><b>Side:</b> Server only
     * @since 1.3.0
     */
    @Nullable
    public static LocationLevelingSettings.RawSettings getStructureSettings(@NotNull ServerLevel level, @NotNull BlockPos pos) {
        return LocationBonusUtils.getStructureSettingsAt(level, pos);
    }

    /**
     * Null if none apply or called on client.
     * <p><b>Side:</b> Server only
     * @since 1.3.0
     */
    @Nullable
    public static LocationLevelingSettings.RawSettings getStructureSettings(@NotNull LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel serverLevel)) return null;
        return LocationBonusUtils.getStructureSettingsAt(serverLevel, entity.blockPosition());
    }

    /**
     * Biome settings at position, merged per field (highest wins) across the biome entry and its tags.
     * Null if none apply.
     * <p><b>Side:</b> Server only
     * @since 1.3.0
     */
    @Nullable
    public static LocationLevelingSettings.RawSettings getBiomeSettings(@NotNull ServerLevel level, @NotNull BlockPos pos) {
        return LocationBonusUtils.getBiomeSettingsAt(level, pos);
    }

    /**
     * Null if none apply or called on client.
     * <p><b>Side:</b> Server only
     * @since 1.3.0
     */
    @Nullable
    public static LocationLevelingSettings.RawSettings getBiomeSettings(@NotNull LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel serverLevel)) return null;
        return LocationBonusUtils.getBiomeSettingsAt(serverLevel, entity.blockPosition());
    }
}