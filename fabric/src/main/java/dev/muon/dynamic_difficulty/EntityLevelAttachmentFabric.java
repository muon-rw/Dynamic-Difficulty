package dev.muon.dynamic_difficulty;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

@SuppressWarnings("UnstableApiUsage")
public class EntityLevelAttachmentFabric {
    
    // Not auto-synced: manual sync via SyncLevelingData packet to handle entity load timing.
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
    public static void registerAttachments() {
        DynamicDifficulty.LOGGER.debug("Registered entity level attachment: {}", LEVEL.identifier());
    }
}
