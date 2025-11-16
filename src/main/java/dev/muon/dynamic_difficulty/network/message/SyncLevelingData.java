package dev.muon.dynamic_difficulty.network.message;

import dev.muon.dynamic_difficulty.client.ClientLevelCache;
import dev.muon.dynamic_difficulty.api.LevelingAPI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import dev.muon.dynamic_difficulty.DynamicDifficulty;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class SyncLevelingData implements CustomPacketPayload {
  public static final CustomPacketPayload.Type<SyncLevelingData> TYPE =
      new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(DynamicDifficulty.MODID, "sync_leveling_data"));

  private final int entityId;
  private final int level;

  public SyncLevelingData(LivingEntity entity) {
    this.entityId = entity.getId();
    this.level = LevelingAPI.getLevel(entity);
  }

  private SyncLevelingData(int entityId, int level) {
    this.entityId = entityId;
    this.level = level;
  }

  public void write(FriendlyByteBuf buf) {
    buf.writeInt(entityId);
    buf.writeInt(level);
  }

  public SyncLevelingData(FriendlyByteBuf buf) {
    this(buf.readInt(), buf.readInt());
  }

  @Override
  public CustomPacketPayload.Type<SyncLevelingData> type() {
    return TYPE;
  }

  public static void handle(final SyncLevelingData msg, final IPayloadContext context) {
    context.enqueueWork(() -> {
      if (context.flow().isClientbound()) {
        handleClient(msg);
      }
    });
  }

  @OnlyIn(Dist.CLIENT)
  private static void handleClient(SyncLevelingData msg) {
    Minecraft client = Minecraft.getInstance();
    ClientLevel level = client.level;
    if (level == null) {
      return;
    }
    
    Entity entity = level.getEntity(msg.entityId);
      switch (entity) {
          case null ->
                  DynamicDifficulty.LOGGER.warn("Entity with ID {} not found on client for SyncLevelingData", msg.entityId);
          case Player player -> {
              ClientLevelCache.updatePlayerLevel(player.getUUID(), msg.level);
              DynamicDifficulty.LOGGER.debug("Updated client cache: Player {} level = {}",
                      player.getName().getString(), msg.level);
          }
          case LivingEntity livingEntity -> {
              ClientLevelCache.updateEntityLevel(msg.entityId, msg.level);
              DynamicDifficulty.LOGGER.debug("Updated client cache: {} (ID {}) level = {}",
                      livingEntity.getType().getDescription().getString(), msg.entityId, msg.level);
          }
          default ->
                  DynamicDifficulty.LOGGER.warn("Received SyncLevelingData for non-living entity ID {}", msg.entityId);
      }
  }
}