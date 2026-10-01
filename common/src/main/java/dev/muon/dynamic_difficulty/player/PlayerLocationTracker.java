package dev.muon.dynamic_difficulty.player;

import dev.muon.dynamic_difficulty.DynamicDifficulty;
import dev.muon.dynamic_difficulty.api.BiomeBonus;
import dev.muon.dynamic_difficulty.LevelingSystem;
import dev.muon.dynamic_difficulty.api.PlayerLevelProvider;
import dev.muon.dynamic_difficulty.api.StructureBonus;
import dev.muon.dynamic_difficulty.config.Configs;
import dev.muon.dynamic_difficulty.network.NetworkDispatcher;
import dev.muon.dynamic_difficulty.network.message.LocationEntryPacket;
import dev.muon.dynamic_difficulty.settings.LevelingSettings;
import dev.muon.dynamic_difficulty.util.LevelingUtils;
import dev.muon.dynamic_difficulty.util.LocationBonusUtils;
import dev.muon.dynamic_difficulty.util.LocationBonusUtils.ResolvedLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerLocationTracker {

    private static final Map<UUID, PlayerLocationState> playerStates = new ConcurrentHashMap<>();

    /** {@code lastLevel} includes the player bonus, so any change to the shown level sends a packet. */
    private record PlayerLocationState(
        ResourceLocation structureId,
        ResourceLocation biomeId,
        ResourceKey<Level> dimension,
        int lastLevel
    ) {
        static final PlayerLocationState EMPTY = new PlayerLocationState(null, null, null, 0);
    }

    public static void cleanupPlayer(UUID playerId) {
        playerStates.remove(playerId);
    }

    public static int cleanupStaleEntries(Set<UUID> onlinePlayerIds) {
        int initialSize = playerStates.size();
        playerStates.keySet().retainAll(onlinePlayerIds);
        int cleaned = initialSize - playerStates.size();

        if (cleaned > 0) {
            DynamicDifficulty.LOGGER.debug("Cleaned up {} stale player location tracking entries", cleaned);
        }

        return cleaned;
    }

    private static int calculatePlayerBonus(ServerPlayer player, LevelingSettings settings) {
        if (!Configs.SYNC.applyPlayerBasedLeveling.get()) {
            return 0;
        }
        return LevelingSystem.scalePlayerBonus(PlayerLevelProvider.sumBonusLevels(List.of(player)), settings);
    }

    public static void updatePlayerLocation(ServerPlayer player) {
        BlockPos playerPos = player.blockPosition();
        ServerLevel level = player.serverLevel();
        UUID playerId = player.getUUID();
        ResourceKey<Level> currentDimension = level.dimension();

        PlayerLocationState state = playerStates.getOrDefault(playerId, PlayerLocationState.EMPTY);

        ResolvedLocation location = LocationBonusUtils.resolveLocation(level, playerPos);
        LevelingSettings settings = location.settings();
        // Includes structures without a bonus; the client decides which titles to show.
        StructureBonus structureBonus = location.structureBonus();
        BiomeBonus biomeBonus = location.biomeBonus();

        int currentBaseLevel = LevelingUtils.calculateBaseEntityLevel(level, playerPos, settings, location.dimension());
        int displayedLevel = LevelingUtils.calculateFinalLevel(currentBaseLevel, settings, structureBonus, biomeBonus, 0);
        // The client shows displayedLevel + playerBonus, so send what the player bonus adds after the cap.
        int playerBonus = LevelingUtils.calculateFinalLevel(currentBaseLevel, settings, structureBonus, biomeBonus,
                calculatePlayerBonus(player, settings)) - displayedLevel;

        ResourceLocation currentStructure = structureBonus.structureId();
        ResourceLocation currentBiome = biomeBonus.biomeId();

        boolean dimensionChanged = !currentDimension.equals(state.dimension());
        boolean structureChanged = !Objects.equals(currentStructure, state.structureId());
        // Null currentBiome means lookup failed; don't treat that as a state change.
        boolean biomeChanged = currentBiome != null && !Objects.equals(currentBiome, state.biomeId());
        boolean levelChanged = displayedLevel + playerBonus != state.lastLevel();

        if (!dimensionChanged && !structureChanged && !biomeChanged && !levelChanged) {
            return;
        }

        // Priority: dimension > structure > biome > level (one packet per update).
        if (dimensionChanged) {
            sendDimensionEntry(player, playerId, currentDimension, currentBaseLevel, playerBonus, displayedLevel);
            return;
        }

        if (structureChanged) {
            sendStructureEntry(player, playerId, state, currentStructure, structureBonus, currentBaseLevel, playerBonus, displayedLevel);
            return;
        }

        if (biomeChanged) {
            sendBiomeEntry(player, playerId, state, currentBiome, biomeBonus, currentBaseLevel, playerBonus, displayedLevel);
            return;
        }

        sendLevelEntry(player, playerId, state, currentDimension, currentBiome, biomeBonus, currentBaseLevel, playerBonus, displayedLevel);
    }

    private static void sendDimensionEntry(ServerPlayer player, UUID playerId, ResourceKey<Level> currentDimension,
            int currentBaseLevel, int playerBonus, int displayedLevel) {
        playerStates.put(playerId, new PlayerLocationState(null, null, currentDimension, displayedLevel + playerBonus));
        DynamicDifficulty.LOGGER.debug("Dimension notification for {}: base={}, player={}",
                player.getName().getString(), currentBaseLevel, playerBonus);
        NetworkDispatcher.sendLocationEntry(player, LocationEntryPacket.EntryType.DIMENSION,
                currentDimension.location(), 0, currentBaseLevel, playerBonus, displayedLevel);
    }

    private static void sendStructureEntry(ServerPlayer player, UUID playerId, PlayerLocationState state, ResourceLocation currentStructure,
            StructureBonus structureBonus, int currentBaseLevel, int playerBonus, int displayedLevel) {
        playerStates.put(playerId, new PlayerLocationState(currentStructure, state.biomeId(), state.dimension(), displayedLevel + playerBonus));
        ResourceLocation sentId = currentStructure != null ? currentStructure : state.structureId();
        int sentBonus = currentStructure != null ? structureBonus.totalBonus() : 0;
        DynamicDifficulty.LOGGER.debug("Structure notification for {}: base={}, structure={}, player={}",
                player.getName().getString(), currentBaseLevel, sentBonus, playerBonus);
        NetworkDispatcher.sendLocationEntry(player, LocationEntryPacket.EntryType.STRUCTURE,
                sentId, sentBonus, currentBaseLevel, playerBonus, displayedLevel);
    }

    private static void sendBiomeEntry(ServerPlayer player, UUID playerId, PlayerLocationState state, ResourceLocation currentBiome,
            BiomeBonus biomeBonus, int currentBaseLevel, int playerBonus, int displayedLevel) {
        playerStates.put(playerId, new PlayerLocationState(state.structureId(), currentBiome, state.dimension(), displayedLevel + playerBonus));
        DynamicDifficulty.LOGGER.debug("Biome notification for {}: base={}, biome={}, player={}",
                player.getName().getString(), currentBaseLevel, biomeBonus.totalBonus(), playerBonus);
        NetworkDispatcher.sendLocationEntry(player, LocationEntryPacket.EntryType.BIOME,
                currentBiome, biomeBonus.totalBonus(), currentBaseLevel, playerBonus, displayedLevel);
    }

    private static void sendLevelEntry(ServerPlayer player, UUID playerId, PlayerLocationState state,
            ResourceKey<Level> currentDimension, ResourceLocation currentBiome, BiomeBonus biomeBonus,
            int currentBaseLevel, int playerBonus, int displayedLevel) {
        playerStates.put(playerId, new PlayerLocationState(state.structureId(), state.biomeId(), state.dimension(), displayedLevel + playerBonus));
        if (currentBiome != null) {
            NetworkDispatcher.sendLocationEntry(player, LocationEntryPacket.EntryType.BIOME, currentBiome,
                    biomeBonus.totalBonus(), currentBaseLevel, playerBonus, displayedLevel);
        } else {
            NetworkDispatcher.sendLocationEntry(player, LocationEntryPacket.EntryType.DIMENSION,
                    currentDimension.location(), 0, currentBaseLevel, playerBonus, displayedLevel);
        }
        DynamicDifficulty.LOGGER.debug("Level update for {}: level={} (was {}), base={}, player={}",
                player.getName().getString(), displayedLevel + playerBonus, state.lastLevel(), currentBaseLevel, playerBonus);
    }

}
