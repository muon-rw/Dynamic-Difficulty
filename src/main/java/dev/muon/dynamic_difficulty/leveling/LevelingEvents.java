package dev.muon.dynamic_difficulty.leveling;

import dev.muon.dynamic_difficulty.DynamicDifficulty;
import dev.muon.dynamic_difficulty.api.LevelingAPI;
import dev.muon.dynamic_difficulty.api.PlayerLevelProvider;
import dev.muon.dynamic_difficulty.config.Config;
import dev.muon.dynamic_difficulty.data.DimensionsLevelingSettingsReloader;
import dev.muon.dynamic_difficulty.settings.DimensionLevelingSettings;
import dev.muon.dynamic_difficulty.util.LevelingUtils;
import dev.muon.dynamic_difficulty.data.DimensionTagLevelingSettingsReloader;
import dev.muon.dynamic_difficulty.data.EntityLevelingSettingsReloader;
import dev.muon.dynamic_difficulty.data.EntityTagLevelingSettingsReloader;
import dev.muon.dynamic_difficulty.data.StructureLevelingSettingsReloader;
import dev.muon.dynamic_difficulty.data.StructureTagLevelingSettingsReloader;
import dev.muon.dynamic_difficulty.data.BiomeLevelingSettingsReloader;
import dev.muon.dynamic_difficulty.data.BiomeTagLevelingSettingsReloader;
import dev.muon.dynamic_difficulty.network.NetworkDispatcher;
import dev.muon.dynamic_difficulty.network.message.LocationEntryPacket;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.packs.PackType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class LevelingEvents {
  // Lightweight caches to prevent duplicate notifications (ResourceLocation is just namespace + path strings)
  // Cleaned up on player disconnect 
  private static final Map<UUID, ResourceLocation> playerStructureMap = new HashMap<>();
  private static final Map<UUID, ResourceLocation> playerBiomeMap = new HashMap<>();
  private static final Map<UUID, ResourceLocation> playerDimensionMap = new HashMap<>();
  // Track last base level sent to client to detect changes
  private static final Map<UUID, Integer> playerLastBaseLevelMap = new HashMap<>();

  public static void init() {
    ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
      if ((!(entity instanceof LivingEntity living)) || !LevelingAPI.canHaveLevel(living) || entity.level().isClientSide()) {
        return;
      }

      if (LevelingAPI.hasLevel(living)) {
        LevelingAPI.applyAllLevelAttributes(living);
        return;
      }

      int level = LevelingAPI.calculateLevelForEntity(living);
      LevelingSystem.setLevelTag(living, level);
      LevelingAPI.applyAllLevelAttributes(living);
      addEquipment(living);
    });

    // FABRIC NOTE: Experience modification handled in LivingEntityMixin#modifyExperienceReward
    // FABRIC NOTE: Loot modification handled in LootTableMixin#modifyLoot

    ResourceLoader loader = ResourceLoader.get(PackType.SERVER_DATA);
    loader.registerReloader(
        DimensionsLevelingSettingsReloader.getReloaderId(),
        new DimensionsLevelingSettingsReloader()
    );
    loader.registerReloader(
        DimensionTagLevelingSettingsReloader.getReloaderId(),
        new DimensionTagLevelingSettingsReloader()
    );
    loader.registerReloader(
        EntityLevelingSettingsReloader.getReloaderId(),
        new EntityLevelingSettingsReloader()
    );
    loader.registerReloader(
        EntityTagLevelingSettingsReloader.getReloaderId(),
        new EntityTagLevelingSettingsReloader()
    );
    loader.registerReloader(
        StructureLevelingSettingsReloader.getReloaderId(),
        new StructureLevelingSettingsReloader()
    );
    loader.registerReloader(
        StructureTagLevelingSettingsReloader.getReloaderId(),
        new StructureTagLevelingSettingsReloader()
    );
    loader.registerReloader(
        BiomeLevelingSettingsReloader.getReloaderId(),
        new BiomeLevelingSettingsReloader()
    );
    loader.registerReloader(
        BiomeTagLevelingSettingsReloader.getReloaderId(),
        new BiomeTagLevelingSettingsReloader()
    );

    EntityTrackingEvents.START_TRACKING.register((trackedEntity, trackingPlayer) -> {
      if (!(trackedEntity instanceof LivingEntity living)) return;

      if (living instanceof ServerPlayer trackedPlayer) {
        NetworkDispatcher.syncLevelToPlayer(trackedPlayer, trackingPlayer);
        return;
      }

      if (LevelingAPI.hasLevel(living)) {
        NetworkDispatcher.syncLevelToPlayer(living, trackingPlayer);
      }
    });

    ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
      ServerPlayer player = handler.player;
      calculateAndSyncPlayerLevel(player);
    });

    ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
      ServerPlayer player = handler.player;
      UUID playerId = player.getUUID();
      playerStructureMap.remove(playerId);
      playerBiomeMap.remove(playerId);
      playerDimensionMap.remove(playerId);
      playerLastBaseLevelMap.remove(playerId);
      DynamicDifficulty.LOGGER.debug("Cleaned up location tracking for disconnected player: {}", 
          player.getName().getString());
    });

    ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
      calculateAndSyncPlayerLevel(newPlayer);
    });

    ServerPlayerEvents.COPY_FROM.register((newPlayer, oldPlayer, alive) -> {
      if (!alive) {
        calculateAndSyncPlayerLevel(newPlayer);
      }
    });

    ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
      if (world.isClientSide() || !(entity instanceof LivingEntity living)) {
        return;
      }

      if (LevelingAPI.hasLevel(living)) {
        NetworkDispatcher.syncLevelToClients(living);
      }
    });

    ServerTickEvents.END_SERVER_TICK.register(server -> {
      for (ServerPlayer player : server.getPlayerList().getPlayers()) {
        // Check location changes every second to reduce performance impact
        if (player.tickCount % 20 == 0) {
          checkPlayerStructure(player);
          checkPlayerBiome(player);
          checkPlayerDimension(player);
          checkPlayerBaseLevel(player);
        }
        
        // Periodic player level update as fallback if provider events are missed
        // Providers should use PlayerLevelProvider.requestPlayerLevelUpdate() for immediate updates
        int updateInterval = Config.COMMON.playerLevelUpdateInterval.get();
        if (updateInterval > 0 && player.tickCount % updateInterval == 0) {
          updatePlayerLevel(player);
        }
      }
    });
  }

  /**
   * Calculates a player's display level from registered providers and syncs it to all clients.
   * This is called automatically on common player events (login, respawn, dimension change, clone, death, join level).
   * Providers can also trigger updates manually via PlayerLevelProvider.requestPlayerLevelUpdate().
   */
  private static void calculateAndSyncPlayerLevel(ServerPlayer player) {
    int playerLevel = LevelingAPI.getPlayerDisplayLevel(player);
    LevelingSystem.setLevelTag(player, playerLevel);
    DynamicDifficulty.LOGGER.debug("Syncing player {} level ({}) to all clients", 
        player.getName().getString(), playerLevel);
    NetworkDispatcher.syncLevelToClients(player);
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

  private static ResourceLocation getEquipmentTableId(
      EquipmentSlot slot, ResourceLocation entityId) {
    String path = "equipment/" + entityId.getPath() + "_" + slot.getName();
    return ResourceLocation.fromNamespaceAndPath(entityId.getNamespace(), path);
  }

  private static LootParams createEquipmentLootParams(LivingEntity entity) {
    return new LootParams.Builder((ServerLevel) entity.level())
        .withParameter(LootContextParams.THIS_ENTITY, entity)
        .withParameter(LootContextParams.ORIGIN, entity.position())
        .create(LootContextParamSets.ENTITY);
  }
  
  /**
   * Recalculates and syncs player level if it has changed
   */
  private static void updatePlayerLevel(ServerPlayer player) {
    int currentLevel = LevelingSystem.getLevel(player);
    int newLevel = LevelingAPI.getPlayerDisplayLevel(player);
    
    if (currentLevel != newLevel) {
      LevelingSystem.setLevelTag(player, newLevel);
      NetworkDispatcher.syncLevelToClients(player);
    }
  }
  
  private static void checkPlayerStructure(ServerPlayer player) {
    BlockPos playerPos = player.blockPosition();
    ServerLevel level = player.level();
    Registry<Structure> structureRegistry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
    
    ResourceLocation currentStructure = null;
    int highestLevelBonus = 0;
    
    for (Structure structure : structureRegistry) {
      StructureStart structureStart = level.structureManager().getStructureAt(playerPos, structure);
      if (structureStart != null && structureStart.isValid()) {
        Optional<ResourceKey<Structure>> optKey = structureRegistry.getResourceKey(structure);
        if (optKey.isPresent()) {
          ResourceLocation structureId = optKey.get().location();
          int levelBonus = LevelingAPI.getStructureLevelBonus(structureId, structureRegistry);
          if (levelBonus > highestLevelBonus) {
            highestLevelBonus = levelBonus;
            currentStructure = structureId;
          }
        }
      }
    }
    
    ResourceLocation lastStructure = playerStructureMap.get(player.getUUID());
    
    if ((currentStructure != null && !currentStructure.equals(lastStructure)) ||
        (currentStructure == null && lastStructure != null)) {
      
      if (currentStructure != null) {
        playerStructureMap.put(player.getUUID(), currentStructure);
      } else {
        playerStructureMap.remove(player.getUUID());
      }
      
      if (currentStructure != null && highestLevelBonus > 0) {
        int baseLevel = calculateBaseEntityLevel(player, playerPos);
        
        int playerBonus = 0;
        if (Config.COMMON.applyPlayerBasedLeveling.get()) {
          int rawBonus = PlayerLevelProvider.getProviders().stream()
                  .filter(PlayerLevelProvider::isEnabled)
                  .mapToInt(provider -> provider.calculateBonusLevels(java.util.List.of(player)))
                  .sum();
          
          // Apply same multiplier used for mob leveling to maintain consistency
          double multiplier = Config.COMMON.playerLevelMultiplier.get();
          playerBonus = (int) (rawBonus * multiplier);
          
          if (multiplier != 1.0 && rawBonus > 0) {
            DynamicDifficulty.LOGGER.debug("Structure notification player bonus scaled: {} * {} = {}", 
                rawBonus, multiplier, playerBonus);
          }
        }
        
        DynamicDifficulty.LOGGER.debug("Structure notification for {}: base={}, structure={}, player={}", 
            player.getName().getString(), baseLevel, highestLevelBonus, playerBonus);
        
        NetworkDispatcher.sendLocationEntry(player, LocationEntryPacket.EntryType.STRUCTURE, currentStructure, highestLevelBonus, baseLevel, playerBonus);
      }
    }
  }
  
  private static void checkPlayerBiome(ServerPlayer player) {
    BlockPos playerPos = player.blockPosition();
    ServerLevel level = player.level();
    Registry<net.minecraft.world.level.biome.Biome> biomeRegistry = level.registryAccess().lookupOrThrow(Registries.BIOME);
    
    net.minecraft.world.level.biome.Biome currentBiome = level.getBiome(playerPos).value();
    Optional<ResourceKey<net.minecraft.world.level.biome.Biome>> optBiomeKey = biomeRegistry.getResourceKey(currentBiome);
    
    if (optBiomeKey.isEmpty()) {
      return;
    }
    
    ResourceLocation currentBiomeId = optBiomeKey.get().location();
    int biomeBonus = LevelingAPI.getBiomeLevelBonus(currentBiomeId, biomeRegistry);
    
    ResourceLocation lastBiome = playerBiomeMap.get(player.getUUID());
    
    if ((currentBiomeId != null && !currentBiomeId.equals(lastBiome)) ||
        (currentBiomeId == null && lastBiome != null)) {
      
      if (currentBiomeId != null) {
        playerBiomeMap.put(player.getUUID(), currentBiomeId);
      } else {
        playerBiomeMap.remove(player.getUUID());
      }
      
      // Always send packet when biome changes - even if biome has no bonus, base level may have changed
      if (currentBiomeId != null) {
        int baseLevel = calculateBaseEntityLevel(player, playerPos);
        
        int playerBonus = 0;
        if (Config.COMMON.applyPlayerBasedLeveling.get()) {
          int rawBonus = PlayerLevelProvider.getProviders().stream()
                  .filter(PlayerLevelProvider::isEnabled)
                  .mapToInt(provider -> provider.calculateBonusLevels(java.util.List.of(player)))
                  .sum();
          
          double multiplier = Config.COMMON.playerLevelMultiplier.get();
          playerBonus = (int) (rawBonus * multiplier);
        }
        
        DynamicDifficulty.LOGGER.debug("Biome notification for {}: base={}, biome={}, player={}", 
            player.getName().getString(), baseLevel, biomeBonus, playerBonus);
        
        NetworkDispatcher.sendLocationEntry(player, LocationEntryPacket.EntryType.BIOME, currentBiomeId, biomeBonus, baseLevel, playerBonus);
      }
    }
  }
  
  private static void checkPlayerDimension(ServerPlayer player) {
    ServerLevel level = player.level();
    ResourceLocation currentDimensionId = level.dimension().location();
    
    ResourceLocation lastDimensionId = playerDimensionMap.get(player.getUUID());
    
    if (lastDimensionId == null || !currentDimensionId.equals(lastDimensionId)) {
      playerDimensionMap.put(player.getUUID(), currentDimensionId);
      
      // Dimensions affect base level through their leveling settings, not bonuses
      BlockPos playerPos = player.blockPosition();
      int baseLevel = calculateBaseEntityLevel(player, playerPos);
      
      int playerBonus = 0;
      if (Config.COMMON.applyPlayerBasedLeveling.get()) {
        int rawBonus = PlayerLevelProvider.getProviders().stream()
                .filter(PlayerLevelProvider::isEnabled)
                .mapToInt(provider -> provider.calculateBonusLevels(java.util.List.of(player)))
                .sum();
        
        double multiplier = Config.COMMON.playerLevelMultiplier.get();
        playerBonus = (int) (rawBonus * multiplier);
      }
      
      DynamicDifficulty.LOGGER.debug("Dimension notification for {}: base={}, player={}", 
          player.getName().getString(), baseLevel, playerBonus);
      
      NetworkDispatcher.sendLocationEntry(player, LocationEntryPacket.EntryType.DIMENSION, currentDimensionId, 0, baseLevel, playerBonus);
    }
  }
  
  /**
   * Calculates base level using dimension-specific settings for distance/deepness,
   * but falls back to global config for day scaling (not dimension-specific).
   */
  private static int calculateBaseEntityLevel(ServerPlayer player, BlockPos pos) {
    ServerLevel level = player.level();
    ResourceKey<Level> dimension = level.dimension();
    DimensionLevelingSettings settings = DimensionsLevelingSettingsReloader.get(dimension, level.registryAccess().lookupOrThrow(Registries.DIMENSION));
    
    BlockPos spawnPos = level.getRespawnData().pos();
    double distance = Math.sqrt(spawnPos.distSqr(pos));
    
    int baseLevel = settings.startingLevel();
    
    int distanceBonus = LevelingUtils.calculateDistanceFactors(player, distance, settings);
    baseLevel += distanceBonus;
    
    // Day scaling uses global config (not dimension-specific)
    long days = level.getDayTime() / 24000L;
    baseLevel += (int)(days * Config.COMMON.levelsPerDay.get());
    
    return Math.max(1, baseLevel);
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
      
      ServerLevel level = player.level();
      ResourceLocation dimensionId = level.dimension().location();
      Registry<Biome> biomeRegistry = level.registryAccess().lookupOrThrow(Registries.BIOME);
      Holder<Biome> biomeHolder = level.getBiome(playerPos);
      Optional<ResourceKey<Biome>> optBiomeKey = biomeRegistry.getResourceKey(biomeHolder.value());
      ResourceLocation biomeId = optBiomeKey.isPresent() ? optBiomeKey.get().location() : null;
      int biomeBonus = biomeId != null ? LevelingAPI.getBiomeLevelBonus(biomeId, biomeRegistry) : 0;
      
      int structureBonus = LevelingAPI.getStructureLevelBonus(player);
      
      int playerBonus = 0;
      if (Config.COMMON.applyPlayerBasedLeveling.get()) {
        int rawBonus = PlayerLevelProvider.getProviders().stream()
                .filter(PlayerLevelProvider::isEnabled)
                .mapToInt(provider -> provider.calculateBonusLevels(java.util.List.of(player)))
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
}
