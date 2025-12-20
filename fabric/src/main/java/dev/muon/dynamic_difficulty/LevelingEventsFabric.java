package dev.muon.dynamic_difficulty;

import dev.muon.dynamic_difficulty.command.ModCommands;
import fuzs.forgeconfigapiport.fabric.api.v5.ModConfigEvents;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.fml.config.ModConfig;

/**
 * Fabric-specific event handlers that delegate to common LevelingEvents.
 */
public class LevelingEventsFabric {

    public static void init() {
        // Command registration
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            ModCommands.register(dispatcher);
        });

        // Entity load - apply level bonuses
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof LivingEntity living) {
                LevelingEvents.onEntityJoinLevel(living);
            }
        });

        // Entity load - sync level to clients
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof LivingEntity living) {
                LevelingEvents.syncEntityOnJoin(living);
            }
        });

        // Experience modification is handled in LivingEntityMixin#modifyExperienceReward
        // Loot modification is handled in LootTableMixin#modifyLoot

        // Entity tracking - sync levels when player starts tracking
        EntityTrackingEvents.START_TRACKING.register((trackedEntity, trackingPlayer) -> {
            if (trackedEntity instanceof LivingEntity living) {
                LevelingEvents.onStartTracking(living, trackingPlayer);
            }
        });

        // Player login
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            LevelingEvents.onPlayerLoggedIn(handler.player);
        });

        // Player logout
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            LevelingEvents.onPlayerLoggedOut(handler.player);
        });

        // Player respawn
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            LevelingEvents.onPlayerRespawn(newPlayer);
        });

        // Player clone (death/respawn data copy)
        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
            if (!alive) {
                LevelingEvents.onPlayerClone(newPlayer);
            }
        });

        // Player death
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (entity instanceof ServerPlayer player) {
                LevelingEvents.onPlayerDeath(player);
            }
        });

        // Player dimension change
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> {
            LevelingEvents.onPlayerChangeDimension(player);
        });

        // Server tick - player location checks and cleanup
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                LevelingEvents.onPlayerTick(player);
            }
            LevelingEvents.onServerTick(server);
        });

        // Config reload
        ModConfigEvents.reloading(DynamicDifficulty.MODID).register(config -> {
            if (config.getType() == ModConfig.Type.COMMON) {
                LevelingEvents.onConfigReload();
            }
        });
    }
}
