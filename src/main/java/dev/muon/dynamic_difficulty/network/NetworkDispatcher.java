package dev.muon.dynamic_difficulty.network;

import dev.muon.dynamic_difficulty.DynamicDifficulty;
import dev.muon.dynamic_difficulty.network.message.SyncLevelingData;
import dev.muon.dynamic_difficulty.network.message.StructureEntryPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = DynamicDifficulty.MODID, bus = EventBusSubscriber.Bus.MOD)
public class NetworkDispatcher {

  @SubscribeEvent
  public static void registerPackets(final RegisterPayloadHandlersEvent event) {
    final PayloadRegistrar registrar = event.registrar(DynamicDifficulty.MODID)
            .versioned("1");

    registrar.playToClient(
            SyncLevelingData.TYPE,
            CustomPacketPayload.codec(SyncLevelingData::write, SyncLevelingData::new),
            SyncLevelingData::handle
    );
    
    registrar.playToClient(
            StructureEntryPacket.TYPE,
            CustomPacketPayload.codec(StructureEntryPacket::write, StructureEntryPacket::new),
            StructureEntryPacket::handle
    );
  }

  public static void syncLevelToClients(LivingEntity entity) {
    if (entity.level().isClientSide()) return;
    PacketDistributor.sendToPlayersTrackingEntity(entity, new SyncLevelingData(entity));
  }

  public static void syncLevelToPlayer(LivingEntity entity, ServerPlayer player) {
    PacketDistributor.sendToPlayer(player, new SyncLevelingData(entity)); 
  }

  public static void syncLevelToAllPlayers(LivingEntity entity) {
    PacketDistributor.sendToAllPlayers(new SyncLevelingData(entity));
  }
  
  public static void sendStructureEntry(ServerPlayer player, ResourceLocation structureId, int levelBonus, int baseLevel) {
    PacketDistributor.sendToPlayer(player, new StructureEntryPacket(structureId, levelBonus, baseLevel));
  }
}