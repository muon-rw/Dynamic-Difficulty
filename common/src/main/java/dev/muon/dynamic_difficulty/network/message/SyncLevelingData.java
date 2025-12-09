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

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

  // Cache for pending data when entity hasn't arrived on client yet
  // Maps entityId -> (level, tickReceived)
  // This handles the race condition where packets arrive before the entity spawn packet
  private static final Map<Integer, PendingData> PENDING_DATA = new ConcurrentHashMap<>();
  private static final int PENDING_TIMEOUT_TICKS = 100; // 5 seconds
  private static final int MAX_PENDING_ENTRIES = 1024; // Safety cap to prevent unbounded growth

  private record PendingData(int level, long tickReceived) {}

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
      applyLevel(living, msg.level, msg.entityId);
    } else {
      // Entity not on client yet - cache for later (with size cap)
      if (PENDING_DATA.size() < MAX_PENDING_ENTRIES) {
        PENDING_DATA.put(msg.entityId, new PendingData(msg.level, level.getGameTime()));
        DynamicDifficulty.LOGGER.debug("SyncLevelingData: Entity ID {} not found yet, caching for later", msg.entityId);
      } else {
        DynamicDifficulty.LOGGER.warn("SyncLevelingData: Pending cache full, dropping data for entity ID {}", msg.entityId);
      }
    }
  }

  private static void applyLevel(LivingEntity entity, int level, int entityId) {
    DynamicDifficulty.getHelper().getLevelAttachmentHelper().setLevel(entity, level);
    DynamicDifficulty.LOGGER.debug("Updated client attachment: {} (ID {}) level = {}",
            entity.getType().getDescription().getString(), entityId, level);
  }

  /**
   * Called from client tick to process pending data.
   * Should be called periodically to apply data to entities that have now appeared.
   */
  public static void processPendingData() {
    Minecraft client = Minecraft.getInstance();
    ClientLevel level = client.level;
    if (level == null || PENDING_DATA.isEmpty()) {
      return;
    }

    long currentTick = level.getGameTime();
    Iterator<Map.Entry<Integer, PendingData>> iterator = PENDING_DATA.entrySet().iterator();
    
    while (iterator.hasNext()) {
      Map.Entry<Integer, PendingData> entry = iterator.next();
      int entityId = entry.getKey();
      PendingData pending = entry.getValue();
      
      // Check for timeout
      if (currentTick - pending.tickReceived() > PENDING_TIMEOUT_TICKS) {
        iterator.remove();
        continue;
      }
      
      // Try to apply to entity
      Entity entity = level.getEntity(entityId);
      if (entity instanceof LivingEntity living) {
        applyLevel(living, pending.level(), entityId);
        iterator.remove();
      }
    }
  }

  /**
   * Clear pending data cache (called on disconnect/world change).
   */
  public static void clearPendingData() {
    PENDING_DATA.clear();
  }

  /**
   * Remove pending data for a specific entity (called when entity unloads on client).
   */
  public static void removePendingData(int entityId) {
    PENDING_DATA.remove(entityId);
  }
}
