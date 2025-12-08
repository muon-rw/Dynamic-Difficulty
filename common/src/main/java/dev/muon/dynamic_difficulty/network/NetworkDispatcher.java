package dev.muon.dynamic_difficulty.network;

import dev.muon.dynamic_difficulty.DynamicDifficulty;
import dev.muon.dynamic_difficulty.network.message.SyncLevelingData;
import dev.muon.dynamic_difficulty.network.message.LocationEntryPacket;
import dev.muon.dynamic_difficulty.platform.NetworkHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Common networking API. Platform-specific implementations handle registration and packet sending.
 */
public class NetworkDispatcher {

  private static NetworkHelper getHelper() {
    return DynamicDifficulty.getHelper().getNetworkHelper();
  }

  /**
   * Syncs an entity's level to all relevant clients.
   * For players, send to all players
   * For non-players, send only to players tracking the entity
   */
  public static void syncLevelToClients(LivingEntity entity) {
    if (entity.level().isClientSide()) return;
    
    SyncLevelingData packet = new SyncLevelingData(entity);
    
    if (entity instanceof ServerPlayer) {
      for (ServerPlayer player : entity.level().getServer().getPlayerList().getPlayers()) {
        getHelper().sendToPlayer(player, packet);
      }
    } else {
      // Non-player entities only sync to players tracking them (more efficient)
      getHelper().sendToPlayersTrackingEntity(entity, packet);
    }
  }

  /**
   * Syncs an entity's level to a specific player.
   * Used when a player starts tracking an entity.
   */
  public static void syncLevelToPlayer(LivingEntity entity, ServerPlayer player) {
    getHelper().sendToPlayer(player, new SyncLevelingData(entity)); 
  }
  
  public static void syncLevelToAllPlayers(LivingEntity entity) {
    if (entity.level().isClientSide()) return;
    getHelper().sendToAllPlayers(entity.level().getServer(), new SyncLevelingData(entity));
  }
  
  public static void sendLocationEntry(ServerPlayer player, LocationEntryPacket.EntryType entryType, ResourceLocation locationId, int locationBonus, int baseLevel, int playerBonus, int displayedLevel) {
    getHelper().sendToPlayer(player, new LocationEntryPacket(entryType, locationId, locationBonus, baseLevel, playerBonus, displayedLevel));
  }
}