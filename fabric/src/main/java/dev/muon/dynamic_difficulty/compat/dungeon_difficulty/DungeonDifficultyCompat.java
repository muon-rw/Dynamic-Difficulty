package dev.muon.dynamic_difficulty.compat.dungeon_difficulty;

import dev.muon.dynamic_difficulty.DynamicDifficulty;
import dev.muon.dynamic_difficulty.network.message.SyncDungeonDifficultyData;
import net.dungeon_difficulty.logic.Difficulty;
import net.dungeon_difficulty.logic.EntityDifficultyScalable;
import net.dungeon_difficulty.logic.PatternMatching;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Compatibility handler for Dungeon Difficulty mod.
 * Captures difficulty data when entities are scaled and syncs to clients.
 */
public class DungeonDifficultyCompat {

    /**
     * Called from mixin after Dungeon Difficulty scales an entity.
     * Captures the difficulty data and syncs to clients.
     */
    public static void onEntityScaled(LivingEntity entity, ServerLevel world) {
        if (!(entity instanceof EntityDifficultyScalable scalable)) {
            return;
        }

        PatternMatching.LocationData locationData = scalable.getScalingLocationData();
        if (locationData == null) {
            return;
        }

        // Get the difficulty for this location
        Difficulty difficulty = PatternMatching.getDifficulty(locationData, world);
        if (difficulty == null || !difficulty.isValid()) {
            return;
        }

        String difficultyName = difficulty.type().name;
        int level = difficulty.entityLevel();

        if (difficultyName == null || difficultyName.isEmpty() || level <= 0) {
            return;
        }

        DungeonDifficultyData data = new DungeonDifficultyData(difficultyName, level);
        
        // Set the attachment on the entity
        DynamicDifficulty.getHelper().getDungeonDifficultyAttachmentHelper().setData(entity, data);
        
        // Sync to all players tracking this entity
        SyncDungeonDifficultyData packet = new SyncDungeonDifficultyData(entity.getId(), data);
        var trackingPlayers = PlayerLookup.tracking(entity);
        int playerCount = 0;
        for (ServerPlayer player : trackingPlayers) {
            ServerPlayNetworking.send(player, packet);
            playerCount++;
            DynamicDifficulty.LOGGER.debug("Sent Dungeon Difficulty packet to player {} for entity ID {}", 
                    player.getName().getString(), entity.getId());
        }

        DynamicDifficulty.LOGGER.debug("Captured Dungeon Difficulty data for {} (ID {}): {} level {} - sent to {} players",
                entity.getType().getDescription().getString(), entity.getId(), difficultyName, level, playerCount);
    }
}

