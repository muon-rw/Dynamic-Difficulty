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
import java.util.HashMap;
import java.util.Optional;

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

        int baseLevel = settings.startingLevel();

        baseLevel += LevelingUtils.calculateDistanceFactors(entity, distanceToSpawn, settings);

        int randomBonusValue = settings.randomLevelBonus();
        if (randomBonusValue > 0) { 
            baseLevel += entity.getRandom().nextInt(randomBonusValue + 1); 
        }

        baseLevel = Math.max(0, baseLevel); 

        int maxLevel = settings.maxLevel();
        if (maxLevel > 0) {
            baseLevel = Math.min(baseLevel, maxLevel);
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
        getAttributeBonuses(entity).forEach((attributeKey, modifier) -> {
            Optional<? extends Holder<Attribute>> optAttributeHolder = BuiltInRegistries.ATTRIBUTE.getHolder(attributeKey);
            if (optAttributeHolder.isPresent()) {
                applyAttributeBonus(entity, optAttributeHolder.get(), modifier);
            } else {
                DynamicDifficulty.LOGGER.warn("Entity {}: Could not find attribute holder for key {} when applying all attributes.", 
                                            EntityType.getKey(entity.getType()), attributeKey.location());
            }
        });
    }

    public static Map<ResourceKey<Attribute>, AttributeModifier> getAttributeBonuses(LivingEntity entity) {
        LevelingSettings settings = getLevelingSettings(entity);
        Map<ResourceKey<Attribute>, AttributeModifier> modifiersToUse;
        String sourceName = "Unknown"; // Initialize sourceName

        if (settings instanceof dev.muon.dynamic_difficulty.settings.EntityLevelingSettings && !settings.attributeModifiers().isEmpty()) {
            modifiersToUse = convertAttributeMapToKeyMap(settings.attributeModifiers());
            sourceName = "EntitySpecific (" + EntityType.getKey(entity.getType()) + ".json)";
        } else if (settings instanceof dev.muon.dynamic_difficulty.settings.DimensionLevelingSettings && !settings.attributeModifiers().isEmpty()) {
            modifiersToUse = convertAttributeMapToKeyMap(settings.attributeModifiers());
            sourceName = "DimensionSpecific (" + entity.level().dimension().location() + ".json)";
        } else {
            modifiersToUse = Config.getAttributeBonuses();
            sourceName = "GlobalConfig";
        }
        DynamicDifficulty.LOGGER.info("Entity {}: Using attribute modifiers from {} ({} modifiers found)", EntityType.getKey(entity.getType()), sourceName, modifiersToUse.size());
        return modifiersToUse;
    }

    // Helper method to convert Map<Attribute, AttributeModifier> to Map<ResourceKey<Attribute>, AttributeModifier>
    private static Map<ResourceKey<Attribute>, AttributeModifier> convertAttributeMapToKeyMap(Map<Attribute, AttributeModifier> attributeMap) {
        Map<ResourceKey<Attribute>, AttributeModifier> keyMap = new HashMap<>();
        for (Map.Entry<Attribute, AttributeModifier> entry : attributeMap.entrySet()) {
            BuiltInRegistries.ATTRIBUTE.getResourceKey(entry.getKey())
                .ifPresent(key -> keyMap.put(key, entry.getValue()));
        }
        return keyMap;
    }

    private static void applyAttributeBonus(
            LivingEntity entity,
            Holder<Attribute> attributeHolder,
            AttributeModifier modifier) {
        Optional<ResourceKey<Attribute>> optAttributeKey = attributeHolder.unwrapKey();

        AttributeInstance instance = entity.getAttribute(attributeHolder);

        if (instance == null) {
            return;
        }
        instance.removeModifier(modifier.id());

        int level = getLevel(entity);

        if (level == 0 || modifier.amount() == 0) {
            return;
        }

        double scaledAmount = modifier.amount() * level;

        AttributeModifier newModifier = new AttributeModifier(modifier.id(), scaledAmount, modifier.operation());

        instance.addPermanentModifier(newModifier);

        Optional<ResourceKey<Attribute>> maxHealthResKeyOpt = Attributes.MAX_HEALTH.unwrapKey();
        if (maxHealthResKeyOpt.isPresent() && optAttributeKey.isPresent() && maxHealthResKeyOpt.get().location().equals(optAttributeKey.get().location())) {
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