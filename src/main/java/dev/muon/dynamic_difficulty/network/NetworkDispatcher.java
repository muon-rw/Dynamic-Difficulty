package dev.muon.dynamic_difficulty.network;

import dev.muon.dynamic_difficulty.network.message.SyncLevelingData;
import dev.muon.dynamic_difficulty.network.message.LocationEntryPacket;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

public class NetworkDispatcher {

  public static void init() {
    PayloadTypeRegistry.playS2C().register(SyncLevelingData.TYPE, SyncLevelingData.CODEC);
    PayloadTypeRegistry.playS2C().register(LocationEntryPacket.TYPE, LocationEntryPacket.CODEC);
  }
  
  @Environment(EnvType.CLIENT)
  public static void registerClient() {
    ClientPlayNetworking.registerGlobalReceiver(
        SyncLevelingData.TYPE, 
        (payload, context) -> SyncLevelingData.handle(payload, context)
    );
    
    ClientPlayNetworking.registerGlobalReceiver(
        LocationEntryPacket.TYPE,
        (payload, context) -> LocationEntryPacket.handle(payload, context)
    );
  }

  /**
   * Syncs an entity's level to clients. 
   * For player entities, syncs to all players (needed for global UI like tablist, placeholders).
   * For other entities, only syncs to players tracking the entity (efficient for mobs).
   */
  public static void syncLevelToClients(LivingEntity entity) {
    if (entity.level().isClientSide()) return;
    
    SyncLevelingData packet = new SyncLevelingData(entity);
    
    if (entity instanceof ServerPlayer) {
      // Player levels are displayed globally (tablist, scoreboards, TextPlaceholderAPI, etc.)
      // so sync to all players for client-side UI
      for (ServerPlayer player : entity.level().getServer().getPlayerList().getPlayers()) {
        ServerPlayNetworking.send(player, packet);
      }
    } else {
      // Non-player entities only sync to players tracking them (more efficient)
      for (ServerPlayer player : PlayerLookup.tracking(entity)) {
        ServerPlayNetworking.send(player, packet);
      }
    }
  }

  /**
   * Syncs an entity's level to a specific player.
   * Used when a player starts tracking an entity.
   */
  public static void syncLevelToPlayer(LivingEntity entity, ServerPlayer player) {
    ServerPlayNetworking.send(player, new SyncLevelingData(entity)); 
  }

  public static void sendLocationEntry(ServerPlayer player, LocationEntryPacket.EntryType entryType, ResourceLocation locationId, int locationBonus, int baseLevel, int playerBonus) {
    ServerPlayNetworking.send(player, new LocationEntryPacket(entryType, locationId, locationBonus, baseLevel, playerBonus));
  }
}