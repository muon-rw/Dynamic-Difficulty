package dev.muon.dynamic_difficulty.compat.dungeon_difficulty;

import dev.muon.dynamic_difficulty.DynamicDifficulty;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * NeoForge attachment for storing Dungeon Difficulty data on entities.
 */
public class DungeonDifficultyAttachmentNeoForge {
    public static final DeferredRegister<AttachmentType<?>> REGISTRY =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, DynamicDifficulty.MODID);

    /**
     * The attachment type for Dungeon Difficulty data.
     * - Default value: null (no overhead on entities without DD scaling)
     * - Serializable: Yes (persists to disk)
     * - Synced: Yes (automatically synced to all clients)
     * 
     * Note: Manual sync packet + pending cache in SyncDungeonDifficultyData
     * handles timing issues where packets arrive before entities load on client.
     * This is only a confirmed issue on Fabric, but we implement it here for consistency.
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<DungeonDifficultyData>> DUNGEON_DIFFICULTY = REGISTRY.register(
            "dungeon_difficulty",
            () -> AttachmentType.<DungeonDifficultyData>builder(() -> null)
                    .serialize(DungeonDifficultyData.CODEC)
                    .sync(DungeonDifficultyData.STREAM_CODEC)
                    .build()
    );
}

