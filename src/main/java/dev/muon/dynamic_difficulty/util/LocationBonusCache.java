package dev.muon.dynamic_difficulty.util;

import dev.muon.dynamic_difficulty.DynamicDifficulty;
import dev.muon.dynamic_difficulty.data.BiomeLevelingSettingsReloader;
import dev.muon.dynamic_difficulty.data.StructureLevelingSettingsReloader;
import dev.muon.dynamic_difficulty.settings.BiomeBonusSettings;
import dev.muon.dynamic_difficulty.settings.StructureBonusSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Cache for structure and biome bonus lookups to avoid redundant calculations.
 * 
 * <p>Implementation details:
 * <ul>
 *   <li>Structure bonuses cached at chunk granularity (16x16 blocks) - structures span large areas</li>
 *   <li>Biome bonuses cached at biome granularity (4x4x4 blocks) - matches Minecraft's biome resolution since 1.18</li>
 *   <li>LRU eviction using LinkedHashMap with access-order</li>
 *   <li>TTL of 5 minutes to handle potential structure/biome changes</li>
 *   <li>Thread-safe using Collections.synchronizedMap wrapper</li>
 * </ul>
 */
public class LocationBonusCache {
    // Cache size limits
    private static final int MAX_STRUCTURE_CACHE_SIZE = 5000;
    private static final int MAX_BIOME_CACHE_SIZE = 10000;
    
    // TTL: 5 minutes in milliseconds
    private static final long CACHE_TTL_MS = 5 * 60 * 1000L;
    
