package dev.muon.dynamic_difficulty;

import dev.muon.dynamic_difficulty.api.LevelingAPI;
import dev.muon.dynamic_difficulty.config.Config;
import dev.muon.dynamic_difficulty.data.BiomeLevelingSettingsReloader;
import dev.muon.dynamic_difficulty.data.BiomeTagLevelingSettingsReloader;
import dev.muon.dynamic_difficulty.data.DimensionTagLevelingSettingsReloader;
import dev.muon.dynamic_difficulty.data.DimensionsLevelingSettingsReloader;
import dev.muon.dynamic_difficulty.data.EntityLevelingSettingsReloader;
import dev.muon.dynamic_difficulty.data.EntityTagLevelingSettingsReloader;
import dev.muon.dynamic_difficulty.data.StructureLevelingSettingsReloader;
import dev.muon.dynamic_difficulty.data.StructureTagLevelingSettingsReloader;
import dev.muon.dynamic_difficulty.network.NetworkDispatcher;
import dev.muon.dynamic_difficulty.util.LevelingUtils;
import dev.muon.dynamic_difficulty.util.LootUtils;
import dev.muon.dynamic_difficulty.util.LocationBonusCache;
import dev.muon.dynamic_difficulty.util.PlayerLevelUpdateHandler;
import dev.muon.dynamic_difficulty.util.PlayerLocationTracker;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@EventBusSubscriber(modid = DynamicDifficulty.MODID)
public class LevelingEvents {


    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void applyLevelBonuses(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        if ((!(entity instanceof LivingEntity living)) || !LevelingAPI.canHaveLevel(living) || entity.level().isClientSide) {
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

    @SubscribeEvent
    public static void adjustExperienceDrop(LivingExperienceDropEvent event) {
        if (!LevelingAPI.hasLevel(event.getEntity())) return;
        int level = LevelingAPI.getLevel(event.getEntity()) + 1;
        int originalExp = event.getDroppedExperience();
        double expBonus = Config.COMMON.expBonus.get() * level;
        event.setDroppedExperience((int) (originalExp + originalExp * expBonus));
    }

    @SubscribeEvent
    public static void dropAdditionalLoot(LivingDropsEvent event) {
        LivingEntity entity = event.getEntity();
        if (!LevelingAPI.hasLevel(entity)) return;
        ResourceLocation lootTableIdRL =
                DynamicDifficulty.loc("gameplay/leveled_mobs");
        MinecraftServer server = entity.level().getServer();
        if (server == null) return;
        ResourceKey<LootTable> lootTableKey = ResourceKey.create(Registries.LOOT_TABLE, lootTableIdRL);
        LootTable lootTable = server.reloadableRegistries().getLootTable(lootTableKey);
        LootParams lootParams = LootUtils.createLootParams(entity, event.getSource());
        lootTable.getRandomItems(lootParams, entity::spawnAtLocation);
    }

    @SubscribeEvent
    public static void reloadSettings(AddReloadListenerEvent event) {
        event.addListener(new DimensionsLevelingSettingsReloader());
        event.addListener(new DimensionTagLevelingSettingsReloader());
        event.addListener(new EntityLevelingSettingsReloader());
        event.addListener(new EntityTagLevelingSettingsReloader());
        event.addListener(new StructureLevelingSettingsReloader());
        event.addListener(new StructureTagLevelingSettingsReloader());
        event.addListener(new BiomeLevelingSettingsReloader());
        event.addListener(new BiomeTagLevelingSettingsReloader());
        
        // Clear location bonus cache when settings reload (structure/biome bonuses may have changed)
        LocationBonusCache.clearCache();
    }

    @SubscribeEvent
    public static void onConfigReload(ModConfigEvent.Reloading event) {
        // Reload attribute bonuses cache when COMMON config is reloaded
        if (event.getConfig().getType() == net.neoforged.fml.config.ModConfig.Type.COMMON
                && event.getConfig().getModId().equals(DynamicDifficulty.MODID)) {
            Config.reloadAttributeBonuses();
            // Reload whitelist/blacklist cache to avoid string concatenation on hot path
            LevelingUtils.reloadConfigCache();
        }
    }

    @SubscribeEvent
    public static void syncEntityLevel(PlayerEvent.StartTracking event) {
        if (!(event.getTarget() instanceof LivingEntity trackedEntity)) return;
        if (!(event.getEntity() instanceof ServerPlayer trackingPlayer)) return;

        // Sync player levels to other players who start tracking them
        if (trackedEntity instanceof ServerPlayer) {
            NetworkDispatcher.syncLevelToPlayer(trackedEntity, trackingPlayer);
            return;
        }

        // Sync mob/entity levels as normal
        if (LevelingAPI.hasLevel(trackedEntity)) {
            NetworkDispatcher.syncLevelToPlayer(trackedEntity, trackingPlayer);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!event.getEntity().level().isClientSide() && event.getEntity() instanceof ServerPlayer player) {
            PlayerLevelUpdateHandler.calculateAndSyncPlayerLevel(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!event.getEntity().level().isClientSide() && event.getEntity() instanceof ServerPlayer player) {
            PlayerLocationTracker.cleanupPlayer(player.getUUID());
            DynamicDifficulty.LOGGER.debug("Cleaned up location tracking for disconnected player: {}",
                    player.getName().getString());
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!event.getEntity().level().isClientSide() && event.getEntity() instanceof ServerPlayer player) {
            PlayerLevelUpdateHandler.calculateAndSyncPlayerLevel(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!event.getEntity().level().isClientSide() && event.getEntity() instanceof ServerPlayer player) {
            PlayerLevelUpdateHandler.calculateAndSyncPlayerLevel(player);
            // Note: PlayerLocationTracker.checkPlayerDimension handles state reset automatically
        }
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (!event.getEntity().level().isClientSide() && event.getEntity() instanceof ServerPlayer player) {
            PlayerLevelUpdateHandler.calculateAndSyncPlayerLevel(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerLevelUpdateHandler.calculateAndSyncPlayerLevel(player);
        }
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof LivingEntity living)) {
            return;
        }

        if (LevelingAPI.hasLevel(living)) {
            NetworkDispatcher.syncLevelToClients(living);
        }
    }


    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // Check every second to reduce performance impact
            // Use consolidated updatePlayerLocation() to avoid redundant calculations
            if (event.getEntity().tickCount % 20 == 0) {
                PlayerLocationTracker.updatePlayerLocation(player);
            }

            // Fallback: Update player level periodically in case provider events are missed
            // Providers should use PlayerLevelProvider.requestPlayerLevelUpdate() for immediate updates
            int updateInterval = Config.COMMON.playerLevelUpdateInterval.get();
            if (updateInterval > 0 && event.getEntity().tickCount % updateInterval == 0) {
                PlayerLevelUpdateHandler.updatePlayerLevel(player);
            }
        }
    }

    /**
     * Periodic cleanup of stale player location tracking entries.
     * Runs every 5 minutes (6000 ticks) to catch edge cases where cleanupPlayer()
     * might not be called (server crash, improper disconnect, mod reload).
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        // Run cleanup every 5 minutes (6000 ticks = 5 minutes at 20 TPS)
        if (event.getServer().getTickCount() % 6000 == 0) {
            Set<UUID> onlinePlayerIds = event.getServer().getPlayerList().getPlayers().stream()
                    .map(ServerPlayer::getUUID)
                    .collect(Collectors.toSet());
            
            PlayerLocationTracker.cleanupStaleEntries(onlinePlayerIds);
        }
    }

    /**
     * Clears location bonus cache for a dimension when it unloads.
     * Prevents stale cache entries from accumulating for unloaded dimensions.
     */
    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            ResourceKey<Level> dimension = serverLevel.dimension();
            LocationBonusCache.clearCacheForDimension(dimension);
        }
    }

}
