package dev.muon.dynamic_difficulty.network.message;

import dev.muon.dynamic_difficulty.DynamicDifficulty;
import dev.muon.dynamic_difficulty.compat.dungeon_difficulty.DungeonDifficultyData;
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
 * Packet to sync Dungeon Difficulty data to clients.
 */
public class SyncDungeonDifficultyData implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SyncDungeonDifficultyData> TYPE =
            new CustomPacketPayload.Type<>(DynamicDifficulty.loc("sync_dungeon_difficulty_data"));
    
    public static final StreamCodec<FriendlyByteBuf, SyncDungeonDifficultyData> CODEC = CustomPacketPayload.codec(
            SyncDungeonDifficultyData::write,
            SyncDungeonDifficultyData::new
    );

    // Cache for pending data when entity hasn't arrived on client yet
    // Maps entityId -> (data, tickReceived)
    // This handles the race condition where packets arrive before the entity spawn packet
    private static final Map<Integer, PendingData> PENDING_DATA = new ConcurrentHashMap<>();
    private static final int PENDING_TIMEOUT_TICKS = 100; // 5 seconds
    private static final int MAX_PENDING_ENTRIES = 1024; // Safety cap to prevent unbounded growth

    private record PendingData(DungeonDifficultyData data, long tickReceived) {}

    private final int entityId;
    private final String difficultyName;
    private final int level;

    public SyncDungeonDifficultyData(int entityId, DungeonDifficultyData data) {
        this.entityId = entityId;
        this.difficultyName = data.difficultyName();
        this.level = data.level();
    }

    private SyncDungeonDifficultyData(int entityId, String difficultyName, int level) {
        this.entityId = entityId;
        this.difficultyName = difficultyName;
        this.level = level;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeInt(entityId);
        buf.writeUtf(difficultyName);
        buf.writeInt(level);
    }

    public SyncDungeonDifficultyData(FriendlyByteBuf buf) {
        this(buf.readInt(), buf.readUtf(), buf.readInt());
    }

    @Override
    public CustomPacketPayload.Type<SyncDungeonDifficultyData> type() {
        return TYPE;
    }

    /**
     * Client-side handling logic. Called by platform-specific packet handlers.
     * Must only be called on the client side.
     */
    public static void handleOnClient(SyncDungeonDifficultyData msg) {
        Minecraft client = Minecraft.getInstance();
        ClientLevel level = client.level;
        if (level == null) {
            DynamicDifficulty.LOGGER.debug("SyncDungeonDifficultyData: ClientLevel is null, cannot process packet");
            return;
        }
        
        DungeonDifficultyData data = new DungeonDifficultyData(msg.difficultyName, msg.level);
        Entity entity = level.getEntity(msg.entityId);
        
        if (entity instanceof LivingEntity living) {
            applyData(living, data, msg.entityId);
        } else {
            // Entity not on client yet - cache for later (with size cap)
            if (PENDING_DATA.size() < MAX_PENDING_ENTRIES) {
                PENDING_DATA.put(msg.entityId, new PendingData(data, client.level.getGameTime()));
                DynamicDifficulty.LOGGER.debug("SyncDungeonDifficultyData: Entity ID {} not found yet, caching for later", msg.entityId);
            } else {
                DynamicDifficulty.LOGGER.warn("SyncDungeonDifficultyData: Pending cache full, dropping data for entity ID {}", msg.entityId);
            }
        }
    }

    private static void applyData(LivingEntity entity, DungeonDifficultyData data, int entityId) {
        DynamicDifficulty.getHelper().getDungeonDifficultyAttachmentHelper().setData(entity, data);
        DynamicDifficulty.LOGGER.debug("Client received Dungeon Difficulty data for {} (ID {}): {} level {}",
                entity.getType().getDescription().getString(), entityId, data.difficultyName(), data.level());
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
                applyData(living, pending.data(), entityId);
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

