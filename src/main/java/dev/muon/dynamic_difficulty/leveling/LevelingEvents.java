package dev.muon.dynamic_difficulty.leveling;

import dev.muon.dynamic_difficulty.DynamicDifficulty;
import dev.muon.dynamic_difficulty.api.LevelingAPI;
import dev.muon.dynamic_difficulty.config.Config;
import dev.muon.dynamic_difficulty.data.DimensionsLevelingSettingsReloader;
import dev.muon.dynamic_difficulty.attribute.ModAttributes;
import dev.muon.dynamic_difficulty.data.EntityLevelingSettingsReloader;
import dev.muon.dynamic_difficulty.mixin.LivingEntityAccessor;
import dev.muon.dynamic_difficulty.network.NetworkDispatcher;
import dev.muon.dynamic_difficulty.network.message.SyncLevelingData;

import javax.annotation.Nonnull;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = DynamicDifficulty.MODID)
public class LevelingEvents {
  private static final String LEVEL_TAG = "LEVEL";


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
  public static void applyAttributesDamageBonus(LivingDamageEvent.Pre event) {
    DamageSource damage = event.getSource();
    if (!(damage.getEntity() instanceof LivingEntity attacker)) return;
    float bonus = getAdditionalDamage(damage, attacker);
    event.setNewDamage(event.getNewDamage() + bonus);
  }

  public static float getAdditionalDamage(DamageSource damage, LivingEntity attacker) {
    if (damage.is(DamageTypeTags.IS_PROJECTILE)) {
      return getAttributeValue(attacker, ModAttributes.PROJECTILE_DAMAGE_ADDITION.get());
    }
    if (damage.is(DamageTypeTags.IS_EXPLOSION)) {
      return getAttributeValue(attacker, ModAttributes.EXPLOSION_DAMAGE_ADDITION.get());
    }
    return 0;
  }

  private static float getAttributeValue(LivingEntity entity, Attribute damageBonusAttribute) {
    var attributeInstance = entity.getAttribute(Holder.direct(damageBonusAttribute));
    if (attributeInstance == null) return 1F;
    return (float) attributeInstance.getValue();
  }

  @SubscribeEvent
  public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
    if (!event.getEntity().level().isClientSide() && event.getEntity() instanceof LivingEntity livingEntity) {
      NetworkDispatcher.syncLevelToAllPlayers(livingEntity);
    }
  }

  @SubscribeEvent
  public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
    if (!event.getEntity().level().isClientSide() && event.getEntity() instanceof LivingEntity livingEntity) {
      NetworkDispatcher.syncLevelToAllPlayers(livingEntity);
    }
  }

  @SubscribeEvent
  public static void onPlayerChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
    if (!event.getEntity().level().isClientSide() && event.getEntity() instanceof LivingEntity livingEntity) {
      NetworkDispatcher.syncLevelToAllPlayers(livingEntity);
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
}
