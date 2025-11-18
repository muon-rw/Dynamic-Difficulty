package dev.muon.dynamic_difficulty.network;

import dev.muon.dynamic_difficulty.network.message.SyncLevelingData;
import dev.muon.dynamic_difficulty.network.message.StructureEntryPacket;
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

  public static void register() {
    // Register payload types (server-side)
    PayloadTypeRegistry.playS2C().register(SyncLevelingData.TYPE, SyncLevelingData.CODEC);
    PayloadTypeRegistry.playS2C().register(StructureEntryPacket.TYPE, StructureEntryPacket.CODEC);
    
    // Client-side handlers are registered in ClientEventHandler
  }
  
  @Environment(EnvType.CLIENT)
  public static void registerClient() {
    // Register client-side handlers
    ClientPlayNetworking.registerGlobalReceiver(
        SyncLevelingData.TYPE, 
        (payload, context) -> SyncLevelingData.handle(payload, context)
    );
    
    ClientPlayNetworking.registerGlobalReceiver(
        StructureEntryPacket.TYPE,
        (payload, context) -> StructureEntryPacket.handle(payload, context)
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
      // Player levels might be displayed globally (tablist, scoreboards, TextPlaceholderAPI, etc.)
      // Send to all players so the data is available for client-side UI
      for (ServerPlayer player : entity.level().getServer().getPlayerList().getPlayers()) {
        ServerPlayNetworking.send(player, packet);
      }
    } else {
      // Non-player entities only need to sync to players who can see them
      // Use Fabric's PlayerLookup.tracking() for efficient entity tracking
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

  /**
   * Syncs an entity's level to all players on the server, regardless of tracking.
   * @deprecated Use syncLevelToClients() instead - it handles player vs non-player entities intelligently.
   */
  @Deprecated
  public static void syncLevelToAllPlayers(LivingEntity entity) {
    SyncLevelingData packet = new SyncLevelingData(entity);
    for (ServerPlayer player : entity.level().getServer().getPlayerList().getPlayers()) {
      ServerPlayNetworking.send(player, packet);
    }
  }
  
  public static void sendStructureEntry(ServerPlayer player, ResourceLocation structureId, int structureBonus, int baseLevel, int playerBonus) {
    ServerPlayNetworking.send(player, new StructureEntryPacket(structureId, structureBonus, baseLevel, playerBonus));
  }
}