package dev.muon.dynamic_difficulty.player;

import dev.muon.dynamic_difficulty.DynamicDifficulty;
import dev.muon.dynamic_difficulty.api.BiomeBonus;
import dev.muon.dynamic_difficulty.api.LevelingAPI;
import dev.muon.dynamic_difficulty.api.PlayerLevelProvider;
import dev.muon.dynamic_difficulty.api.StructureBonus;
import dev.muon.dynamic_difficulty.config.Configs;
import dev.muon.dynamic_difficulty.network.NetworkDispatcher;
import dev.muon.dynamic_difficulty.network.message.LocationEntryPacket;
import dev.muon.dynamic_difficulty.settings.LevelingSettings;
import dev.muon.dynamic_difficulty.util.LevelingUtils;
import dev.muon.dynamic_difficulty.util.LocationBonusUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks player locations (structure, biome, dimension) and sends level info updates to clients.
 * Uses {@link dev.muon.dynamic_difficulty.util.LocationBonusUtils} for structure/biome lookups.
 */
public class PlayerLocationTracker {

    /** Consolidated player location state - one entry per player */
    private static final java.util.Map<UUID, PlayerLocationState> playerStates = new ConcurrentHashMap<>();

    /**
     * Record holding all tracked location state for a player.
     * Caches {@code lastDisplayedLevel} (the chain-resolved final level at this position) so the
     * tracker fires a packet whenever it changes, capturing override-driven changes that wouldn't
     * surface via {@code totalBonus()} alone.
     */
    private record PlayerLocationState(
        Identifier structureId,
        Identifier biomeId,
        ResourceKey<Level> dimension,
        int lastDisplayedLevel
    ) {
        static final PlayerLocationState EMPTY = new PlayerLocationState(null, null, null, 0);
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
     * Calculates the player-based bonus level for mob scaling, using the already-resolved chain
     * settings at the player's position so biome/structure overrides of {@code playerLevelMultiplier}
     * are honoured.
     */
    private static int calculatePlayerBonus(ServerPlayer player, LevelingSettings settings) {
        if (!Configs.SYNC.applyPlayerBasedLeveling.get()) {
            return 0;
        }
        int rawBonus = PlayerLevelProvider.sumBonusLevels(List.of(player));
        Double override = settings.playerLevelMultiplier();
        double multiplier = override != null ? override : Configs.SYNC.playerLevelMultiplier.get();
        return (int) (rawBonus * multiplier);
    }

    /**
     * Updates all player location tracking (structure, biome, dimension, displayed level) in a single
     * pass. Consolidates checks to avoid redundant calculations (position, displayed level, player bonus).
     */
    public static void updatePlayerLocation(ServerPlayer player) {
        BlockPos playerPos = player.blockPosition();
        ServerLevel level = player.level();
        UUID playerId = player.getUUID();
        ResourceKey<Level> currentDimension = level.dimension();

        PlayerLocationState state = playerStates.getOrDefault(playerId, PlayerLocationState.EMPTY);

        // Resolve the position-level settings chain ONCE and reuse for all derived calculations.
        LevelingSettings positionSettings = LocationBonusUtils.resolveLocationSettings(level, playerPos);
        int currentBaseLevel = LevelingUtils.calculateBaseEntityLevel(level, playerPos, positionSettings);
        int playerBonus = calculatePlayerBonus(player, positionSettings);

        // Detect ALL structures (including those without bonuses); client decides what to display.
        StructureBonus structureBonus = LocationBonusUtils.getStructureAt(level, playerPos, false);
        BiomeBonus biomeBonus = LevelingAPI.getBiomeBonus(level, playerPos);

        Identifier currentStructure = structureBonus.structureId();
        Identifier currentBiome = biomeBonus.biomeId();

        int displayedLevel = LevelingUtils.calculateDisplayedLevel(positionSettings, currentBaseLevel, structureBonus, biomeBonus);
        int maxLevel = positionSettings.maxLevel();

        boolean dimensionChanged = !currentDimension.equals(state.dimension());
        boolean structureChanged = !java.util.Objects.equals(currentStructure, state.structureId());
        // Null currentBiome means lookup failed; don't treat that as a state change.
        boolean biomeChanged = currentBiome != null && !java.util.Objects.equals(currentBiome, state.biomeId());
        boolean displayedLevelChanged = displayedLevel != state.lastDisplayedLevel();

        if (!dimensionChanged && !structureChanged && !biomeChanged && !displayedLevelChanged) {
            return;
        }

        // Priority: dimension > structure > biome > displayed-level (only one packet per tick).
        if (dimensionChanged) {
            playerStates.put(playerId, new PlayerLocationState(null, null, currentDimension, displayedLevel));
            DynamicDifficulty.LOGGER.debug("Dimension notification for {}: base={}, player={}",
                    player.getName().getString(), currentBaseLevel, playerBonus);
            NetworkDispatcher.sendLocationEntry(player, LocationEntryPacket.EntryType.DIMENSION,
                    currentDimension.identifier(), 0, 0, currentBaseLevel, playerBonus, displayedLevel, maxLevel);
            return;
        }

        if (structureChanged) {
            playerStates.put(playerId, new PlayerLocationState(currentStructure, state.biomeId(), state.dimension(), displayedLevel));
            Identifier sentId = currentStructure != null ? currentStructure : state.structureId();
            int sentNonBypassing = currentStructure != null ? structureBonus.nonBypassingBonus() : 0;
            int sentBypassing = currentStructure != null ? structureBonus.bypassingBonus() : 0;
            DynamicDifficulty.LOGGER.debug("Structure notification for {}: base={}, structure={}+{}, player={}",
                    player.getName().getString(), currentBaseLevel, sentNonBypassing, sentBypassing, playerBonus);
            NetworkDispatcher.sendLocationEntry(player, LocationEntryPacket.EntryType.STRUCTURE,
                    sentId, sentNonBypassing, sentBypassing, currentBaseLevel, playerBonus, displayedLevel, maxLevel);
            return;
        }

        if (biomeChanged) {
            playerStates.put(playerId, new PlayerLocationState(state.structureId(), currentBiome, state.dimension(), displayedLevel));
            DynamicDifficulty.LOGGER.debug("Biome notification for {}: base={}, biome={}+{}, player={}",
                    player.getName().getString(), currentBaseLevel,
                    biomeBonus.nonBypassingBonus(), biomeBonus.bypassingBonus(), playerBonus);
            NetworkDispatcher.sendLocationEntry(player, LocationEntryPacket.EntryType.BIOME,
                    currentBiome, biomeBonus.nonBypassingBonus(), biomeBonus.bypassingBonus(),
                    currentBaseLevel, playerBonus, displayedLevel, maxLevel);
            return;
        }

        // Displayed level changed: pick the most informative location id we have.
        playerStates.put(playerId, new PlayerLocationState(state.structureId(), state.biomeId(), state.dimension(), displayedLevel));
        if (currentBiome != null) {
            NetworkDispatcher.sendLocationEntry(player, LocationEntryPacket.EntryType.BIOME, currentBiome,
                    biomeBonus.nonBypassingBonus(), biomeBonus.bypassingBonus(),
                    currentBaseLevel, playerBonus, displayedLevel, maxLevel);
        } else {
            NetworkDispatcher.sendLocationEntry(player, LocationEntryPacket.EntryType.DIMENSION,
                    currentDimension.identifier(), 0, 0, currentBaseLevel, playerBonus, displayedLevel, maxLevel);
        }
        DynamicDifficulty.LOGGER.debug("Displayed level update for {}: displayed={} (was {}), base={}, player={}",
                player.getName().getString(), displayedLevel, state.lastDisplayedLevel(), currentBaseLevel, playerBonus);
    }

}
