package dev.muon.dynamic_difficulty.util;

import dev.muon.dynamic_difficulty.api.BiomeBonus;
import dev.muon.dynamic_difficulty.api.StructureBonus;
import dev.muon.dynamic_difficulty.data.DimensionsLevelingSettingsReloader;
import dev.muon.dynamic_difficulty.data.LocationLevelingSettingsStore;
import dev.muon.dynamic_difficulty.settings.DimensionLevelingSettings;
import dev.muon.dynamic_difficulty.settings.LevelingSettings;
import dev.muon.dynamic_difficulty.settings.LocationLevelingSettings;
import net.minecraft.advancements.criterion.LocationPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

public final class LocationBonusUtils {

    private LocationBonusUtils() {} // Utility class

    /**
     * Finds the structure with the highest bonus at the given position.
     *
     * @param level The server level
     * @param pos The block position
     * @param onlyWithBonuses If true, only returns structures whose settings include a non-zero bonus.
     *                        If false, returns any structure (for StructureCredits-like display).
     * @return The structure bonus info (structureId may be null if no structure at position)
     */
    public static StructureBonus getStructureAt(ServerLevel level, BlockPos pos, boolean onlyWithBonuses) {
        Registry<Structure> structureRegistry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        ChunkPos chunkPos = ChunkPos.containing(pos);

        Identifier bestStructureId = null;
        int highestNonBypassing = 0;
        int highestBypassing = 0;
        int highestTotalBonus = 0;

        var structureStarts = level.structureManager().startsForStructure(chunkPos, structure -> true);

        for (StructureStart start : structureStarts) {
            if (!start.isValid()) continue;

            Structure structure = start.getStructure();
            Identifier structureId = structureRegistry.getKey(structure);
            if (structureId == null) continue;

            ResourceKey<Structure> structureKey = ResourceKey.create(Registries.STRUCTURE, structureId);
            Holder<Structure> structureHolder = structureRegistry.getOrThrow(structureKey);

            if (!LocationPredicate.Builder.inStructure(structureHolder).build().matches(level, pos.getX(), pos.getY(), pos.getZ())) {
                continue;
            }

            List<LocationLevelingSettings.RawSettings> matching =
                    LocationLevelingSettingsStore.STRUCTURES.getMatching(structureId, structureRegistry);

            int structureBypassing = 0;
            int structureNonBypassing = 0;
            for (LocationLevelingSettings.RawSettings entry : matching) {
                int bonus = entry.levelBonus();
                if (bonus <= 0) continue;
                if (entry.bypassesCap()) structureBypassing = Math.max(structureBypassing, bonus);
                else structureNonBypassing = Math.max(structureNonBypassing, bonus);
            }

            if (onlyWithBonuses && structureBypassing == 0 && structureNonBypassing == 0) {
                continue;
            }

            int structureTotal = structureBypassing + structureNonBypassing;
            if (structureTotal > highestTotalBonus || (bestStructureId == null && !onlyWithBonuses)) {
                highestTotalBonus = structureTotal;
                bestStructureId = structureId;
            }

            highestBypassing = Math.max(highestBypassing, structureBypassing);
            highestNonBypassing = Math.max(highestNonBypassing, structureNonBypassing);
        }

        return new StructureBonus(bestStructureId, highestNonBypassing, highestBypassing);
    }

