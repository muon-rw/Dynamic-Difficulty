package dev.muon.dynamic_difficulty;

import dev.muon.dynamic_difficulty.api.LevelingAPI;
import dev.muon.dynamic_difficulty.config.Config;
import dev.muon.dynamic_difficulty.data.BiomeLevelingSettingsReloader;
import dev.muon.dynamic_difficulty.data.BiomeTagLevelingSettingsReloader;
import dev.muon.dynamic_difficulty.data.DimensionTagLevelingSettingsReloader;
import dev.muon.dynamic_difficulty.data.DimensionsLevelingSettingsReloader;
import dev.muon.dynamic_difficulty.data.EntityLevelingSettingsReloader;
import dev.muon.dynamic_difficulty.data.EntityTagLevelingSettingsReloader;
import dev.muon.dynamic_difficulty.data.StructureLevelingSettingsReloader;
import dev.muon.dynamic_difficulty.data.StructureTagLevelingSettingsReloader;
import dev.muon.dynamic_difficulty.network.NetworkDispatcher;
import dev.muon.dynamic_difficulty.player.PlayerLevelUpdateHandler;
import dev.muon.dynamic_difficulty.player.PlayerLocationTracker;
import dev.muon.dynamic_difficulty.util.LevelingUtils;
import dev.muon.dynamic_difficulty.util.LootUtils;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;


public class LevelingEvents {

  public static void register() {
    // Entity join level - apply level bonuses
    ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
      if ((!(entity instanceof LivingEntity living)) || !LevelingAPI.canHaveLevel(living) || entity.level().isClientSide) {
        return;
      }

      if (LevelingAPI.hasLevel(living)) {
        LevelingAPI.applyAllLevelAttributes(living);
        return;
      }

      int level = LevelingAPI.calculateLevelForEntity(living);
      LevelingSystem.setLevelAttachment(living, level);
      LevelingAPI.applyAllLevelAttributes(living);
      LootUtils.addEquipment(living);
    });

    // FABRIC NOTE: See LivingEntityMixin##modifyExperienceReward

    // FABRIC NOTE: See LootTableMixin#modifyLoot

    // Reload listeners
    ResourceManagerHelper.get(PackType.SERVER_DATA)
        .registerReloadListener(new DimensionsLevelingSettingsReloader());
    ResourceManagerHelper.get(PackType.SERVER_DATA)
        .registerReloadListener(new DimensionTagLevelingSettingsReloader());
    ResourceManagerHelper.get(PackType.SERVER_DATA)
        .registerReloadListener(new EntityLevelingSettingsReloader());
    ResourceManagerHelper.get(PackType.SERVER_DATA)
        .registerReloadListener(new EntityTagLevelingSettingsReloader());
    ResourceManagerHelper.get(PackType.SERVER_DATA)
        .registerReloadListener(new BiomeLevelingSettingsReloader());
    ResourceManagerHelper.get(PackType.SERVER_DATA)
        .registerReloadListener(new BiomeTagLevelingSettingsReloader());
    ResourceManagerHelper.get(PackType.SERVER_DATA)
        .registerReloadListener(new StructureLevelingSettingsReloader());
    ResourceManagerHelper.get(PackType.SERVER_DATA)
        .registerReloadListener(new StructureTagLevelingSettingsReloader());

    // Entity tracking - sync levels
    EntityTrackingEvents.START_TRACKING.register((trackedEntity, trackingPlayer) -> {
      if (!(trackedEntity instanceof LivingEntity living)) return;

      // Sync player levels to other players who start tracking them
      if (living instanceof ServerPlayer trackedPlayer) {
        NetworkDispatcher.syncLevelToPlayer(trackedPlayer, trackingPlayer);
        return;
      }

      // Sync mob/entity levels as normal
      if (LevelingAPI.hasLevel(living)) {
        NetworkDispatcher.syncLevelToPlayer(living, trackingPlayer);
      }
    });

    // Player login
    ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
      ServerPlayer player = handler.player;
      dev.muon.dynamic_difficulty.player.PlayerLevelUpdateHandler.calculateAndSyncPlayerLevel(player);
    });

    // Player disconnect - cleanup server-side caches
    ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
      ServerPlayer player = handler.player;
      PlayerLocationTracker.cleanupPlayer(player.getUUID());
      DynamicDifficulty.LOGGER.debug("Cleaned up location tracking for disconnected player: {}", 
          player.getName().getString());
    });

    // Player respawn
    ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
      dev.muon.dynamic_difficulty.player.PlayerLevelUpdateHandler.calculateAndSyncPlayerLevel(newPlayer);
    });

    // Player clone (death)
    ServerPlayerEvents.COPY_FROM.register((newPlayer, oldPlayer, alive) -> {
      if (!alive) {
        dev.muon.dynamic_difficulty.player.PlayerLevelUpdateHandler.calculateAndSyncPlayerLevel(newPlayer);
      }
    });

    // Entity join level - sync to clients
    ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
      if (world.isClientSide() || !(entity instanceof LivingEntity living)) {
        return;
      }

      if (LevelingAPI.hasLevel(living)) {
        NetworkDispatcher.syncLevelToClients(living);
      }
    });

    // Player tick
    ServerTickEvents.END_SERVER_TICK.register(server -> {
      for (ServerPlayer player : server.getPlayerList().getPlayers()) {
        // Check every second to reduce performance impact
        if (player.tickCount % 20 == 0) {
          PlayerLocationTracker.updatePlayerLocation(player);
        }
        
        // Fallback: Update player level periodically in case provider events are missed
        // Providers should use PlayerLevelProvider.requestPlayerLevelUpdate() for immediate updates
        int updateInterval = Config.COMMON.playerLevelUpdateInterval.get();
        if (updateInterval > 0 && player.tickCount % updateInterval == 0) {
          updatePlayerLevel(player);
        }
      }
      
      // Periodic cleanup of stale entries (every 5 minutes)
      if (server.getTickCount() % 6000 == 0) {
        Set<UUID> onlinePlayerIds = server.getPlayerList().getPlayers().stream()
            .map(ServerPlayer::getUUID)
            .collect(java.util.stream.Collectors.toSet());
        PlayerLocationTracker.cleanupStaleEntries(onlinePlayerIds);
      }
    });
  }

  /**
   * Recalculates and syncs player level if it has changed
   */
  private static void updatePlayerLevel(ServerPlayer player) {
    dev.muon.dynamic_difficulty.player.PlayerLevelUpdateHandler.updatePlayerLevel(player);
  }
  
}
