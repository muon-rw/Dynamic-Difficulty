package dev.muon.dynamic_difficulty.player;

import dev.muon.dynamic_difficulty.DynamicDifficulty;
import dev.muon.dynamic_difficulty.api.BiomeBonus;
import dev.muon.dynamic_difficulty.api.LevelingAPI;
import dev.muon.dynamic_difficulty.api.PlayerLevelProvider;
import dev.muon.dynamic_difficulty.api.StructureBonus;
import dev.muon.dynamic_difficulty.config.Config;
import dev.muon.dynamic_difficulty.network.NetworkDispatcher;
import dev.muon.dynamic_difficulty.network.message.LocationEntryPacket;
import dev.muon.dynamic_difficulty.util.LevelingUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks player locations (structure, biome, dimension) and sends level info updates to clients.
 * Uses {@link dev.muon.dynamic_difficulty.util.LocationBonusUtils} for structure/biome lookups.
 */
public class PlayerLocationTracker {
    
    /** Consolidated player location state - one entry per player */
    private static final Map<UUID, PlayerLocationState> playerStates = new ConcurrentHashMap<>();
    
    /**
     * Record holding all tracked location state for a player.
     */
    private record PlayerLocationState(
        ResourceLocation structureId,
        int structureBonus,
        ResourceLocation biomeId,
        int biomeBonus,
        ResourceKey<Level> dimension,
        int lastBaseLevel
    ) {
        static final PlayerLocationState EMPTY = new PlayerLocationState(null, 0, null, 0, null, 0);
        
        PlayerLocationState withStructure(ResourceLocation structureId, int bonus) {
            return new PlayerLocationState(structureId, bonus, biomeId, biomeBonus, dimension, lastBaseLevel);
        }
        
        PlayerLocationState withBiome(ResourceLocation biomeId, int bonus) {
            return new PlayerLocationState(structureId, structureBonus, biomeId, bonus, dimension, lastBaseLevel);
        }
        
        PlayerLocationState withDimension(ResourceKey<Level> dimension) {
            return new PlayerLocationState(structureId, structureBonus, biomeId, biomeBonus, dimension, lastBaseLevel);
        }
        
        PlayerLocationState withBaseLevel(int baseLevel) {
            return new PlayerLocationState(structureId, structureBonus, biomeId, biomeBonus, dimension, baseLevel);
        }
    }

    /**
     * Cleans up tracking data for a disconnected player.
     */
    public static void cleanupPlayer(UUID playerId) {
        playerStates.remove(playerId);
    }

    /**
     * Periodic cleanup to remove stale entries for players who are no longer online.
     * Should be called periodically (e.g., every 5 minutes) from server tick events.
     * 
     * @param onlinePlayerIds Set of UUIDs for currently online players
     * @return Number of stale entries cleaned up
     */
    public static int cleanupStaleEntries(Set<UUID> onlinePlayerIds) {
        int initialSize = playerStates.size();
        playerStates.keySet().retainAll(onlinePlayerIds);
        int cleaned = initialSize - playerStates.size();
        
        if (cleaned > 0) {
            DynamicDifficulty.LOGGER.debug("Cleaned up {} stale player location tracking entries", cleaned);
        }
        
        return cleaned;
    }

    /**
     * Calculates the player-based bonus level for mob scaling.
     */
    private static int calculatePlayerBonus(ServerPlayer player) {
        if (!Config.COMMON.applyPlayerBasedLeveling.get()) {
            return 0;
        }
        
        int rawBonus = PlayerLevelProvider.getProviders().stream()
                .filter(PlayerLevelProvider::isEnabled)
                .mapToInt(provider -> provider.calculateBonusLevels(List.of(player)))
                .sum();
        
        double multiplier = Config.COMMON.playerLevelMultiplier.get();
        return (int) (rawBonus * multiplier);
    }

    /**
     * Sends a location entry packet with all calculated values.
     */
    private static void sendLocationPacket(ServerPlayer player, LocationEntryPacket.EntryType entryType,
                                          ResourceLocation locationId, int locationBonus, int baseLevel,
                                          int playerBonus, int displayedLevel) {
        NetworkDispatcher.sendLocationEntry(player, entryType, locationId, locationBonus, baseLevel, playerBonus, displayedLevel);
    }

