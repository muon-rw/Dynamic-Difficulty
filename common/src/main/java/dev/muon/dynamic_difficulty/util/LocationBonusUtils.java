package dev.muon.dynamic_difficulty.util;

import dev.muon.dynamic_difficulty.api.BiomeBonus;
import dev.muon.dynamic_difficulty.api.StructureBonus;
import dev.muon.dynamic_difficulty.data.DimensionLevelingSettingsStore;
import dev.muon.dynamic_difficulty.data.LocationLevelingSettingsStore;
import dev.muon.dynamic_difficulty.mixin.ChunkMapInvoker;
import dev.muon.dynamic_difficulty.settings.DimensionLevelingSettings;
import dev.muon.dynamic_difficulty.settings.LevelingSettings;
import dev.muon.dynamic_difficulty.settings.LocationLevelingSettings.RawSettings;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class LocationBonusUtils {

    private LocationBonusUtils() {}

    /**
     * The dimension, biome and structure tiers of the settings chain at a position, without the entity tier.
     * {@code structureBonus} also names a structure that has no bonus.
     */
    public record ResolvedLocation(
            DimensionLevelingSettings dimension,
            LevelingSettings settings,
            @Nullable RawSettings biomeSettings,
            @Nullable RawSettings structureSettings,
            BiomeBonus biomeBonus,
            StructureBonus structureBonus) {}

    private record Match(ResourceLocation id, List<RawSettings> entries) {}

    private record BonusPair(int nonBypassing, int bypassing) {
        static BonusPair highestPerBucket(List<RawSettings> entries) {
            int nonBypassing = 0;
            int bypassing = 0;
            for (RawSettings entry : entries) {
                int bonus = entry.levelBonus();
                if (bonus <= 0) continue;
                if (entry.bypassesCap()) bypassing = Math.max(bypassing, bonus);
                else nonBypassing = Math.max(nonBypassing, bonus);
            }
            return new BonusPair(nonBypassing, bypassing);
        }

        int total() {
            return nonBypassing + bypassing;
        }
    }

    public static ResolvedLocation resolveLocation(ServerLevel level, BlockPos pos) {
        DimensionLevelingSettings dimension = DimensionLevelingSettingsStore.get(level);
        Match biome = biomeAt(level, pos);
        List<Match> structures = structuresAt(level, pos);

        RawSettings biomeSettings = biome == null ? null : merge(biome.entries());
        RawSettings structureSettings = mergeAll(structures);

        LevelingSettings settings = dimension;
        if (biomeSettings != null) {
            settings = biomeSettings.resolve(settings);
        }
        if (structureSettings != null) {
            settings = structureSettings.resolve(settings);
        }

        return new ResolvedLocation(dimension, settings, biomeSettings, structureSettings,
                biomeBonus(biome), structureBonus(structures, false));
    }

    /**
     * @param onlyWithBonuses If false, a structure without a bonus can still be reported (for title display).
     */
    public static StructureBonus getStructureAt(ServerLevel level, BlockPos pos, boolean onlyWithBonuses) {
        return structureBonus(structuresAt(level, pos), onlyWithBonuses);
    }

    /** Merged per field (highest wins) across every structure overlapping the position and their tags. */
    @Nullable
    public static RawSettings getStructureSettingsAt(ServerLevel level, BlockPos pos) {
        return mergeAll(structuresAt(level, pos));
    }

    public static BiomeBonus getBiomeAt(ServerLevel level, BlockPos pos) {
        return biomeBonus(biomeAt(level, pos));
    }

    /** Merged per field (highest wins) across the biome's own entry and its tags. */
    @Nullable
    public static RawSettings getBiomeSettingsAt(ServerLevel level, BlockPos pos) {
        Match biome = biomeAt(level, pos);
        return biome == null ? null : merge(biome.entries());
    }

    @Nullable
    private static Match biomeAt(ServerLevel level, BlockPos pos) {
        Registry<Biome> biomeRegistry = level.registryAccess().registryOrThrow(Registries.BIOME);
        return biomeRegistry.getResourceKey(level.getBiome(pos).value())
                .map(key -> new Match(key.location(),
                        LocationLevelingSettingsStore.BIOMES.getMatching(key.location(), biomeRegistry)))
                .orElse(null);
    }

    private static List<Match> structuresAt(ServerLevel level, BlockPos pos) {
        ChunkAccess chunk = loadedChunk(level, new ChunkPos(pos), ChunkStatus.STRUCTURE_REFERENCES);
        if (chunk == null) {
            return List.of();
        }
        Registry<Structure> structureRegistry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        List<Match> matches = new ArrayList<>();
        chunk.getAllReferences().forEach((structure, startChunks) -> {
            ResourceLocation structureId = structureRegistry.getKey(structure);
            if (structureId != null && hasLoadedPieceAt(level, pos, structure, startChunks)) {
                matches.add(new Match(structureId, LocationLevelingSettingsStore.STRUCTURES.getMatching(structureId, structureRegistry)));
            }
        });
        return matches;
    }

    private static boolean hasLoadedPieceAt(ServerLevel level, BlockPos pos, Structure structure, LongSet startChunks) {
        StructureManager structureManager = level.structureManager();
        for (long startChunkPos : startChunks) {
            ChunkAccess startChunk = loadedChunk(level, new ChunkPos(startChunkPos), ChunkStatus.STRUCTURE_STARTS);
            if (startChunk == null) continue;
            StructureStart start = startChunk.getStartForStructure(structure);
            if (start != null && start.isValid() && structureManager.structureHasPieceAt(pos, start)) {
                return true;
            }
        }
        return false;
    }

    // Vanilla's structure lookups load or generate missing chunks, which can hang the server tick.
    @Nullable
    private static ChunkAccess loadedChunk(ServerLevel level, ChunkPos pos, ChunkStatus status) {
        ChunkHolder holder = ((ChunkMapInvoker) level.getChunkSource().chunkMap).dynamic_difficulty$getVisibleChunkIfPresent(pos.toLong());
        return holder == null ? null : holder.getChunkIfPresent(status);
    }

    private static StructureBonus structureBonus(List<Match> structures, boolean onlyWithBonuses) {
        ResourceLocation bestStructureId = null;
        int highestTotal = 0;
        int nonBypassing = 0;
        int bypassing = 0;

        for (Match structure : structures) {
            BonusPair bonus = BonusPair.highestPerBucket(structure.entries());
            if (onlyWithBonuses && bonus.total() == 0) continue;

            if (bestStructureId == null || bonus.total() > highestTotal) {
                bestStructureId = structure.id();
                highestTotal = bonus.total();
            }
            nonBypassing = Math.max(nonBypassing, bonus.nonBypassing());
            bypassing = Math.max(bypassing, bonus.bypassing());
        }

        return new StructureBonus(bestStructureId, nonBypassing, bypassing);
    }

    private static BiomeBonus biomeBonus(@Nullable Match biome) {
        if (biome == null) {
            return BiomeBonus.EMPTY;
        }
        BonusPair bonus = BonusPair.highestPerBucket(biome.entries());
        return new BiomeBonus(biome.id(), bonus.nonBypassing(), bonus.bypassing());
    }

    @Nullable
    private static RawSettings mergeAll(List<Match> matches) {
        return merge(matches.stream().flatMap(match -> match.entries().stream()).toList());
    }

    @Nullable
    private static RawSettings merge(List<RawSettings> entries) {
        return entries.stream().reduce(RawSettings::merge).orElse(null);
    }
}
