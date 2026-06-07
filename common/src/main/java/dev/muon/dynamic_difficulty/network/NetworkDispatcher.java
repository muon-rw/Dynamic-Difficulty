package dev.muon.dynamic_difficulty.network;

import dev.muon.dynamic_difficulty.DynamicDifficulty;
import dev.muon.dynamic_difficulty.network.message.SyncLevelingData;
import dev.muon.dynamic_difficulty.network.message.LocationEntry;
import dev.muon.dynamic_difficulty.platform.NetworkHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

public class NetworkDispatcher {

  private static NetworkHelper getHelper() {
    return DynamicDifficulty.getHelper().getNetworkHelper();
  }

  public static void syncLevelToClients(LivingEntity entity) {
    if (entity.level().isClientSide()) return;
    
    SyncLevelingData packet = new SyncLevelingData(entity);
    
    if (entity instanceof ServerPlayer) {
      for (ServerPlayer player : entity.level().getServer().getPlayerList().getPlayers()) {
        getHelper().sendToPlayer(player, packet);
      }
    } else {
      getHelper().sendToPlayersTrackingEntity(entity, packet);
    }
  }

  public static void syncLevelToPlayer(LivingEntity entity, ServerPlayer player) {
    getHelper().sendToPlayer(player, new SyncLevelingData(entity)); 
  }
  
  public static void syncLevelToAllPlayers(LivingEntity entity) {
    if (entity.level().isClientSide()) return;
    getHelper().sendToAllPlayers(entity.level().getServer(), new SyncLevelingData(entity));
  }
  
  public static void sendLocationEntry(ServerPlayer player, LocationEntry.EntryType entryType,
                                       Identifier locationId,
                                       int nonBypassingBonus, int bypassingBonus,
                                       int baseLevel, int playerBonus, int displayedLevel, int maxLevel) {
    getHelper().sendToPlayer(player, new LocationEntry(entryType, locationId,
            nonBypassingBonus, bypassingBonus, baseLevel, playerBonus, displayedLevel, maxLevel));
  }

}