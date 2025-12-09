package dev.muon.dynamic_difficulty;

import com.mojang.serialization.Codec;
import dev.muon.dynamic_difficulty.DynamicDifficulty;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;

/**
 * Fabric attachment for storing entity levels.
 * This replaces the old NBT tag-based storage system.
 */
public class EntityLevelAttachmentFabric {
    
    /**
     * The attachment type for entity levels.
     * - Default value: 1
     * - Serializable: Yes (persists to disk)
     * - Synced: Yes (automatically synced to all clients)
     */
    @SuppressWarnings("UnstableApiUsage")
    public static final AttachmentType<Integer> LEVEL = AttachmentRegistry.create(
            DynamicDifficulty.loc("level"),
            builder -> builder
                    .initializer(() -> 1)
                    .persistent(Codec.INT)
                    .syncWith(ByteBufCodecs.VAR_INT.cast(), AttachmentSyncPredicate.all())
    );
    
    /**
     * Forces class loading and attachment registration.
     * Must be called during mod initialization on both client and server
     * to ensure the attachment type is registered before any sync packets arrive.
     */
    public static void init() {
        // Simply accessing LEVEL triggers static initialization and registration
        DynamicDifficulty.LOGGER.debug("Registered entity level attachment: {}", LEVEL.identifier());
    }
}
