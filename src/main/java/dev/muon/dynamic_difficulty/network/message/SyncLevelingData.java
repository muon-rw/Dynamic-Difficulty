package dev.muon.dynamic_difficulty.network.message;

import dev.muon.dynamic_difficulty.api.LevelingAPI;
import dev.muon.dynamic_difficulty.leveling.EntityLevelAttachment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
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
    if (entity instanceof LivingEntity living) {
      // Update attachment - this is the source of truth
      living.setData(EntityLevelAttachment.LEVEL, msg.level);
      DynamicDifficulty.LOGGER.debug("Updated client attachment: {} (ID {}) level = {}",
              living.getType().getDescription().getString(), msg.entityId, msg.level);
    } else if (entity == null) {
      // Entity not loaded yet - this is fine, level will be synced when entity loads
      // or when player starts tracking it. No need to cache separately.
      DynamicDifficulty.LOGGER.debug("Received SyncLevelingData for entity ID {} (not yet loaded) - will sync when entity loads", msg.entityId);
    }
  }
}
