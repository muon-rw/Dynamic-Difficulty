package dev.muon.dynamic_difficulty;

import dev.muon.dynamic_difficulty.api.LevelingAPI;
import dev.muon.dynamic_difficulty.config.Config;
import dev.muon.dynamic_difficulty.data.DimensionsLevelingSettingsReloader;
import dev.muon.dynamic_difficulty.attribute.ModAttributes;
import dev.muon.dynamic_difficulty.data.EntityLevelingSettingsReloader;
import dev.muon.dynamic_difficulty.mixin.LivingEntityAccessor;
import dev.muon.dynamic_difficulty.network.NetworkDispatcher;
import dev.muon.dynamic_difficulty.network.message.SyncLevelingData;

import java.util.Objects;
import javax.annotation.Nonnull;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;

@EventBusSubscriber(modid = DynamicDifficulty.MODID)
public class MobsLevelingEvents {
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
    ResourceLocation lootTableId =
        new ResourceLocation(DynamicDifficulty.MODID, "gameplay/leveled_mobs");
    MinecraftServer server = entity.level().getServer();
    if (server == null) return;
    LootTable lootTable = server.getLootData().getLootTable(lootTableId);
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
    if (!LevelingAPI.hasLevel(event.getTarget())) return;
    LivingEntity entity = (LivingEntity) event.getTarget();
    ServerPlayer player = (ServerPlayer) event.getEntity();
    PacketDistributor.PacketTarget packetTarget = PacketDistributor.PLAYER.with(() -> player);
    NetworkDispatcher.CHANNEL.send(packetTarget, new SyncLevelingData(entity));
  }

  @SubscribeEvent
  public static void applyAttributesDamageBonus(LivingHurtEvent event) {
    DamageSource damage = event.getSource();
    if (!(damage.getEntity() instanceof LivingEntity attacker)) return;
    float multiplier = getDamageMultiplier(damage, attacker);
    if (multiplier > 1F) event.setAmount(event.getAmount() * multiplier);
  }

  public static float getDamageMultiplier(DamageSource damage, LivingEntity attacker) {
    if (damage.is(DamageTypeTags.IS_PROJECTILE)) {
      return getAttributeValue(attacker, ModAttributes.PROJECTILE_DAMAGE_MULTIPLIER.get());
    }
    if (damage.is(DamageTypeTags.IS_EXPLOSION)) {
      return getAttributeValue(attacker, ModAttributes.EXPLOSION_DAMAGE_MULTIPLIER.get());
    }
    return 1F;
  }

  private static float getAttributeValue(LivingEntity entity, Attribute damageBonusAttribute) {
    if (entity.getAttribute(damageBonusAttribute) == null) return 0F;
    return (float) Objects.requireNonNull(entity.getAttribute(damageBonusAttribute)).getValue();
  }

  @OnlyIn(Dist.CLIENT)
  public static boolean shouldShowName(LivingEntity entity) {
    Minecraft minecraft = Minecraft.getInstance();
    LocalPlayer clientPlayer = minecraft.player;

    // Initial checks from original method
    if (clientPlayer == null) return false;
    if (!Minecraft.renderNames()) return false;
    if (entity.isVehicle()) return false;
    if (entity == minecraft.getCameraEntity()) return false;

    // Line of sight and invisibility (occlusion) - UNCOMMENTED
    if (!clientPlayer.hasLineOfSight(entity) || entity.isInvisibleTo(clientPlayer)) return false;

    // Leveling API checks
    if (!LevelingAPI.hasLevel(entity)) {
        // This check should now work correctly on the client due to LevelingSystem changes
        return false; 
    }

    // UNCOMMENTED
    if (!LevelingAPI.shouldShowLevel(entity)) return false; // Uses LevelingUtils which checks hiddenLevelEntities

    // Config: Max Render Distance - UNCOMMENTED
    double maxDistSq = Config.CLIENT.renderDistance.get() * Config.CLIENT.renderDistance.get();
    if (entity.distanceToSqr(clientPlayer) > maxDistSq) {
      return false;
    }

    // Config: Render Behavior
    Config.RenderBehavior behavior = Config.CLIENT.renderBehavior.get();
    switch (behavior) {
      case NEVER:
        return false;
      case ALWAYS:
        return true;
      case LOOKING_AT:
        HitResult hitResult = minecraft.hitResult;
        if (hitResult != null && hitResult.getType() == HitResult.Type.ENTITY) {
          EntityHitResult entityHitResult = (EntityHitResult) hitResult;
          return entityHitResult.getEntity() == entity;
        }
        return false; 
      default:
        return false; 
    }
  }

  @SubscribeEvent
  public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
    if (!event.getEntity().level().isClientSide()) {
      NetworkDispatcher.syncLevelToAll((LivingEntity)event.getEntity());
    }
  }

  @SubscribeEvent
  public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
    if (!event.getEntity().level().isClientSide()) {
      NetworkDispatcher.syncLevelToAll((LivingEntity)event.getEntity());
    }
  }

  @SubscribeEvent
  public static void onPlayerChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
    if (!event.getEntity().level().isClientSide()) {
      NetworkDispatcher.syncLevelToAll((LivingEntity)event.getEntity());
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
    ResourceLocation lootTableId = getEquipmentTableId(slot, entityId);
    return server.getLootData().getLootTable(lootTableId);
  }

  @Nonnull
  private static ResourceLocation getEquipmentTableId(
      EquipmentSlot slot, ResourceLocation entityId) {
    String path = "equipment/" + entityId.getPath() + "_" + slot.getName();
    return new ResourceLocation(entityId.getNamespace(), path);
  }

  private static LootParams createLootParams(LivingEntity entity, DamageSource damageSource) {
    LivingEntityAccessor accessor = (LivingEntityAccessor) entity;
    ServerLevel level = (ServerLevel) entity.level();
    LootParams.Builder builder =
        new LootParams.Builder(level)
            .withParameter(LootContextParams.THIS_ENTITY, entity)
            .withParameter(LootContextParams.ORIGIN, entity.position())
            .withParameter(LootContextParams.DAMAGE_SOURCE, damageSource)
            .withOptionalParameter(LootContextParams.KILLER_ENTITY, damageSource.getEntity())
            .withOptionalParameter(
                LootContextParams.DIRECT_KILLER_ENTITY, damageSource.getDirectEntity());
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
        .create(LootContextParamSets.SELECTOR);
  }
}
