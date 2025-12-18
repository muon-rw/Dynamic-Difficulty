package dev.muon.dynamic_difficulty;

import dev.muon.dynamic_difficulty.api.LevelingAPI;
import dev.muon.dynamic_difficulty.config.Config;
import dev.muon.dynamic_difficulty.network.NetworkDispatcher;
import dev.muon.dynamic_difficulty.util.LevelingUtils;
import dev.muon.dynamic_difficulty.util.LootUtils;
import dev.muon.dynamic_difficulty.player.PlayerLevelUpdateHandler;
import dev.muon.dynamic_difficulty.player.PlayerLocationTracker;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.Set;
import java.util.UUID;

/**
 * Common leveling event handlers. Platform-specific event listeners call these methods.
 */
public class LevelingEvents {

    /**
     * Called when an entity joins a level. Applies level bonuses and equipment.
     */
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

    /**
     * Called to sync entity level to clients after joining level.
     */
    public static void syncEntityOnJoin(LivingEntity living) {
        if (living.level().isClientSide()) return;
        if (LevelingAPI.hasLevel(living)) {
            NetworkDispatcher.syncLevelToClients(living);
        }
    }

    /**
     * Adjusts experience drop based on entity level.
     * @return the modified experience amount
     */
    public static int adjustExperienceDrop(LivingEntity entity, int originalExp) {
        if (!LevelingAPI.hasLevel(entity)) return originalExp;
        int level = LevelingAPI.getLevel(entity) + 1;
        double expBonus = Config.COMMON.expBonus.get() * level;
        return (int) (originalExp + originalExp * expBonus);
    }

    /**
     * Drops additional loot for leveled entities.
     */
    public static void dropAdditionalLoot(LivingEntity entity, java.util.function.Consumer<net.minecraft.world.item.ItemStack> dropConsumer, net.minecraft.world.damagesource.DamageSource source) {
        if (!LevelingAPI.hasLevel(entity)) return;
        ResourceLocation lootTableIdRL = DynamicDifficulty.loc("gameplay/leveled_mobs");
        MinecraftServer server = entity.level().getServer();
        if (server == null) return;
        ResourceKey<LootTable> lootTableKey = ResourceKey.create(Registries.LOOT_TABLE, lootTableIdRL);
        LootTable lootTable = server.reloadableRegistries().getLootTable(lootTableKey);
        LootParams lootParams = LootUtils.createLootParams(entity, source);
        lootTable.getRandomItems(lootParams, dropConsumer);
    }

    /**
     * Called when config is reloaded.
     */
    public static void onConfigReload() {
        Config.reloadAttributeBonuses();
        LevelingUtils.reloadConfigCache();
    }

    /**
     * Called when a player starts tracking an entity.
     */
    public static void onStartTracking(LivingEntity trackedEntity, ServerPlayer trackingPlayer) {
        if (trackedEntity instanceof ServerPlayer) {
            NetworkDispatcher.syncLevelToPlayer(trackedEntity, trackingPlayer);
            return;
        }
        if (LevelingAPI.hasLevel(trackedEntity)) {
            NetworkDispatcher.syncLevelToPlayer(trackedEntity, trackingPlayer);
        }
        // Sync Dungeon Difficulty data if present
        if (DynamicDifficulty.isModLoaded("dungeon_difficulty")) {
            NetworkDispatcher.syncDungeonDifficultyToPlayer(trackedEntity, trackingPlayer);
        }
    }

    /**
     * Called when a player logs in.
     */
    public static void onPlayerLoggedIn(ServerPlayer player) {
        PlayerLevelUpdateHandler.calculateAndSyncPlayerLevel(player);
    }

    /**
     * Called when a player logs out.
     */
    public static void onPlayerLoggedOut(ServerPlayer player) {
        PlayerLocationTracker.cleanupPlayer(player.getUUID());
        DynamicDifficulty.LOGGER.debug("Cleaned up location tracking for disconnected player: {}",
                player.getName().getString());
    }

    /**
     * Called when a player respawns.
     */
    public static void onPlayerRespawn(ServerPlayer player) {
        PlayerLevelUpdateHandler.calculateAndSyncPlayerLevel(player);
    }

    /**
     * Called when a player changes dimension.
     */
    public static void onPlayerChangeDimension(ServerPlayer player) {
        PlayerLevelUpdateHandler.calculateAndSyncPlayerLevel(player);
    }

    /**
     * Called when player data is cloned (death/respawn).
     */
    public static void onPlayerClone(ServerPlayer player) {
        PlayerLevelUpdateHandler.calculateAndSyncPlayerLevel(player);
    }

    /**
     * Called when a player dies.
     */
    public static void onPlayerDeath(ServerPlayer player) {
        PlayerLevelUpdateHandler.calculateAndSyncPlayerLevel(player);
    }

    /**
     * Called every player tick.
     */
    public static void onPlayerTick(ServerPlayer player) {
        // Check every second to reduce performance impact
        if (player.tickCount % 20 == 0) {
            PlayerLocationTracker.updatePlayerLocation(player);
        }

        // Fallback: Update player level periodically in case provider events are missed
        int updateInterval = Config.COMMON.playerLevelUpdateInterval.get();
        if (updateInterval > 0 && player.tickCount % updateInterval == 0) {
            PlayerLevelUpdateHandler.updatePlayerLevel(player);
        }
    }

    /**
     * Called every server tick for cleanup.
     */
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