    /**
     * Returns the override-style settings (per-field max merged across all matching structures and
     * their tags) at this position, or {@code null} if no structures with settings overlap here.
     * This is the override-chain payload; it does not include the bonus pair contribution.
     */
    @Nullable
    public static LocationLevelingSettings.RawSettings getStructureSettingsAt(ServerLevel level, BlockPos pos) {
        Registry<Structure> structureRegistry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        ChunkPos chunkPos = ChunkPos.containing(pos);

        LocationLevelingSettings.RawSettings result = null;
        var structureStarts = level.structureManager().startsForStructure(chunkPos, structure -> true);

        for (StructureStart start : structureStarts) {
            if (!start.isValid()) continue;

            Structure structure = start.getStructure();
            Identifier structureId = structureRegistry.getKey(structure);
            if (structureId == null) continue;

            ResourceKey<Structure> structureKey = ResourceKey.create(Registries.STRUCTURE, structureId);
            Holder<Structure> structureHolder = structureRegistry.getOrThrow(structureKey);

            if (!LocationPredicate.Builder.inStructure(structureHolder).build().matches(level, pos.getX(), pos.getY(), pos.getZ())) {
                continue;
            }

            LocationLevelingSettings.RawSettings merged = LocationLevelingSettingsStore.STRUCTURES.get(structureId, structureRegistry);
            if (merged != null) {
                result = (result == null) ? merged : result.merge(merged);
            }
        }

        return result;
    }

    /**
     * Gets biome bonus information at the given position.
     * Minecraft caches biome lookups internally, so no additional caching needed.
     *
     * @param level The server level
     * @param pos The block position
     * @return The biome bonus info (biomeId may be null if lookup fails)
     */
    public static BiomeBonus getBiomeAt(ServerLevel level, BlockPos pos) {
        Registry<Biome> biomeRegistry = level.registryAccess().lookupOrThrow(Registries.BIOME);
        Biome biome = level.getBiome(pos).value();
        Optional<ResourceKey<Biome>> optKey = biomeRegistry.getResourceKey(biome);

        if (optKey.isEmpty()) {
            return BiomeBonus.EMPTY;
        }

        Identifier biomeId = optKey.get().identifier();
        List<LocationLevelingSettings.RawSettings> matching =
                LocationLevelingSettingsStore.BIOMES.getMatching(biomeId, biomeRegistry);

        if (matching.isEmpty()) {
            return new BiomeBonus(biomeId, 0, 0);
        }

        int bypassing = 0;
        int nonBypassing = 0;
        for (LocationLevelingSettings.RawSettings entry : matching) {
            int bonus = entry.levelBonus();
            if (bonus <= 0) continue;
            if (entry.bypassesCap()) bypassing = Math.max(bypassing, bonus);
            else nonBypassing = Math.max(nonBypassing, bonus);
        }

        return new BiomeBonus(biomeId, nonBypassing, bypassing);
    }

    /**
     * Returns the override-style settings (per-field max merged across the biome's individual entry
     * and any matching tag entries) at this position, or {@code null} if the biome has no settings.
     */
    @Nullable
    public static LocationLevelingSettings.RawSettings getBiomeSettingsAt(ServerLevel level, BlockPos pos) {
        Registry<Biome> biomeRegistry = level.registryAccess().lookupOrThrow(Registries.BIOME);
        Biome biome = level.getBiome(pos).value();
        Optional<ResourceKey<Biome>> optKey = biomeRegistry.getResourceKey(biome);
        if (optKey.isEmpty()) return null;

        Identifier biomeId = optKey.get().identifier();
        return LocationLevelingSettingsStore.BIOMES.get(biomeId, biomeRegistry);
    }

    /**
     * Resolves the leveling settings chain for a position, including dimension, biome, and
     * structure tiers (no entity tier). Each tier overrides the prior. Useful for "what would
     * spawn here?" queries that aren't tied to a specific entity.
     */
    public static LevelingSettings resolveLocationSettings(ServerLevel level, BlockPos pos) {
        DimensionLevelingSettings dimSettings = DimensionsLevelingSettingsReloader.get(level.dimension());
        LevelingSettings result = dimSettings;

        LocationLevelingSettings.RawSettings biomeRaw = getBiomeSettingsAt(level, pos);
        if (biomeRaw != null) {
            result = biomeRaw.resolve(result);
        }

        LocationLevelingSettings.RawSettings structureRaw = getStructureSettingsAt(level, pos);
        if (structureRaw != null) {
            result = structureRaw.resolve(result);
        }

        return result;
    }
}
