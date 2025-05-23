package dev.muon.dynamic_difficulty.network.message;

import dev.muon.dynamic_difficulty.client.ClientLevelCache;
import dev.muon.dynamic_difficulty.api.LevelingAPI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import dev.muon.dynamic_difficulty.DynamicDifficulty;

import java.util.function.Supplier;

public class SyncLevelingData {
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

  public static void encode(SyncLevelingData msg, FriendlyByteBuf buf) {
    buf.writeInt(msg.entityId);
    buf.writeInt(msg.level);
  }

  public static SyncLevelingData decode(FriendlyByteBuf buf) {
    return new SyncLevelingData(buf.readInt(), buf.readInt());
  }

  public static void handle(SyncLevelingData msg, Supplier<NetworkEvent.Context> ctx) {
    ctx.get().enqueueWork(() ->
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> handleClient(msg))
    );
    ctx.get().setPacketHandled(true);
  }

  @OnlyIn(Dist.CLIENT)
  private static void handleClient(SyncLevelingData msg) {

    Minecraft client = Minecraft.getInstance();
    ClientLevel level = client.level;
    if (level == null) {
      return;
    }
    
    Entity entity = level.getEntity(msg.entityId);
    if (entity == null) {
      DynamicDifficulty.LOGGER.warn("Entity with ID {} not found on client", msg.entityId);
      return;
    }

    if (entity instanceof Player player) {
      ClientLevelCache.updatePlayerLevel(player.getUUID(), msg.level);
    } else {
      ClientLevelCache.updateEntityLevel(msg.entityId, msg.level);
    }
  }
}