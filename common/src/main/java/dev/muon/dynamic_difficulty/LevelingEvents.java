package dev.muon.dynamic_difficulty;

import dev.muon.dynamic_difficulty.api.LevelingAPI;
import dev.muon.dynamic_difficulty.config.ConfigSync;
import dev.muon.dynamic_difficulty.config.Configs;
import dev.muon.dynamic_difficulty.network.NetworkDispatcher;
import dev.muon.dynamic_difficulty.util.LevelingUtils;
import dev.muon.dynamic_difficulty.util.LootUtils;
import dev.muon.dynamic_difficulty.player.PlayerLevelUpdateHandler;
import dev.muon.dynamic_difficulty.player.PlayerLocationTracker;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.Set;
import java.util.UUID;

public class LevelingEvents {

    public static void onEntityJoinLevel(LivingEntity living) {
        if (!LevelingAPI.canHaveLevel(living) || living.level().isClientSide()) {
            return;
        }

        if (LevelingAPI.hasLevel(living)) {
            LevelingAPI.applyAllLevelAttributes(living);
            return;
        }

        int level = LevelingAPI.calculateLevelForEntity(living);
        LevelingSystem.setLevelAttachment(living, level);
        LevelingAPI.applyAllLevelAttributes(living);
        LootUtils.addEquipment(living);
    }

    public static void syncEntityOnJoin(LivingEntity living) {
        if (living.level().isClientSide()) return;
        if (LevelingAPI.hasLevel(living)) {
            NetworkDispatcher.syncLevelToClients(living);
        }
    }

    public static int adjustExperienceDrop(LivingEntity entity, int originalExp) {
        if (!LevelingAPI.hasLevel(entity)) return originalExp;
        int level = LevelingAPI.getLevel(entity) + 1;
        double expBonus = Configs.SYNC.expBonus.get() * level;
        return (int) (originalExp + originalExp * expBonus);
    }

    public static void dropAdditionalLoot(LivingEntity entity, java.util.function.Consumer<net.minecraft.world.item.ItemStack> dropConsumer, net.minecraft.world.damagesource.DamageSource source) {
        if (!LevelingAPI.hasLevel(entity)) return;
        Identifier lootTableId = DynamicDifficulty.id("gameplay/leveled_mobs");
        MinecraftServer server = entity.level().getServer();
        if (server == null) return;
        ResourceKey<LootTable> lootTableKey = ResourceKey.create(Registries.LOOT_TABLE, lootTableId);
        LootTable lootTable = server.reloadableRegistries().getLootTable(lootTableKey);
        LootParams lootParams = LootUtils.createLootParams(entity, source);
        lootTable.getRandomItems(lootParams, dropConsumer);
    }

    public static void onConfigReload() {
        ConfigSync.reloadAttributeBonuses();
        LevelingUtils.reloadConfigCache();
    }

    public static void onStartTracking(LivingEntity trackedEntity, ServerPlayer trackingPlayer) {
        if (trackedEntity instanceof ServerPlayer) {
            NetworkDispatcher.syncLevelToPlayer(trackedEntity, trackingPlayer);
            return;
        }
        if (LevelingAPI.hasLevel(trackedEntity)) {
            NetworkDispatcher.syncLevelToPlayer(trackedEntity, trackingPlayer);
        }
    }

    public static void onPlayerLoggedIn(ServerPlayer player) {
        PlayerLevelUpdateHandler.calculateAndSyncPlayerLevel(player);
    }

    public static void onPlayerLoggedOut(ServerPlayer player) {
        PlayerLocationTracker.cleanupPlayer(player.getUUID());
        DynamicDifficulty.LOGGER.debug("Cleaned up location tracking for disconnected player: {}",
                player.getName().getString());
    }

    public static void onPlayerRespawn(ServerPlayer player) {
        PlayerLevelUpdateHandler.calculateAndSyncPlayerLevel(player);
    }

    public static void onPlayerChangeDimension(ServerPlayer player) {
        PlayerLevelUpdateHandler.calculateAndSyncPlayerLevel(player);
    }

    public static void onPlayerClone(ServerPlayer player) {
        PlayerLevelUpdateHandler.calculateAndSyncPlayerLevel(player);
    }

    public static void onPlayerDeath(ServerPlayer player) {
        PlayerLevelUpdateHandler.calculateAndSyncPlayerLevel(player);
    }

    public static void onPlayerTick(ServerPlayer player) {
        updatePlayerLocationPeriodically(player);
        updatePlayerLevelFallback(player);
    }

    private static void updatePlayerLocationPeriodically(ServerPlayer player) {
        if (player.tickCount % 20 == 0) {
            PlayerLocationTracker.updatePlayerLocation(player);
        }
    }

    private static void updatePlayerLevelFallback(ServerPlayer player) {
        int updateInterval = Configs.SYNC.playerLevelUpdateInterval.get();
        if (updateInterval > 0 && player.tickCount % updateInterval == 0) {
            PlayerLevelUpdateHandler.updatePlayerLevel(player);
        }
    }

    public static void onServerTick(MinecraftServer server) {
        // Run cleanup every 5 minutes (6000 ticks = 5 minutes at 20 TPS)
        if (server.getTickCount() % 6000 == 0) {
            Set<UUID> onlinePlayerIds = server.getPlayerList().getPlayers().stream()
                    .map(ServerPlayer::getUUID)
                    .collect(java.util.stream.Collectors.toSet());
            PlayerLocationTracker.cleanupStaleEntries(onlinePlayerIds);
        }
    }
}
