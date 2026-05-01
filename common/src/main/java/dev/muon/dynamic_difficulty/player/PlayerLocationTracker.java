package dev.muon.dynamic_difficulty.player;

import dev.muon.dynamic_difficulty.DynamicDifficulty;
import dev.muon.dynamic_difficulty.api.BiomeBonus;
import dev.muon.dynamic_difficulty.api.LevelingAPI;
import dev.muon.dynamic_difficulty.api.PlayerLevelProvider;
import dev.muon.dynamic_difficulty.api.StructureBonus;
import dev.muon.dynamic_difficulty.config.Configs;
import dev.muon.dynamic_difficulty.network.NetworkDispatcher;
import dev.muon.dynamic_difficulty.network.message.LocationEntryPacket;
import dev.muon.dynamic_difficulty.util.LevelingUtils;
import dev.muon.dynamic_difficulty.util.LocationBonusUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
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
        Identifier structureId,
        int structureBonus,
        Identifier biomeId,
        int biomeBonus,
        ResourceKey<Level> dimension,
        int lastBaseLevel
    ) {
        static final PlayerLocationState EMPTY = new PlayerLocationState(null, 0, null, 0, null, 0);
        
        PlayerLocationState withStructure(Identifier structureId, int bonus) {
            return new PlayerLocationState(structureId, bonus, biomeId, biomeBonus, dimension, lastBaseLevel);
        }
        
        PlayerLocationState withBiome(Identifier biomeId, int bonus) {
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
        if (!Configs.SYNC.applyPlayerBasedLeveling.get()) {
            return 0;
        }
        int rawBonus = PlayerLevelProvider.sumBonusLevels(List.of(player));
        return (int) (rawBonus * Configs.SYNC.playerLevelMultiplier.get());
    }

    /**
     * Updates all player location tracking (structure, biome, dimension, base level) in a single pass.
     * Consolidates checks to avoid redundant calculations (position, base level, player bonus).
     */
    public static void updatePlayerLocation(ServerPlayer player) {
        BlockPos playerPos = player.blockPosition();
        ServerLevel level = player.level();
        UUID playerId = player.getUUID();
        ResourceKey<Level> currentDimension = level.dimension();

        PlayerLocationState state = playerStates.getOrDefault(playerId, PlayerLocationState.EMPTY);

        int currentBaseLevel = LevelingUtils.calculateBaseEntityLevel(level, playerPos);
        int playerBonus = calculatePlayerBonus(player);

        // Detect ALL structures (including those without bonuses) — client decides what to display.
        StructureBonus structureBonus = LocationBonusUtils.getStructureAt(level, playerPos, false);
        BiomeBonus biomeBonus = LevelingAPI.getBiomeBonus(level, playerPos);

        Identifier currentStructure = structureBonus.structureId();
        Identifier currentBiome = biomeBonus.biomeId();

        boolean dimensionChanged = !currentDimension.equals(state.dimension());
        boolean structureChanged = !java.util.Objects.equals(currentStructure, state.structureId());
        boolean biomeChanged = currentBiome != null && !currentBiome.equals(state.biomeId());
        boolean baseLevelChanged = Math.abs(currentBaseLevel - state.lastBaseLevel()) >= 1;

        if (!dimensionChanged && !structureChanged && !biomeChanged && !baseLevelChanged) {
            return;
        }

        int displayedLevel = LevelingUtils.calculateDisplayedLevel(level, currentBaseLevel, structureBonus, biomeBonus);

        // Priority: dimension > structure > biome > base level (only one packet per tick).
        if (dimensionChanged) {
            playerStates.put(playerId, PlayerLocationState.EMPTY.withDimension(currentDimension));
            DynamicDifficulty.LOGGER.debug("Dimension notification for {}: base={}, player={}",
                    player.getName().getString(), currentBaseLevel, playerBonus);
            NetworkDispatcher.sendLocationEntry(player, LocationEntryPacket.EntryType.DIMENSION,
                    currentDimension.identifier(), 0, currentBaseLevel, playerBonus, displayedLevel);
            return;
        }

        if (structureChanged) {
            playerStates.put(playerId, state.withStructure(currentStructure, structureBonus.totalBonus()));
            Identifier sentId = currentStructure != null ? currentStructure : state.structureId();
            int sentBonus = currentStructure != null ? structureBonus.totalBonus() : 0;
            DynamicDifficulty.LOGGER.debug("Structure notification for {}: base={}, structure={}, player={}",
                    player.getName().getString(), currentBaseLevel, sentBonus, playerBonus);
            NetworkDispatcher.sendLocationEntry(player, LocationEntryPacket.EntryType.STRUCTURE,
                    sentId, sentBonus, currentBaseLevel, playerBonus, displayedLevel);
            return;
        }

        if (biomeChanged) {
            playerStates.put(playerId, state.withBiome(currentBiome, biomeBonus.totalBonus()));
            DynamicDifficulty.LOGGER.debug("Biome notification for {}: base={}, biome={}, player={}",
                    player.getName().getString(), currentBaseLevel, biomeBonus.totalBonus(), playerBonus);
            NetworkDispatcher.sendLocationEntry(player, LocationEntryPacket.EntryType.BIOME,
                    currentBiome, biomeBonus.totalBonus(), currentBaseLevel, playerBonus, displayedLevel);
            return;
        }

        // baseLevelChanged: use current biome (most informative) or fall back to dimension.
        playerStates.put(playerId, state.withBaseLevel(currentBaseLevel));
        if (currentBiome != null) {
            NetworkDispatcher.sendLocationEntry(player, LocationEntryPacket.EntryType.BIOME, currentBiome,
                    biomeBonus.totalBonus(), currentBaseLevel, playerBonus, displayedLevel);
        } else {
            NetworkDispatcher.sendLocationEntry(player, LocationEntryPacket.EntryType.DIMENSION,
                    currentDimension.identifier(), 0, currentBaseLevel, playerBonus, displayedLevel);
        }
        DynamicDifficulty.LOGGER.debug("Base level update for {}: base={} (was {}), structure={}, biome={}, player={}",
                player.getName().getString(), currentBaseLevel, state.lastBaseLevel(),
                state.structureBonus(), biomeBonus.totalBonus(), playerBonus);
    }

}
