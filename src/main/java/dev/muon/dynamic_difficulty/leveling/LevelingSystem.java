package dev.muon.dynamic_difficulty.leveling;

import dev.muon.dynamic_difficulty.DynamicDifficulty;
import dev.muon.dynamic_difficulty.api.LevelingAPI;
import dev.muon.dynamic_difficulty.api.PlayerLevelProvider;
import dev.muon.dynamic_difficulty.config.Config;
import dev.muon.dynamic_difficulty.data.DimensionsLevelingSettingsReloader;
import dev.muon.dynamic_difficulty.data.EntityLevelingSettingsReloader;
import dev.muon.dynamic_difficulty.settings.DimensionLevelingSettings;
import dev.muon.dynamic_difficulty.settings.LevelingSettings;
import dev.muon.dynamic_difficulty.util.LevelingUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

// Import ClientLevelCache for client-side checks
import dev.muon.dynamic_difficulty.client.ClientLevelCache;

import java.util.List;
import java.util.Map;

public class LevelingSystem {
    private static final String LEVEL_TAG = (DynamicDifficulty.MODID + ":level").toLowerCase();
    private static final TagKey<EntityType<?>> FIXED_LEVEL_ENTITIES = TagKey.create(Registries.ENTITY_TYPE,
            DynamicDifficulty.loc("fixed_level_entities"));

    public static boolean hasLevel(Entity entity) {
        if (entity.level().isClientSide()) {
            if (entity instanceof LivingEntity livingEntity) {
                return ClientLevelCache.getLevel(livingEntity) > 0; // Use ClientLevelCache on client
            }
            return false; // Non-living entities or other client-side cases
        } else {
            return entity.getPersistentData().contains(LEVEL_TAG); // Server-side logic remains
        }
    }

    public static int getLevel(LivingEntity entity) {
        if (entity.level().isClientSide()) {
            return ClientLevelCache.getLevel(entity); // Use ClientLevelCache on client
        } else {
            return entity.getPersistentData().getInt(LEVEL_TAG); // Server-side logic remains
        }
    }

    public static void setLevel(LivingEntity entity, int level) {
        entity.getPersistentData().putInt(LEVEL_TAG, level);
    }

    public static int createLevelForEntity(LivingEntity entity) {
        if (!LevelingAPI.canHaveLevel(entity)) return 0;

        if (entity.getType().is(FIXED_LEVEL_ENTITIES)) {
            return getFixedLevel(entity);
        }

        int baseLevel = calculateInitialLevel(entity);
        int totalBonusLevels = calculateBonusLevels(entity);

        return Math.max(0, baseLevel + totalBonusLevels);
    }

    private static int getFixedLevel(LivingEntity entity) {
        return getLevelingSettings(entity).startingLevel() - 1;
    }

    private static int calculateInitialLevel(LivingEntity entity) {
        LevelingSettings settings = getLevelingSettings(entity);
        BlockPos spawnPos = getSpawnPosition(entity);
        double distanceToSpawn = Math.sqrt(spawnPos.distSqr(entity.blockPosition()));

        int baseLevel = LevelingUtils.calculateDistanceFactors(entity, distanceToSpawn, settings);

        int randomBonus = settings.randomLevelBonus() + 1;
        if (randomBonus > 0) {
            baseLevel += entity.getRandom().nextInt(randomBonus);
        }

        baseLevel = Math.abs(baseLevel);
        int maxLevel = settings.maxLevel();
        if (maxLevel > 0) {
            baseLevel = Math.min(baseLevel, maxLevel - 1);
        }

        return baseLevel;
    }

    private static int calculateBonusLevels(LivingEntity entity) {
        int bonusLevels = 0;

        if (Config.COMMON.applyPlayerBasedLeveling.get() && entity.level() instanceof ServerLevel serverLevel) {
            bonusLevels += LevelingAPI.getLevelsFromNearbyPlayers(serverLevel, entity);
        }

        return bonusLevels;
    }

    public static void applyAllLevelAttributes(LivingEntity entity) {
        getAttributeBonuses(entity).forEach((attribute, modifier) ->
                applyAttributeBonus(entity, attribute, modifier));
    }

    public static Map<Attribute, AttributeModifier> getAttributeBonuses(LivingEntity entity) {
        LevelingSettings settings = getLevelingSettings(entity);
        return settings.attributeModifiers().isEmpty() ?
                Config.getAttributeBonuses() :
                settings.attributeModifiers();
    }

    private static void applyAttributeBonus(
            LivingEntity entity,
            Attribute attribute,
            AttributeModifier modifier) {
        AttributeInstance instance = entity.getAttribute(Holder.direct(attribute));
        if (instance == null) return;

        AttributeModifier existing = instance.getModifier(modifier.id());
        if (existing != null) {
            if (existing.amount() == modifier.amount()) return;
            instance.removeModifier(existing);
        }

        int level = getLevel(entity);
        double amount = modifier.amount() * level;
        AttributeModifier newModifier = new AttributeModifier(
                modifier.id(),
                amount,
                modifier.operation()
        );

        instance.addPermanentModifier(newModifier);

        if (attribute == Attributes.MAX_HEALTH) {
            entity.heal(entity.getMaxHealth());
        }
    }

    static BlockPos getSpawnPosition(LivingEntity entity) {
        ResourceKey<Level> dimension = entity.level().dimension();
        DimensionLevelingSettings settings = DimensionsLevelingSettingsReloader.get(dimension);
        return settings.spawnPosOverride() != null ?
                settings.spawnPosOverride() :
                entity.level().getSharedSpawnPos();
    }

    static LevelingSettings getLevelingSettings(LivingEntity entity) {
        LevelingSettings entitySettings = EntityLevelingSettingsReloader.get(entity.getType());
        if (entitySettings != null) return entitySettings;

        ResourceKey<Level> dimension = entity.level().dimension();
        return DimensionsLevelingSettingsReloader.get(dimension);
    }

    /**
     * Gets the level contribution from nearby players within configured radius
     */
    public static int getLevelsFromNearbyPlayers(ServerLevel level, LivingEntity entity) {
        if (!Config.COMMON.applyPlayerBasedLeveling.get()) return 0;

        double radius = Config.COMMON.playerLevelRadius.get();
        List<Player> nearbyPlayers = level.getEntitiesOfClass(Player.class,
                entity.getBoundingBox().inflate(radius));

        if (nearbyPlayers.isEmpty()) return 0;

        return PlayerLevelProvider.getProviders().stream()
                .filter(PlayerLevelProvider::isEnabled)
                .mapToInt(provider -> provider.calculateBonusLevels(nearbyPlayers))
                .sum();
    }

    /**
     * Gets the structure level bonus for an entity's current position
     */
    public static int getStructureLevelBonus(LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel serverLevel)) return 0;

        BlockPos pos = entity.blockPosition();
        Registry<Structure> structureRegistry = serverLevel.registryAccess()
                .registryOrThrow(Registries.STRUCTURE);

        for (Structure structure : structureRegistry) {
            StructureStart start = serverLevel.structureManager().getStructureAt(pos, structure);
            if (start != null && start.isValid()) {
                ResourceLocation structureId = structureRegistry.getKey(structure);
                if (structureId != null) {
                    return LevelingUtils.getStructureLevelBonus(structureId);
                }
            }
        }

        return 0;
    }
}