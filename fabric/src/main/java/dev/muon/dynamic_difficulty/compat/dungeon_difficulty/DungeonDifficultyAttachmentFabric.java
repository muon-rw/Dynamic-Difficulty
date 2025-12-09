package dev.muon.dynamic_difficulty.compat.dungeon_difficulty;

import dev.muon.dynamic_difficulty.DynamicDifficulty;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

/**
 * Fabric attachment for storing Dungeon Difficulty data on entities.
 */
public class DungeonDifficultyAttachmentFabric {
    
    /**
     * The attachment type for Dungeon Difficulty data.
     * - Default value: null (no initializer to avoid overhead on all entities)
     * - Serializable: Yes (persists to disk)
     * - Synced: Yes (automatically synced to all clients)
     * 
     * Note: Due to Fabric API timing issues, the manual sync packet + pending cache
     * in SyncDungeonDifficultyData handles the case where packets arrive before entities.
     */
    @SuppressWarnings("UnstableApiUsage")
    public static final AttachmentType<DungeonDifficultyData> DUNGEON_DIFFICULTY = AttachmentRegistry.create(
            DynamicDifficulty.loc("dungeon_difficulty"),
            builder -> builder
                    .persistent(DungeonDifficultyData.CODEC)
                    .syncWith(DungeonDifficultyData.STREAM_CODEC, AttachmentSyncPredicate.all())
    );
}

