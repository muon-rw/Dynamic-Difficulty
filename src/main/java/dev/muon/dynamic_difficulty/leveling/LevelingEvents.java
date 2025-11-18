package dev.muon.dynamic_difficulty.leveling;

import dev.muon.dynamic_difficulty.DynamicDifficulty;
import dev.muon.dynamic_difficulty.api.LevelingAPI;
import dev.muon.dynamic_difficulty.api.PlayerLevelProvider;
import dev.muon.dynamic_difficulty.config.Config;
import dev.muon.dynamic_difficulty.data.DimensionsLevelingSettingsReloader;
import dev.muon.dynamic_difficulty.data.EntityLevelingSettingsReloader;
import dev.muon.dynamic_difficulty.network.NetworkDispatcher;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.packs.PackType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class LevelingEvents {
  private static final Map<UUID, ResourceLocation> playerStructureMap = new HashMap<>();

  public static void register() {
    // Entity join level - apply level bonuses
    ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
      if ((!(entity instanceof LivingEntity living)) || !LevelingAPI.canHaveLevel(living) || entity.level().isClientSide) {
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

    // Experience drop modification
    // Note: Fabric doesn't have a direct equivalent to LivingExperienceDropEvent
    // Experience modification should be handled via mixin to LivingEntity.getExperienceReward()
    // or by modifying the experience value before the entity dies
    // For now, this is handled via mixin - see LivingEntityMixin

    // Additional loot drops are handled via LootTableMixin
    // The mixin intercepts loot generation and checks the context to determine if it's an entity loot table

    // Reload listeners
    ResourceManagerHelper.get(PackType.SERVER_DATA)
        .registerReloadListener(new DimensionsLevelingSettingsReloader());
    ResourceManagerHelper.get(PackType.SERVER_DATA)
        .registerReloadListener(new EntityLevelingSettingsReloader());

    // Entity tracking - sync levels
    EntityTrackingEvents.START_TRACKING.register((trackedEntity, trackingPlayer) -> {
      if (!(trackedEntity instanceof LivingEntity living)) return;

      // Sync player levels to other players who start tracking them
      if (living instanceof ServerPlayer trackedPlayer) {
        NetworkDispatcher.syncLevelToPlayer(trackedPlayer, trackingPlayer);
        return;
      }

      // Sync mob/entity levels as normal
      if (LevelingAPI.hasLevel(living)) {
        NetworkDispatcher.syncLevelToPlayer(living, trackingPlayer);
      }
    });

    // Player login
    ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
      ServerPlayer player = handler.player;
      calculateAndSyncPlayerLevel(player);
    });

    // Player disconnect - cleanup server-side caches
    ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
      ServerPlayer player = handler.player;
      playerStructureMap.remove(player.getUUID());
      DynamicDifficulty.LOGGER.debug("Cleaned up structure tracking for disconnected player: {}", 
          player.getName().getString());
    });

    // Player respawn
    ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
      calculateAndSyncPlayerLevel(newPlayer);
    });

    // Player clone (death)
    ServerPlayerEvents.COPY_FROM.register((newPlayer, oldPlayer, alive) -> {
      if (!alive) {
        calculateAndSyncPlayerLevel(newPlayer);
      }
    });

    // Entity join level - sync to clients
    ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
      if (world.isClientSide() || !(entity instanceof LivingEntity living)) {
        return;
      }

      if (LevelingAPI.hasLevel(living)) {
        NetworkDispatcher.syncLevelToClients(living);
      }
    });

    // Player tick
    ServerTickEvents.END_SERVER_TICK.register(server -> {
      for (ServerPlayer player : server.getPlayerList().getPlayers()) {
        // Check every second to reduce performance impact
        if (player.tickCount % 20 == 0) {
          checkPlayerStructure(player);
        }
        
        // Fallback: Update player level periodically in case provider events are missed
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
    ServerLevel level = player.serverLevel();
    Registry<Structure> structureRegistry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
    
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
      
      // Send packet if entering a structure with bonus
      if (currentStructure != null && highestLevelBonus > 0) {
        // Calculate base level at this position (environmental factors)
        int baseLevel = calculateBaseEntityLevel(player, playerPos);
        
        // Calculate player-based bonus (get this player's level from providers)
        int playerBonus = 0;
        if (Config.COMMON.applyPlayerBasedLeveling.get()) {
          // Get the bonus that would apply to mobs from this player being nearby
          int rawBonus = PlayerLevelProvider.getProviders().stream()
                  .filter(PlayerLevelProvider::isEnabled)
                  .mapToInt(provider -> provider.calculateBonusLevels(java.util.List.of(player)))
                  .sum();
          
          // Apply the same multiplier used for mob leveling
          double multiplier = Config.COMMON.playerLevelMultiplier.get();
          playerBonus = (int) (rawBonus * multiplier);
          
          if (multiplier != 1.0 && rawBonus > 0) {
            DynamicDifficulty.LOGGER.debug("Structure notification player bonus scaled: {} * {} = {}", 
                rawBonus, multiplier, playerBonus);
          }
        }
        
        DynamicDifficulty.LOGGER.debug("Structure notification for {}: base={}, structure={}, player={}", 
            player.getName().getString(), baseLevel, highestLevelBonus, playerBonus);
        
        // Send the packet
        NetworkDispatcher.sendStructureEntry(player, currentStructure, highestLevelBonus, baseLevel, playerBonus);
      }
    }
  }
  
  private static int calculateBaseEntityLevel(ServerPlayer player, BlockPos pos) {
    // Calculate base level including all environmental factors
    ServerLevel level = player.serverLevel();
    BlockPos spawnPos = level.getSharedSpawnPos();
    double distance = Math.sqrt(spawnPos.distSqr(pos));
    
    int baseLevel = Config.COMMON.startingLevel.get();
    
    // Distance scaling
    baseLevel += (int)(distance * Config.COMMON.levelsPerDistance.get());
    
    // Depth scaling (below Y=63, sea level)
    int depth = Math.max(0, 63 - pos.getY());
    baseLevel += (int)(depth * Config.COMMON.levelsPerDeepness.get());
    
    // Day scaling
    long days = level.getDayTime() / 24000L;
    baseLevel += (int)(days * Config.COMMON.levelsPerDay.get());
    
    return Math.max(1, baseLevel);
  }
}
