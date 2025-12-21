package dev.muon.dynamic_difficulty.util;

import dev.muon.dynamic_difficulty.api.BiomeBonus;
import dev.muon.dynamic_difficulty.api.StructureBonus;
import dev.muon.dynamic_difficulty.data.BiomeLevelingSettingsReloader;
import dev.muon.dynamic_difficulty.data.StructureLevelingSettingsReloader;
import dev.muon.dynamic_difficulty.settings.BiomeBonusSettings;
import dev.muon.dynamic_difficulty.settings.StructureBonusSettings;
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

import java.util.Optional;

public final class LocationBonusUtils {
    
    private LocationBonusUtils() {} // Utility class
    
    /**
     * Finds the structure with the highest bonus at the given position.
     * 
     * @param level The server level
     * @param pos The block position
     * @param onlyWithBonuses If true, only returns structures with configured bonuses.
     *                        If false, returns any structure (for StructureCredits-like display).
     * @return The structure bonus info (structureId may be null if no structure at position)
     */
    public static StructureBonus getStructureAt(ServerLevel level, BlockPos pos, boolean onlyWithBonuses) {
        Registry<Structure> structureRegistry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        ChunkPos chunkPos = new ChunkPos(pos);
        
        Identifier bestStructureId = null;
        int highestNonBypassing = 0;
        int highestBypassing = 0;
        int highestTotalBonus = 0;
        
        // Get all structure starts in this chunk - this is fast, only iterates structures actually present
        var structureStarts = level.structureManager().startsForStructure(chunkPos, structure -> true);
        
        for (StructureStart start : structureStarts) {
            if (!start.isValid()) continue;
            
            Structure structure = start.getStructure();
            Identifier structureId = structureRegistry.getKey(structure);
            if (structureId == null) continue;
            
            // Get holder for LocationPredicate
            ResourceKey<Structure> structureKey = ResourceKey.create(Registries.STRUCTURE, structureId);
            Holder<Structure> structureHolder = structureRegistry.getOrThrow(structureKey);
            
            // Check if player is actually inside this structure's bounds
            if (!LocationPredicate.Builder.inStructure(structureHolder).build().matches(level, pos.getX(), pos.getY(), pos.getZ())) {
                continue;
            }
            
            // Player is inside this structure
            StructureBonusSettings settings = StructureLevelingSettingsReloader.get(structureId, structureRegistry);
            
            if (onlyWithBonuses && settings == null) {
                continue; // Skip structures without bonuses when filtering
            }
            
            int bonus = settings != null ? settings.levelBonus() : 0;
            boolean bypasses = settings != null && settings.bypassesCap();
            
            if (bypasses) {
                highestBypassing = Math.max(highestBypassing, bonus);
            } else {
                highestNonBypassing = Math.max(highestNonBypassing, bonus);
            }
            
            // Track structure with highest bonus, or first structure if no bonuses
            if (bonus > highestTotalBonus || (bestStructureId == null && !onlyWithBonuses)) {
                highestTotalBonus = bonus;
                bestStructureId = structureId;
            }
        }
        
        return new StructureBonus(bestStructureId, highestNonBypassing, highestBypassing);
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
        BiomeBonusSettings settings = BiomeLevelingSettingsReloader.get(biomeId, biomeRegistry);
        
        if (settings == null) {
            return new BiomeBonus(biomeId, 0, 0);
        }
        
        int bonus = settings.levelBonus();
        if (settings.bypassesCap()) {
            return new BiomeBonus(biomeId, 0, bonus);
        } else {
            return new BiomeBonus(biomeId, bonus, 0);
        }
    }
}

