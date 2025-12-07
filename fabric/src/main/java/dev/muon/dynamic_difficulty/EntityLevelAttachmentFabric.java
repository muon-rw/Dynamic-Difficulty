package dev.muon.dynamic_difficulty;

import com.mojang.serialization.Codec;
import dev.muon.dynamic_difficulty.DynamicDifficulty;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

/**
 * Fabric attachment for storing entity levels.
 * This replaces the old NBT tag-based storage system.
 * 
 * Note: Syncing is handled manually via NetworkDispatcher for better control
 * over when and how levels are synced to clients (important for performance
 * with many entities).
 */
public class EntityLevelAttachmentFabric {
    
    /**
     * The attachment type for entity levels.
     * - Default value: 1
     * - Serializable: Yes (persists to disk)
     * - Synced: No (handled manually via NetworkDispatcher for performance)
     */
    @SuppressWarnings("UnstableApiUsage")
    public static final AttachmentType<Integer> LEVEL = AttachmentRegistry.createPersistent(
            DynamicDifficulty.loc("level"),
            Codec.INT
    );
}
