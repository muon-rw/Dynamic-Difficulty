package dev.muon.dynamic_difficulty.compat.dungeon_difficulty;

import dev.muon.dynamic_difficulty.DynamicDifficulty;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

/**
 * Fabric attachment for storing Dungeon Difficulty data on entities.
 */
@SuppressWarnings("UnstableApiUsage")
public class DungeonDifficultyAttachmentFabric {
    
    /**
     * The attachment type for Dungeon Difficulty data.
     * - Default value: null (no initializer to avoid overhead on all entities)
     * - Serializable: Yes (persists to disk)
     * - Synced: No (manual sync via SyncDungeonDifficultyData packet with pending cache
     *   to handle cases where packets arrive before entities are loaded)
     */
    public static final AttachmentType<DungeonDifficultyData> DUNGEON_DIFFICULTY = AttachmentRegistry.create(
            DynamicDifficulty.loc("dungeon_difficulty"),
            builder -> builder
                    .persistent(DungeonDifficultyData.CODEC)
    );
    
    /**
     * Forces class loading and attachment registration.
     * Must be called during mod initialization on both client and server
     * to ensure the attachment type is registered before any sync packets arrive.
     */
    public static void init() {
        DynamicDifficulty.LOGGER.debug("Registered Dungeon Difficulty attachment: {}", DUNGEON_DIFFICULTY.identifier());
    }
}

