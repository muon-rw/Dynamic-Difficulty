package dev.muon.dynamic_difficulty.leveling;

import dev.muon.dynamic_difficulty.DynamicDifficulty;
import dev.muon.dynamic_difficulty.api.LevelingAPI;
import dev.muon.dynamic_difficulty.api.PlayerLevelProvider;
import dev.muon.dynamic_difficulty.config.Config;
import dev.muon.dynamic_difficulty.data.BiomeLevelingSettingsReloader;
import dev.muon.dynamic_difficulty.data.BiomeTagLevelingSettingsReloader;
import dev.muon.dynamic_difficulty.data.DimensionTagLevelingSettingsReloader;
import dev.muon.dynamic_difficulty.data.DimensionsLevelingSettingsReloader;
import dev.muon.dynamic_difficulty.data.EntityLevelingSettingsReloader;
import dev.muon.dynamic_difficulty.settings.DimensionLevelingSettings;
import dev.muon.dynamic_difficulty.data.EntityTagLevelingSettingsReloader;
import dev.muon.dynamic_difficulty.data.StructureLevelingSettingsReloader;
import dev.muon.dynamic_difficulty.data.StructureTagLevelingSettingsReloader;
import dev.muon.dynamic_difficulty.mixin.LivingEntityAccessor;
import dev.muon.dynamic_difficulty.network.NetworkDispatcher;
import dev.muon.dynamic_difficulty.network.message.LocationEntryPacket;
import dev.muon.dynamic_difficulty.network.message.SyncLevelingData;
import dev.muon.dynamic_difficulty.util.LevelingUtils;