    // LRU caches with access-order - synchronizedMap maintains LinkedHashMap's access-order behavior
    private static final Map<StructureCacheKey, TimestampedResult<StructureBonusResult>> structureCache = 
        Collections.synchronizedMap(new LinkedHashMap<StructureCacheKey, TimestampedResult<StructureBonusResult>>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<StructureCacheKey, TimestampedResult<StructureBonusResult>> eldest) {
                // Auto-evict when exceeding max size (eldest = least recently accessed)
                return size() > MAX_STRUCTURE_CACHE_SIZE;
            }
        });
    private static final Map<BiomeCacheKey, TimestampedResult<BiomeBonusResult>> biomeCache = 
        Collections.synchronizedMap(new LinkedHashMap<BiomeCacheKey, TimestampedResult<BiomeBonusResult>>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<BiomeCacheKey, TimestampedResult<BiomeBonusResult>> eldest) {
                // Auto-evict when exceeding max size (eldest = least recently accessed)
                return size() > MAX_BIOME_CACHE_SIZE;
            }
        });
    
    /**
     * Gets structure bonuses at the given position, computing and caching if necessary.
     * Uses chunk-level granularity since structures span large areas.
     * 
     * @param level The server level
     * @param pos The block position
     * @return The structure bonus result (never null, may have null structureId if no structure)
     */
    public static StructureBonusResult getStructureBonuses(ServerLevel level, BlockPos pos) {
        StructureCacheKey key = new StructureCacheKey(level.dimension(), pos.getX() >> 4, pos.getZ() >> 4);
        
        // Double-check locking pattern to avoid redundant computation
        TimestampedResult<StructureBonusResult> cached = structureCache.get(key);
        if (cached != null && !cached.isExpired()) {
            return cached.result;
        }
        
        // Synchronize on cache to ensure atomic check-then-compute
        synchronized (structureCache) {
            // Re-check after acquiring lock (another thread may have computed it)
            cached = structureCache.get(key);
            if (cached != null && !cached.isExpired()) {
                return cached.result;
            }
            
            // Compute and cache
            StructureBonusResult result = computeStructureBonuses(level, pos);
            cacheStructureResult(key, result);
            return result;
        }
    }
    
    /**
     * Gets biome bonuses at the given position, computing and caching if necessary.
     * Uses 4x4x4 block granularity to match Minecraft's biome resolution.
     * 
     * @param level The server level
     * @param pos The block position
     * @return The biome bonus result (never null, may have null biomeId if biome lookup fails)
     */
    public static BiomeBonusResult getBiomeBonuses(ServerLevel level, BlockPos pos) {
        // Biomes use 4x4x4 resolution since 1.18, so shift by 2 (divide by 4)
        BiomeCacheKey key = new BiomeCacheKey(level.dimension(), pos.getX() >> 2, pos.getY() >> 2, pos.getZ() >> 2);
        
        // Double-check locking pattern to avoid redundant computation
        TimestampedResult<BiomeBonusResult> cached = biomeCache.get(key);
        if (cached != null && !cached.isExpired()) {
            return cached.result;
        }
        
        // Synchronize on cache to ensure atomic check-then-compute
        synchronized (biomeCache) {
            // Re-check after acquiring lock (another thread may have computed it)
            cached = biomeCache.get(key);
            if (cached != null && !cached.isExpired()) {
                return cached.result;
            }
            
            // Compute and cache
            BiomeBonusResult result = computeBiomeBonuses(level, pos);
            cacheBiomeResult(key, result);
            return result;
        }
    }
    
    /**
     * Computes structure bonuses by iterating all structures at the position.
     * Returns the highest bonus found for each bypass category.
     */
    private static StructureBonusResult computeStructureBonuses(ServerLevel level, BlockPos pos) {
        Registry<Structure> structureRegistry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        
        ResourceLocation bestStructureId = null;
        int highestNonBypassing = 0;
        int highestBypassing = 0;
        int highestTotalBonus = 0;
        
        for (Structure structure : structureRegistry) {
            StructureStart start = level.structureManager().getStructureAt(pos, structure);
            if (start != null && start.isValid()) {
                ResourceLocation structureId = structureRegistry.getKey(structure);
                if (structureId != null) {
                    StructureBonusSettings settings = StructureLevelingSettingsReloader.get(structureId, structureRegistry);
                    if (settings != null) {
                        int bonus = settings.levelBonus();
                        if (settings.bypassesCap()) {
                            highestBypassing = Math.max(highestBypassing, bonus);
                        } else {
                            highestNonBypassing = Math.max(highestNonBypassing, bonus);
                        }
                        // Track structure with highest total bonus for display purposes
                        if (bonus > highestTotalBonus) {
                            highestTotalBonus = bonus;
                            bestStructureId = structureId;
                        }
                    }
                }
            }
        }
        
        return new StructureBonusResult(bestStructureId, highestNonBypassing, highestBypassing);
    }
    
    /**
     * Computes biome bonuses at the position.
     */
    private static BiomeBonusResult computeBiomeBonuses(ServerLevel level, BlockPos pos) {
        Registry<Biome> biomeRegistry = level.registryAccess().registryOrThrow(Registries.BIOME);
        Biome biome = level.getBiome(pos).value();
        Optional<ResourceKey<Biome>> optKey = biomeRegistry.getResourceKey(biome);
        
        if (optKey.isPresent()) {
            ResourceLocation biomeId = optKey.get().location();
            BiomeBonusSettings settings = BiomeLevelingSettingsReloader.get(biomeId, biomeRegistry);
            if (settings != null) {
                int bonus = settings.levelBonus();
                if (settings.bypassesCap()) {
                    return new BiomeBonusResult(biomeId, 0, bonus);
                } else {
                    return new BiomeBonusResult(biomeId, bonus, 0);
                }
            }
            return new BiomeBonusResult(biomeId, 0, 0);
        }
        
        return new BiomeBonusResult(null, 0, 0);
    }
    
    private static void cacheStructureResult(StructureCacheKey key, StructureBonusResult result) {
        // LinkedHashMap's removeEldestEntry handles LRU eviction automatically
        // Also remove expired entries opportunistically before adding
        // Note: Already synchronized in calling method
        structureCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
        structureCache.put(key, new TimestampedResult<>(result));
    }
    
    private static void cacheBiomeResult(BiomeCacheKey key, BiomeBonusResult result) {
        // LinkedHashMap's removeEldestEntry handles LRU eviction automatically
        // Also remove expired entries opportunistically before adding
        // Note: Already synchronized in calling method
        biomeCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
        biomeCache.put(key, new TimestampedResult<>(result));
    }
    
    /**
     * Clears all caches. Called on datapack reload when bonus settings may have changed.
     */
    public static void clearCache() {
        synchronized (structureCache) {
            structureCache.clear();
        }
        synchronized (biomeCache) {
            biomeCache.clear();
        }
        DynamicDifficulty.LOGGER.debug("Location bonus caches cleared");
    }
    
    /**
     * Clears cache entries for a specific dimension.
     * Useful when a dimension is unloaded.
     */
    public static void clearCacheForDimension(ResourceKey<Level> dimension) {
        synchronized (structureCache) {
            structureCache.entrySet().removeIf(entry -> entry.getKey().dimension.equals(dimension));
        }
        synchronized (biomeCache) {
            biomeCache.entrySet().removeIf(entry -> entry.getKey().dimension.equals(dimension));
        }
        DynamicDifficulty.LOGGER.debug("Location bonus cache cleared for dimension: {}", dimension.location());
    }
    
    // ========== Cache Keys ==========
    
    /**
     * Cache key for structures - uses chunk coordinates (16x16 blocks).
     */
    private record StructureCacheKey(ResourceKey<Level> dimension, int chunkX, int chunkZ) {}
    
    /**
     * Cache key for biomes - uses biome resolution (4x4x4 blocks).
     * Includes Y coordinate since biomes vary by height in 1.18+.
     */
    private record BiomeCacheKey(ResourceKey<Level> dimension, int biomeX, int biomeY, int biomeZ) {}
    
    // ========== Result Types ==========
    
    /**
     * Wrapper that adds timestamp for TTL checking.
     */
    private static class TimestampedResult<T> {
        final T result;
        final long timestamp;
        
        TimestampedResult(T result) {
            this.result = result;
            this.timestamp = System.currentTimeMillis();
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_TTL_MS;
        }
    }
    
    /**
     * Result containing structure bonus information.
     * 
     * @param structureId The structure with the highest bonus (null if no structure at position)
     * @param nonBypassingBonus Highest bonus from structures that don't bypass cap
     * @param bypassingBonus Highest bonus from structures that bypass cap
     */
    public record StructureBonusResult(
        ResourceLocation structureId,
        int nonBypassingBonus,
        int bypassingBonus
    ) {
        /** Total bonus (non-bypassing + bypassing) */
        public int totalBonus() {
            return nonBypassingBonus + bypassingBonus;
        }
    }
    
    /**
     * Result containing biome bonus information.
     * 
     * @param biomeId The biome at the position (null if lookup failed)
     * @param nonBypassingBonus Bonus that doesn't bypass cap (0 if biome bypasses cap)
     * @param bypassingBonus Bonus that bypasses cap (0 if biome doesn't bypass cap)
     */
    public record BiomeBonusResult(
        ResourceLocation biomeId,
        int nonBypassingBonus,
        int bypassingBonus
    ) {
        /** Total bonus (non-bypassing + bypassing) */
        public int totalBonus() {
            return nonBypassingBonus + bypassingBonus;
        }
    }
}