    /**
     * Updates all player location tracking (structure, biome, dimension, base level) in a single pass.
     * Consolidates checks to avoid redundant calculations (position, base level, player bonus).
     * This is more efficient than calling individual check methods separately.
     */
    public static void updatePlayerLocation(ServerPlayer player) {
        BlockPos playerPos = player.blockPosition();
        ServerLevel level = player.serverLevel();
        UUID playerId = player.getUUID();
        ResourceKey<Level> currentDimension = level.dimension();

        PlayerLocationState state = playerStates.getOrDefault(playerId, PlayerLocationState.EMPTY);

        // Calculate common data once (these are expensive operations)
        int currentBaseLevel = LevelingUtils.calculateBaseEntityLevel(player, playerPos);
        int playerBonus = calculatePlayerBonus(player);

        // Fetch location data - detect ALL structures, let client decide what to display
        StructureBonus structureBonus = LevelingAPI.getStructureBonus(level, playerPos);
        BiomeBonus biomeBonus = LevelingAPI.getBiomeBonus(level, playerPos);

        ResourceLocation currentStructure = structureBonus.structureId();
        ResourceLocation currentBiome = biomeBonus.biomeId();

        // Check for changes (priority: dimension > structure > biome > base level)
        boolean dimensionChanged = !currentDimension.equals(state.dimension());
        boolean structureChanged = (currentStructure != null && !currentStructure.equals(state.structureId())) ||
                                   (currentStructure == null && state.structureId() != null);
        boolean biomeChanged = currentBiome != null && !currentBiome.equals(state.biomeId());
        boolean baseLevelChanged = Math.abs(currentBaseLevel - state.lastBaseLevel()) >= 1;

        // Update state and send packets based on priority
        if (dimensionChanged) {
            // Dimension change resets structure/biome state
            PlayerLocationState newState = PlayerLocationState.EMPTY.withDimension(currentDimension);
            playerStates.put(playerId, newState);

            int displayedLevel = LevelingUtils.calculateDisplayedLevel(player, currentBaseLevel, structureBonus, biomeBonus);

            DynamicDifficulty.LOGGER.debug("Dimension notification for {}: base={}, player={}",
                    player.getName().getString(), currentBaseLevel, playerBonus);

            sendLocationPacket(player, LocationEntryPacket.EntryType.DIMENSION,
                    currentDimension.location(), 0, currentBaseLevel, playerBonus, displayedLevel);
            return;
        }

        if (structureChanged) {
            PlayerLocationState newState = state.withStructure(currentStructure, structureBonus.totalBonus());
            playerStates.put(playerId, newState);

            ResourceLocation sentId = currentStructure != null ? currentStructure : state.structureId();
            int sentBonus = currentStructure != null ? structureBonus.totalBonus() : 0;

            int displayedLevel = LevelingUtils.calculateDisplayedLevel(player, currentBaseLevel, structureBonus, biomeBonus);

            DynamicDifficulty.LOGGER.debug("Structure notification for {}: base={}, structure={}, player={}",
                    player.getName().getString(), currentBaseLevel, sentBonus, playerBonus);

            sendLocationPacket(player, LocationEntryPacket.EntryType.STRUCTURE,
                    sentId, sentBonus, currentBaseLevel, playerBonus, displayedLevel);
            return;
        }

        if (biomeChanged) {
            PlayerLocationState newState = state.withBiome(currentBiome, biomeBonus.totalBonus());
            playerStates.put(playerId, newState);

            int displayedLevel = LevelingUtils.calculateDisplayedLevel(player, currentBaseLevel, structureBonus, biomeBonus);

            DynamicDifficulty.LOGGER.debug("Biome notification for {}: base={}, biome={}, player={}",
                    player.getName().getString(), currentBaseLevel, biomeBonus.totalBonus(), playerBonus);

            sendLocationPacket(player, LocationEntryPacket.EntryType.BIOME,
                    currentBiome, biomeBonus.totalBonus(), currentBaseLevel, playerBonus, displayedLevel);
            return;
        }

        if (baseLevelChanged) {
            PlayerLocationState newState = state.withBaseLevel(currentBaseLevel);
            playerStates.put(playerId, newState);

            // Use current structure/biome bonuses
            int displayedLevel = LevelingUtils.calculateDisplayedLevel(player, currentBaseLevel, structureBonus, biomeBonus);

            // Prefer biome entry type (most common), fallback to dimension if biome unknown
            if (currentBiome != null) {
                sendLocationPacket(player, LocationEntryPacket.EntryType.BIOME, currentBiome,
                        biomeBonus.totalBonus(), currentBaseLevel, playerBonus, displayedLevel);
            } else {
                sendLocationPacket(player, LocationEntryPacket.EntryType.DIMENSION,
                        currentDimension.location(), 0, currentBaseLevel, playerBonus, displayedLevel);
            }

            DynamicDifficulty.LOGGER.debug("Base level update for {}: base={} (was {}), structure={}, biome={}, player={}",
                    player.getName().getString(), currentBaseLevel, state.lastBaseLevel(),
                    state.structureBonus(), biomeBonus.totalBonus(), playerBonus);
        }
    }

}
