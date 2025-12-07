package dev.muon.dynamic_difficulty;

import com.mojang.serialization.Codec;
import dev.muon.dynamic_difficulty.DynamicDifficulty;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * NeoForge attachment for storing entity levels.
 * This replaces the old NBT tag-based storage system.
 * 
 * Note: Syncing is handled manually via NetworkDispatcher for better control
 * over when and how levels are synced to clients (important for performance
 * with many entities).
 */
public class EntityLevelAttachmentNeoForge {
    public static final DeferredRegister<AttachmentType<?>> REGISTRY =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, DynamicDifficulty.MODID);

    /**
     * The attachment type for entity levels.
     * - Default value: 1
     * - Serializable: Yes (persists to disk)
     * - Synced: No (handled manually via NetworkDispatcher for performance)
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> LEVEL = REGISTRY.register(
            "level",
            () -> AttachmentType.builder(() -> 1)
                    .serialize(Codec.INT)
                    .build()
    );
    
}
