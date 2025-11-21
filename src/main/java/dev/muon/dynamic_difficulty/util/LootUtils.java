package dev.muon.dynamic_difficulty.util;

import dev.muon.dynamic_difficulty.mixin.LivingEntityAccessor;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/**
 * Utility methods for handling loot tables and equipment for leveled entities.
 */
public class LootUtils {

    /**
     * Adds equipment to an entity based on its level and entity type.
     */
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

    /**
     * Gets the equipment loot table for a specific slot and entity type.
     */
    private static LootTable getEquipmentLootTableForSlot(
            MinecraftServer server, LivingEntity entity, EquipmentSlot slot) {
        ResourceKey<net.minecraft.world.entity.EntityType<?>> entityTypeKey = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getResourceKey(entity.getType()).orElse(null);
        if (entityTypeKey == null) return LootTable.EMPTY;
        
        net.minecraft.resources.ResourceLocation entityId = entityTypeKey.location();
        net.minecraft.resources.ResourceLocation lootTableIdRL = getEquipmentTableId(slot, entityId);
        ResourceKey<LootTable> lootTableKey = ResourceKey.create(Registries.LOOT_TABLE, lootTableIdRL);
        return server.reloadableRegistries().getLootTable(lootTableKey);
    }

    /**
     * Generates the equipment loot table ID for a slot and entity type.
     */
    private static net.minecraft.resources.ResourceLocation getEquipmentTableId(
            EquipmentSlot slot, net.minecraft.resources.ResourceLocation entityId) {
        String path = "equipment/" + entityId.getPath() + "_" + slot.getName();
        return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(entityId.getNamespace(), path);
    }

    /**
     * Creates loot parameters for entity death/drops.
     */
    public static LootParams createLootParams(LivingEntity entity, DamageSource damageSource) {
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

    /**
     * Creates loot parameters for equipment generation.
     */
    private static LootParams createEquipmentLootParams(LivingEntity entity) {
        return new LootParams.Builder((ServerLevel) entity.level())
                .withParameter(LootContextParams.THIS_ENTITY, entity)
                .withParameter(LootContextParams.ORIGIN, entity.position())
                .create(LootContextParamSets.ENTITY);
    }
}

