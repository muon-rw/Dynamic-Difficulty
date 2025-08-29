package dev.muon.dynamic_difficulty.leveling;

import dev.muon.dynamic_difficulty.DynamicDifficulty;
import dev.muon.dynamic_difficulty.api.LevelingAPI;
import dev.muon.dynamic_difficulty.config.Config;
import dev.muon.dynamic_difficulty.data.DimensionsLevelingSettingsReloader;
import dev.muon.dynamic_difficulty.data.EntityLevelingSettingsReloader;
import dev.muon.dynamic_difficulty.mixin.LivingEntityAccessor;
import dev.muon.dynamic_difficulty.network.NetworkDispatcher;
import dev.muon.dynamic_difficulty.network.message.SyncLevelingData;
import dev.muon.dynamic_difficulty.util.LevelingUtils;

import javax.annotation.Nonnull;

import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = DynamicDifficulty.MODID)
public class LevelingEvents {
  private static final String LEVEL_TAG = "LEVEL";
  private static final Map<UUID, ResourceLocation> playerStructureMap = new HashMap<>();


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
    LevelingAPI.setLevel(living, level);
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
        ResourceLocation.fromNamespaceAndPath(DynamicDifficulty.MODID, "gameplay/leveled_mobs");
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
    event.addListener(new EntityLevelingSettingsReloader());
  }

  @SubscribeEvent
  public static void syncEntityLevel(PlayerEvent.StartTracking event) {
    if (!(event.getTarget() instanceof LivingEntity trackedEntity) || !LevelingAPI.hasLevel(trackedEntity)) return;
    if (!(event.getEntity() instanceof ServerPlayer player)) return;

    PacketDistributor.sendToPlayer(player, new SyncLevelingData(trackedEntity));
  }

  @SubscribeEvent
  public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
    if (!event.getEntity().level().isClientSide()) {
        NetworkDispatcher.syncLevelToAllPlayers(event.getEntity());
    }
  }

  @SubscribeEvent
  public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
    if (!event.getEntity().level().isClientSide()) {
      NetworkDispatcher.syncLevelToAllPlayers(event.getEntity());
    }
  }

  @SubscribeEvent
  public static void onPlayerChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
    if (!event.getEntity().level().isClientSide()) {
      NetworkDispatcher.syncLevelToAllPlayers(event.getEntity());
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
    if (event.getEntity() instanceof ServerPlayer player && event.getEntity().tickCount % 20 == 0) {
      // Check every second to reduce performance impact
      checkPlayerStructure(player);
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
          int levelBonus = LevelingUtils.getStructureLevelBonus(structureId, structureRegistry);
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
        // Calculate base level at this position
        int baseLevel = calculateBaseEntityLevel(player, playerPos);
        
        // Send the packet
        NetworkDispatcher.sendStructureEntry(player, currentStructure, highestLevelBonus, baseLevel);
      }
    }
  }
  
  private static int calculateBaseEntityLevel(ServerPlayer player, BlockPos pos) {
    // Simple calculation - just using starting level and distance
    BlockPos spawnPos = player.serverLevel().getSharedSpawnPos();
    double distance = Math.sqrt(spawnPos.distSqr(pos));
    
    int baseLevel = Config.COMMON.startingLevel.get();
    baseLevel += (int)(distance * Config.COMMON.levelsPerDistance.get());
    
    return Math.max(1, baseLevel);
  }
}
