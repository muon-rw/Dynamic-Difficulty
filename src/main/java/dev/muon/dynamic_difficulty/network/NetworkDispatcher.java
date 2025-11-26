package dev.muon.dynamic_difficulty.network;

import dev.muon.dynamic_difficulty.DynamicDifficulty;
import dev.muon.dynamic_difficulty.network.message.SyncLevelingData;
import dev.muon.dynamic_difficulty.network.message.LocationEntryPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = DynamicDifficulty.MODID)
public class NetworkDispatcher {

  @SubscribeEvent
  public static void registerPackets(final RegisterPayloadHandlersEvent event) {
    final PayloadRegistrar registrar = event.registrar(DynamicDifficulty.MODID)
            .versioned("1");

    registrar.playToClient(
            SyncLevelingData.TYPE,
            SyncLevelingData.CODEC,
            SyncLevelingData::handle
    );
    
    registrar.playToClient(
            LocationEntryPacket.TYPE,
            LocationEntryPacket.CODEC,
            LocationEntryPacket::handle
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
        PacketDistributor.sendToPlayer(player, packet);
      }
    } else {
      // Non-player entities only sync to players tracking them (more efficient)
      PacketDistributor.sendToPlayersTrackingEntity(entity, packet);
    }
  }

  /**
   * Syncs an entity's level to a specific player.
   * Used when a player starts tracking an entity.
   */
  public static void syncLevelToPlayer(LivingEntity entity, ServerPlayer player) {
    PacketDistributor.sendToPlayer(player, new SyncLevelingData(entity)); 
  }
  
  public static void syncLevelToAllPlayers(LivingEntity entity) {
    PacketDistributor.sendToAllPlayers(new SyncLevelingData(entity));
  }
  
  public static void sendLocationEntry(ServerPlayer player, LocationEntryPacket.EntryType entryType, ResourceLocation locationId, int locationBonus, int baseLevel, int playerBonus, int displayedLevel) {
    PacketDistributor.sendToPlayer(player, new LocationEntryPacket(entryType, locationId, locationBonus, baseLevel, playerBonus, displayedLevel));
  }
}