package dev.muon.dynamic_difficulty;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.ResourceLocation;

/**
 * Fabric attachment for storing entity levels.
 * This replaces the old NBT tag-based storage system.
 * 
 * Note: Syncing is handled manually via NetworkDispatcher for better control
 * over when and how levels are synced to clients (important for performance
 * with many entities).
 */
public class EntityLevelAttachment {
    /**
     * The attachment type for entity levels.
     * - Default value: 1 (required for getData() to work without explicit set)
     * - Serializable: Yes (persists to disk)
     * - Synced: No (handled manually via NetworkDispatcher for performance)
     */
    public static final AttachmentType<Integer> LEVEL = AttachmentRegistry.<Integer>builder()
            .persistent(Codec.INT)
            .initializer(() -> 1)
            .buildAndRegister(ResourceLocation.fromNamespaceAndPath(DynamicDifficulty.MODID, "level"));
}

