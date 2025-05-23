package dev.muon.dynamic_difficulty.network;

import dev.muon.dynamic_difficulty.DynamicDifficulty;
import dev.muon.dynamic_difficulty.network.message.SyncLevelingData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

@Mod.EventBusSubscriber(modid = DynamicDifficulty.MODID)
public class NetworkDispatcher {
  private static final String PROTOCOL_VERSION = "1";
  public static SimpleChannel CHANNEL;
  private static int packetId = 0;

  private static int nextPacketId() {
    return packetId++;
  }

  public static void init() {
    CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(DynamicDifficulty.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    // Register messages
    CHANNEL.messageBuilder(SyncLevelingData.class, nextPacketId(), NetworkDirection.PLAY_TO_CLIENT)
            .encoder(SyncLevelingData::encode)
            .decoder(SyncLevelingData::decode)
            .consumerMainThread(SyncLevelingData::handle)
            .add();
  }

  public static void syncLevelToClients(LivingEntity entity) {
    if (entity.level().isClientSide()) return;
    CHANNEL.send(
            PacketDistributor.TRACKING_ENTITY.with(() -> entity),
            new SyncLevelingData(entity)
    );
  }

  public static void syncLevelToPlayer(LivingEntity entity, ServerPlayer player) {
    CHANNEL.send(
            PacketDistributor.PLAYER.with(() -> player),
            new SyncLevelingData(entity)
    );
  }

  public static void syncLevelToAll(LivingEntity entity) {
    CHANNEL.send(
            PacketDistributor.ALL.noArg(),
            new SyncLevelingData(entity)
    );
  }
}