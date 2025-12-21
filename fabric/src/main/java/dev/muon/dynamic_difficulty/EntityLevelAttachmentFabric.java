package dev.muon.dynamic_difficulty;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

/**
 * Fabric attachment for storing entity levels.
 * This replaces the old NBT tag-based storage system.
 */
@SuppressWarnings("UnstableApiUsage")
public class EntityLevelAttachmentFabric {
    
    /**
     * The attachment type for entity levels.
     * - Default value: 1
     * - Serializable: Yes (persists to disk)
     * - Synced: No (manual sync via SyncLevelingData packet to handle entity load timing)
     */
    public static final AttachmentType<Integer> LEVEL = AttachmentRegistry.create(
            DynamicDifficulty.id("level"),
            builder -> builder
                    .initializer(() -> 1)
                    .persistent(Codec.INT)
    );
    
    /**
     * Forces class loading and attachment registration.
     * Must be called during mod initialization on both client and server
     * to ensure the attachment type is registered before any sync packets arrive.
     */
    public static void init() {
        DynamicDifficulty.LOGGER.debug("Registered entity level attachment: {}", LEVEL.identifier());
    }
}
