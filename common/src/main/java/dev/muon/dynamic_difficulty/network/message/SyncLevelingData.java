package dev.muon.dynamic_difficulty.network.message;

import dev.muon.dynamic_difficulty.api.LevelingAPI;
import dev.muon.dynamic_difficulty.DynamicDifficulty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Packet to manually sync entity level data to clients.
 * Platform-specific code handles registration and the handle() method adapter.
 */
public class SyncLevelingData implements CustomPacketPayload {
  public static final CustomPacketPayload.Type<SyncLevelingData> TYPE =
      new CustomPacketPayload.Type<>(DynamicDifficulty.loc("sync_leveling_data"));
  
  public static final StreamCodec<FriendlyByteBuf, SyncLevelingData> CODEC = CustomPacketPayload.codec(
      SyncLevelingData::write,
      SyncLevelingData::new
  );

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

  /**
   * Client-side handling logic. Called by platform-specific packet handlers.
   * Must only be called on the client side.
   */
  public static void handleOnClient(SyncLevelingData msg) {
    Minecraft client = Minecraft.getInstance();
    ClientLevel level = client.level;
    if (level == null) {
      return;
    }
    
    Entity entity = level.getEntity(msg.entityId);
    if (entity instanceof LivingEntity living) {
      DynamicDifficulty.getHelper().getLevelAttachmentHelper().setLevel(living, msg.level);
      DynamicDifficulty.LOGGER.debug("Updated client attachment: {} (ID {}) level = {}",
              living.getType().getDescription().getString(), msg.entityId, msg.level);
    } else if (entity == null) {
      DynamicDifficulty.LOGGER.debug("Received SyncLevelingData for entity ID {} (not yet loaded)", msg.entityId);
    }
  }
}