import javax.annotation.Nonnull;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
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
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@EventBusSubscriber(modid = DynamicDifficulty.MODID)
public class LevelingEvents {
    private static final Map<UUID, ResourceLocation> playerStructureMap = new HashMap<>();
    private static final Map<UUID, ResourceLocation> playerBiomeMap = new HashMap<>();
    private static final Map<UUID, ResourceKey<Level>> playerDimensionMap = new HashMap<>();
    // Track last base level sent to client to detect changes
    private static final Map<UUID, Integer> playerLastBaseLevelMap = new HashMap<>();


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
        addEquipment(living);
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
        LootParams lootParams = createLootParams(entity, event.getSource());
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
    }

    @SubscribeEvent
    public static void onConfigReload(ModConfigEvent.Reloading event) {
        // Reload attribute bonuses cache when COMMON config is reloaded
        if (event.getConfig().getType() == net.neoforged.fml.config.ModConfig.Type.COMMON
                && event.getConfig().getModId().equals(DynamicDifficulty.MODID)) {
            Config.reloadAttributeBonuses();
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
            calculateAndSyncPlayerLevel(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!event.getEntity().level().isClientSide() && event.getEntity() instanceof ServerPlayer player) {
            UUID playerId = player.getUUID();
            playerStructureMap.remove(playerId);
            playerBiomeMap.remove(playerId);
            playerDimensionMap.remove(playerId);
            playerLastBaseLevelMap.remove(playerId);
            DynamicDifficulty.LOGGER.debug("Cleaned up location tracking for disconnected player: {}",
                    player.getName().getString());
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!event.getEntity().level().isClientSide() && event.getEntity() instanceof ServerPlayer player) {
            calculateAndSyncPlayerLevel(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!event.getEntity().level().isClientSide() && event.getEntity() instanceof ServerPlayer player) {
            calculateAndSyncPlayerLevel(player);
            // Clear biome cache on dimension change
            playerBiomeMap.remove(player.getUUID());
            // Dimension change is handled by checkPlayerDimension in tick event
        }
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (!event.getEntity().level().isClientSide() && event.getEntity() instanceof ServerPlayer player) {
            calculateAndSyncPlayerLevel(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            calculateAndSyncPlayerLevel(player);
        }
    }


    /**
     * Calculates a player's display level from registered providers and syncs it to all clients.
     * This is called automatically on common player events (login, respawn, dimension change, clone, death, join level).
     * Providers can also trigger updates manually via PlayerLevelProvider.requestPlayerLevelUpdate().
     * 
     * Note: The calculated level is used for display purposes. Player levels also contribute
     * to mob scaling via PlayerLevelProvider.calculateBonusLevels() when mobs spawn nearby.
     */
    private static void calculateAndSyncPlayerLevel(ServerPlayer player) {
        int playerLevel = LevelingAPI.getPlayerDisplayLevel(player);
        LevelingSystem.setLevelAttachment(player, playerLevel);
        DynamicDifficulty.LOGGER.debug("Syncing player {} level ({}) to clients",
                player.getName().getString(), playerLevel);
        NetworkDispatcher.syncLevelToAllPlayers(player);
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

    public static void addEquipment(LivingEntity entity) {
        MinecraftServer server = entity.level().getServer();
        if (server == null) return;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            LootTable equipmentTable = getEquipmentLootTableForSlot(server, entity, slot);
            if (equipmentTable == LootTable.EMPTY) continue;
            LootParams lootParams = createEquipmentLootParams(entity);
            equipmentTable.getRandomItems(lootParams, itemStack -> entity.setItemSlot(slot, itemStack));
        }
    }

    private static LootTable getEquipmentLootTableForSlot(
            MinecraftServer server, LivingEntity entity, EquipmentSlot slot) {
        ResourceLocation entityId = EntityType.getKey(entity.getType());
        ResourceLocation lootTableIdRL = getEquipmentTableId(slot, entityId);
        ResourceKey<LootTable> lootTableKey = ResourceKey.create(Registries.LOOT_TABLE, lootTableIdRL);
        return server.reloadableRegistries().getLootTable(lootTableKey);
    }

    @Nonnull
    private static ResourceLocation getEquipmentTableId(
            EquipmentSlot slot, ResourceLocation entityId) {
        String path = "equipment/" + entityId.getPath() + "_" + slot.getName();
        return ResourceLocation.fromNamespaceAndPath(entityId.getNamespace(), path);
    }

    private static LootParams createLootParams(LivingEntity entity, DamageSource damageSource) {
        LivingEntityAccessor accessor = (LivingEntityAccessor) entity;
        ServerLevel level = (ServerLevel) entity.level();
        LootParams.Builder builder =
                new LootParams.Builder(level)
                        .withParameter(LootContextParams.THIS_ENTITY, entity)
                        .withParameter(LootContextParams.ORIGIN, entity.position())
                        .withParameter(LootContextParams.DAMAGE_SOURCE, damageSource)
                        .withOptionalParameter(LootContextParams.ATTACKING_ENTITY, damageSource.getEntity())
                        .withOptionalParameter(
                                LootContextParams.DIRECT_ATTACKING_ENTITY, damageSource.getDirectEntity());
        int lastHurtByPlayerTime = accessor.getLastHurtByPlayerTime();
        Player lastHurtByPlayer = accessor.getLastHurtByPlayer();
        if (lastHurtByPlayerTime > 0 && lastHurtByPlayer != null) {
            builder =
                    builder
                            .withParameter(LootContextParams.LAST_DAMAGE_PLAYER, lastHurtByPlayer)
                            .withLuck(lastHurtByPlayer.getLuck());
        }
        return builder.create(LootContextParamSets.ENTITY);
    }

    private static LootParams createEquipmentLootParams(LivingEntity entity) {
        return new LootParams.Builder((ServerLevel) entity.level())
                .withParameter(LootContextParams.THIS_ENTITY, entity)
                .withParameter(LootContextParams.ORIGIN, entity.position())
                .create(LootContextParamSets.ENTITY);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // Check every second to reduce performance impact
            if (event.getEntity().tickCount % 20 == 0) {
                checkPlayerStructure(player);
                checkPlayerBiome(player);
                checkPlayerDimension(player);
                checkPlayerBaseLevel(player);
            }

            // Fallback: Update player level periodically in case provider events are missed
            // Providers should use PlayerLevelProvider.requestPlayerLevelUpdate() for immediate updates
            int updateInterval = Config.COMMON.playerLevelUpdateInterval.get();
            if (updateInterval > 0 && event.getEntity().tickCount % updateInterval == 0) {
                updatePlayerLevel(player);
            }
        }
    }

    /**
     * Recalculates and syncs player level if it has changed
     */
    private static void updatePlayerLevel(ServerPlayer player) {
        int currentLevel = LevelingSystem.getLevel(player);
        int newLevel = LevelingAPI.getPlayerDisplayLevel(player);

        if (currentLevel != newLevel) {
            LevelingSystem.setLevelAttachment(player, newLevel);
            NetworkDispatcher.syncLevelToAllPlayers(player);
        }
    }

    private static void checkPlayerStructure(ServerPlayer player) {
        BlockPos playerPos = player.blockPosition();
        ServerLevel level = player.serverLevel();
        Registry<Structure> structureRegistry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);

        ResourceLocation currentStructure = null;
        int highestLevelBonus = 0;

        // Check all structures at player position
        for (Structure structure : structureRegistry) {
            StructureStart structureStart = level.structureManager().getStructureAt(playerPos, structure);
            if (structureStart.isValid()) {
                ResourceLocation structureId = structureRegistry.getKey(structure);
                if (structureId != null) {
                    int levelBonus = LevelingAPI.getStructureLevelBonus(structureId, structureRegistry);
                    if (levelBonus > highestLevelBonus) {
                        highestLevelBonus = levelBonus;
                        currentStructure = structureId;
                    }
                }
            }
        }

        // Get the last known structure for this player
        ResourceLocation lastStructure = playerStructureMap.get(player.getUUID());

        // If structure changed (including null -> structure or structure -> null)
        if ((currentStructure != null && !currentStructure.equals(lastStructure)) ||
                (currentStructure == null && lastStructure != null)) {

            // Update the map
            if (currentStructure != null) {
                playerStructureMap.put(player.getUUID(), currentStructure);
            } else {
                playerStructureMap.remove(player.getUUID());
            }

            // Calculate base level at this position (environmental factors)
            int baseLevel = calculateBaseEntityLevel(player, playerPos);

            // Calculate player-based bonus (get this player's level from providers)
            int playerBonus = 0;
            if (Config.COMMON.applyPlayerBasedLeveling.get()) {
                // Get the bonus that would apply to mobs from this player being nearby
                int rawBonus = PlayerLevelProvider.getProviders().stream()
                        .filter(PlayerLevelProvider::isEnabled)
                        .mapToInt(provider -> provider.calculateBonusLevels(List.of(player)))
                        .sum();

                // Apply the same multiplier used for mob leveling
                double multiplier = Config.COMMON.playerLevelMultiplier.get();
                playerBonus = (int) (rawBonus * multiplier);

                if (multiplier != 1.0 && rawBonus > 0) {
                    DynamicDifficulty.LOGGER.debug("Structure notification player bonus scaled: {} * {} = {}",
                            rawBonus, multiplier, playerBonus);
                }
            }

            // Send packet when entering a structure with bonus, OR when leaving a structure
            // (to update client with structureBonus = 0)
            if (currentStructure != null) {
                DynamicDifficulty.LOGGER.debug("Structure notification for {}: base={}, structure={}, player={}",
                        player.getName().getString(), baseLevel, highestLevelBonus, playerBonus);

                // Send the packet for entering a structure
                NetworkDispatcher.sendLocationEntry(player, LocationEntryPacket.EntryType.STRUCTURE,
                        currentStructure, highestLevelBonus, baseLevel, playerBonus);
            } else {
                // Leaving a structure - send update with structureBonus = 0 to clear client cache
                DynamicDifficulty.LOGGER.debug("Structure exit notification for {}: base={}, structure=0 (left {}), player={}",
                        player.getName().getString(), baseLevel, lastStructure, playerBonus);

                // Send packet with last structure ID but bonus 0 to update client
                NetworkDispatcher.sendLocationEntry(player, LocationEntryPacket.EntryType.STRUCTURE,
                        lastStructure, 0, baseLevel, playerBonus);
            }
        }
    }

    private static void checkPlayerBiome(ServerPlayer player) {
        BlockPos playerPos = player.blockPosition();
        ServerLevel level = player.serverLevel();
        Registry<Biome> biomeRegistry = level.registryAccess().registryOrThrow(Registries.BIOME);

        Holder<Biome> biomeHolder = level.getBiome(playerPos);
        ResourceLocation currentBiome = biomeRegistry.getKey(biomeHolder.value());

        if (currentBiome == null) return;

        int biomeBonus = LevelingAPI.getBiomeLevelBonus(player);

        // Get the last known biome for this player
        ResourceLocation lastBiome = playerBiomeMap.get(player.getUUID());

        if (!currentBiome.equals(lastBiome)) {

            // Update the map
            if (currentBiome != null) {
                playerBiomeMap.put(player.getUUID(), currentBiome);
            } else {
                playerBiomeMap.remove(player.getUUID());
            }

            // Always send packet when biome changes - even if biome has no bonus, base level may have changed
            if (currentBiome != null) {
                // Calculate base level at this position (environmental factors)
                int baseLevel = calculateBaseEntityLevel(player, playerPos);

                // Calculate player-based bonus
                int playerBonus = 0;
                if (Config.COMMON.applyPlayerBasedLeveling.get()) {
                    int rawBonus = PlayerLevelProvider.getProviders().stream()
                            .filter(PlayerLevelProvider::isEnabled)
                            .mapToInt(provider -> provider.calculateBonusLevels(List.of(player)))
                            .sum();

                    double multiplier = Config.COMMON.playerLevelMultiplier.get();
                    playerBonus = (int) (rawBonus * multiplier);
                }

                DynamicDifficulty.LOGGER.debug("Biome notification for {}: base={}, biome={}, player={}",
                        player.getName().getString(), baseLevel, biomeBonus, playerBonus);

                // Send the packet
                NetworkDispatcher.sendLocationEntry(player, LocationEntryPacket.EntryType.BIOME,
                        currentBiome, biomeBonus, baseLevel, playerBonus);
            }
        }
    }

    private static void checkPlayerDimension(ServerPlayer player) {
        ResourceKey<Level> currentDimension = player.level().dimension();

        // Get the last known dimension for this player
        ResourceKey<Level> lastDimension = playerDimensionMap.get(player.getUUID());

        // If dimension changed
        if (!currentDimension.equals(lastDimension)) {
            // Update the map
            playerDimensionMap.put(player.getUUID(), currentDimension);

            // Always send dimension entry packet (dimensions affect base level, not bonuses)
            BlockPos playerPos = player.blockPosition();
            int baseLevel = calculateBaseEntityLevel(player, playerPos);

            // Calculate player-based bonus
            int playerBonus = 0;
            if (Config.COMMON.applyPlayerBasedLeveling.get()) {
                int rawBonus = PlayerLevelProvider.getProviders().stream()
                        .filter(PlayerLevelProvider::isEnabled)
                        .mapToInt(provider -> provider.calculateBonusLevels(List.of(player)))
                        .sum();

                double multiplier = Config.COMMON.playerLevelMultiplier.get();
                playerBonus = (int) (rawBonus * multiplier);
            }

            DynamicDifficulty.LOGGER.debug("Dimension notification for {}: base={}, player={}",
                    player.getName().getString(), baseLevel, playerBonus);

            // Send the packet (dimension bonus is always 0)
            NetworkDispatcher.sendLocationEntry(player, LocationEntryPacket.EntryType.DIMENSION,
                    currentDimension.location(), 0, baseLevel, playerBonus);
        }
    }

    /**
     * Checks if base level has changed significantly and sends update if needed.
     * Ensures level info updates when player moves (distance/deepness changes).
     * Only updates if level changed by at least 1 to avoid spam from minor distance changes.
     */
    private static void checkPlayerBaseLevel(ServerPlayer player) {
        BlockPos playerPos = player.blockPosition();
        int currentBaseLevel = calculateBaseEntityLevel(player, playerPos);
        UUID playerId = player.getUUID();
        Integer lastBaseLevel = playerLastBaseLevelMap.get(playerId);

        if (lastBaseLevel == null || Math.abs(currentBaseLevel - lastBaseLevel) >= 1) {
            playerLastBaseLevelMap.put(playerId, currentBaseLevel);

            ServerLevel level = player.serverLevel();
            ResourceLocation dimensionId = level.dimension().location();
            
            // Get biome ID for packet (still need it for the packet type)
            Registry<Biome> biomeRegistry = level.registryAccess().registryOrThrow(Registries.BIOME);
            Holder<Biome> biomeHolder = level.getBiome(playerPos);
            Optional<ResourceKey<Biome>> optBiomeKey = biomeRegistry.getResourceKey(biomeHolder.value());
            ResourceLocation biomeId = optBiomeKey.isPresent() ? optBiomeKey.get().location() : null;
            
            // Use API method for bonus calculation (consistent with structure bonuses)
            int biomeBonus = LevelingAPI.getBiomeLevelBonus(player);

            Registry<Structure> structureRegistry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
            int structureBonus = 0;
            ResourceLocation currentStructure = null;
            int highestLevelBonus = 0;

            // Check all structures at player position
            for (Structure structure : structureRegistry) {
                StructureStart structureStart = level.structureManager().getStructureAt(playerPos, structure);
                if (structureStart != null && structureStart.isValid()) {
                    ResourceLocation structureId = structureRegistry.getKey(structure);
                    if (structureId != null) {
                        int levelBonus = LevelingAPI.getStructureLevelBonus(structureId, structureRegistry);
                        if (levelBonus > highestLevelBonus) {
                            highestLevelBonus = levelBonus;
                            currentStructure = structureId;
                        }
                    }
                }
            }
            structureBonus = highestLevelBonus;

            int playerBonus = 0;
            if (Config.COMMON.applyPlayerBasedLeveling.get()) {
                int rawBonus = PlayerLevelProvider.getProviders().stream()
                        .filter(PlayerLevelProvider::isEnabled)
                        .mapToInt(provider -> provider.calculateBonusLevels(List.of(player)))
                        .sum();
                double multiplier = Config.COMMON.playerLevelMultiplier.get();
                playerBonus = (int) (rawBonus * multiplier);
            }

            // Prefer biome entry type (most common), fallback to dimension if biome unknown
            if (biomeId != null) {
                NetworkDispatcher.sendLocationEntry(player, LocationEntryPacket.EntryType.BIOME, biomeId, biomeBonus, currentBaseLevel, playerBonus);
            } else {
                NetworkDispatcher.sendLocationEntry(player, LocationEntryPacket.EntryType.DIMENSION, dimensionId, 0, currentBaseLevel, playerBonus);
            }

            DynamicDifficulty.LOGGER.debug("Base level update for {}: base={} (was {}), structure={}, biome={}, player={}",
                    player.getName().getString(), currentBaseLevel, lastBaseLevel != null ? lastBaseLevel : "unknown",
                    structureBonus, biomeBonus, playerBonus);
        }
    }

    private static int calculateBaseEntityLevel(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        // Get dimension-specific settings (or fall back to global config)
        ResourceKey<Level> dimension = level.dimension();
        DimensionLevelingSettings dimSettings = DimensionsLevelingSettingsReloader.get(dimension, level.registryAccess().registryOrThrow(Registries.DIMENSION));

        // Get spawn position (may be overridden by dimension settings)
        BlockPos spawnPos = dimSettings.spawnPosOverride() != null ?
                dimSettings.spawnPosOverride() : level.getSharedSpawnPos();
        double distanceToSpawn = Math.sqrt(spawnPos.distSqr(pos));

        // Starting level from dimension settings
        int baseLevel = dimSettings.startingLevel();

        // Distance and depth factors (using dimension-specific settings)
        int distanceBonus = LevelingUtils.calculateDistanceFactors(player, distanceToSpawn, dimSettings);
        baseLevel += distanceBonus;

        // Day scaling (global config, not dimension-specific)
        long days = level.getDayTime() / 24000L;
        baseLevel += (int) (days * Config.COMMON.levelsPerDay.get());

        // Note: Random bonus is excluded as it's per-entity and non-deterministic

        return Math.max(1, baseLevel);
    }
}
