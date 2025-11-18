package dev.muon.dynamic_difficulty.leveling;

import dev.muon.dynamic_difficulty.DynamicDifficulty;
import dev.muon.dynamic_difficulty.api.LevelingAPI;
import dev.muon.dynamic_difficulty.api.PlayerLevelProvider;
import dev.muon.dynamic_difficulty.config.Config;
import dev.muon.dynamic_difficulty.data.DimensionsLevelingSettingsReloader;
import dev.muon.dynamic_difficulty.data.EntityLevelingSettingsReloader;
import dev.muon.dynamic_difficulty.network.NetworkDispatcher;
import dev.muon.dynamic_difficulty.settings.DimensionLevelingSettings;
import dev.muon.dynamic_difficulty.settings.EntityLevelingSettings;
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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

// Import ClientLevelCache for client-side checks
import dev.muon.dynamic_difficulty.client.ClientLevelCache;

import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.minecraft.world.entity.Entity;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;

/**
 * Core leveling system for Dynamic Difficulty.
 * 
 * IMPORTANT: This system handles levels differently for players vs non-players:
 * 
 * MOBS/ENTITIES:
 * - Level determines combat power (health, damage, armor, etc.)
 * - Levels are calculated on spawn based on location, structures, nearby players
 * - Attribute bonuses are applied based on level
 * - Level can be changed at runtime via setAndUpdateLevel() or addLevels()
 * 
 * PLAYERS:
 * - Level is for DISPLAY ONLY (shown above head, used for color-coding mob difficulty)
 * - Player levels do NOT grant attribute bonuses
 * - Player levels are calculated from PlayerLevelProvider implementations (e.g., skill points)
 * - Players affect mob difficulty through proximity, not by gaining personal power
 * - Cannot use setAndUpdateLevel() on players (throws exception)
 */
public class LevelingSystem {
    private static final TagKey<EntityType<?>> FIXED_LEVEL_ENTITIES = TagKey.create(Registries.ENTITY_TYPE,
            DynamicDifficulty.loc("fixed_level_entities"));

    public static boolean hasLevel(Entity entity) {
        if (entity.level().isClientSide()) {
            // On client, we consider an entity to have a level if it's a living entity that can have levels
            // The actual level value will come from ClientLevelCache once synced
            return entity instanceof LivingEntity && LevelingUtils.canHaveLevel(entity);
        } else {
            return entity.hasAttached(EntityLevelAttachment.LEVEL);
        }
    }

    /**
     * Gets the level of any living entity, including players.
     * 
     * IMPORTANT: Player levels are for DISPLAY ONLY (name tags, color-coding difficulty).
     * Player levels do NOT grant attribute bonuses - players use the PlayerLevelProvider
     * system to contribute to mob difficulty, not to gain power themselves.
     * 
     * Non-player entity levels DO grant attribute bonuses and are used for combat scaling.
     * 
     * @param entity The entity to get the level for
     * @return The entity's level (1 if no level set)
     */
    public static int getLevel(LivingEntity entity) {
        if (entity.level().isClientSide()) {
            return ClientLevelCache.getLevel(entity);
        } else {
            Integer level = entity.getAttached(EntityLevelAttachment.LEVEL);
            return level != null ? level : 1;
        }
    }

    /**
     * Internal: Sets the level attachment without updating attributes or syncing.
     * Use setAndUpdateLevel() for runtime level changes.
     */
    static void setLevelTag(LivingEntity entity, int level) {
        if (entity instanceof AttachmentTarget target) {
            target.setAttached(EntityLevelAttachment.LEVEL, level);
        }
    }

    /**
     * Sets an entity's level, updates its attributes, and syncs to clients.
     * This is the proper way to change an entity's level at runtime.
     * 
     * @param entity The entity to level up/down (must not be a player)
     * @param newLevel The new level to set
     * @throws IllegalArgumentException if entity is a player or level is negative
     */
    public static void setAndUpdateLevel(LivingEntity entity, int newLevel) {
        if (entity instanceof ServerPlayer) {
            throw new IllegalArgumentException("Cannot set levels on players - use PlayerLevelProvider system instead");
        }
        
        if (newLevel < 0) {
            throw new IllegalArgumentException("Level cannot be negative");
        }
        
        if (!LevelingAPI.canHaveLevel(entity)) {
            throw new IllegalArgumentException("Entity type " + entity.getType().getDescription().getString() + " cannot have levels");
        }
        
        int oldLevel = getLevel(entity);
        setLevelTag(entity, newLevel);
        
        // Reapply all attribute bonuses with new level
        applyAllLevelAttributes(entity);
        
        // Sync to tracking clients if on server
        if (entity.level() instanceof ServerLevel) {
            NetworkDispatcher.syncLevelToClients(entity);
        }
        
        DynamicDifficulty.LOGGER.debug("{} level changed: {} -> {}", 
            entity.getType().getDescription().getString(), oldLevel, newLevel);
    }

    /**
     * Adds levels to an entity (can be negative to subtract).
     * Updates attributes and syncs to clients.
     * 
     * @param entity The entity to level up/down (must not be a player)
     * @param levelsToAdd How many levels to add (negative to subtract)
     * @throws IllegalArgumentException if entity is a player
     */
    public static void addLevels(LivingEntity entity, int levelsToAdd) {
        int currentLevel = getLevel(entity);
        int newLevel = Math.max(1, currentLevel + levelsToAdd);
        setAndUpdateLevel(entity, newLevel);
    }

    public static int createLevelForEntity(LivingEntity entity) {
        if (!LevelingAPI.canHaveLevel(entity)) {
            return 1;
        }

        if (entity.getType().is(FIXED_LEVEL_ENTITIES)) {
            int fixedLevel = getFixedLevel(entity);
            DynamicDifficulty.LOGGER.debug("{} has fixed level: {}", 
                entity.getType().getDescription().getString(), fixedLevel);
            return fixedLevel;
        }

        int baseLevel = calculateInitialLevel(entity);
        int totalBonusLevels = calculateBonusLevels(entity);
        int finalLevel = Math.max(1, baseLevel + totalBonusLevels);

        DynamicDifficulty.LOGGER.debug("{} level calculated: base={}, bonuses={}, final={}", 
            entity.getType().getDescription().getString(), baseLevel, totalBonusLevels, finalLevel);

        return finalLevel;
    }

    private static int getFixedLevel(LivingEntity entity) {
        return getLevelingSettings(entity).startingLevel();
    }

    private static int calculateInitialLevel(LivingEntity entity) {
        LevelingSettings settings = getLevelingSettings(entity);
        BlockPos spawnPos = getSpawnPosition(entity);
        double distanceToSpawn = Math.sqrt(spawnPos.distSqr(entity.blockPosition()));

        int startingLevel = settings.startingLevel();
        int baseLevel = startingLevel;

        int distanceBonus = LevelingUtils.calculateDistanceFactors(entity, distanceToSpawn, settings);
        baseLevel += distanceBonus;

        int randomBonus = 0;
        int randomBonusValue = settings.randomLevelBonus();
        if (randomBonusValue > 0) { 
            randomBonus = entity.getRandom().nextInt(randomBonusValue + 1);
            baseLevel += randomBonus;
        }

        DynamicDifficulty.LOGGER.debug("{} base factors: starting={}, distance={}, random={}, subtotal={}", 
            entity.getType().getDescription().getString(), 
            startingLevel, distanceBonus, randomBonus, baseLevel);

        baseLevel = Math.max(1, baseLevel);

        int maxLevel = settings.maxLevel();
        if (maxLevel > 1) {
            int originalLevel = baseLevel;
            baseLevel = Math.min(baseLevel, maxLevel);
            if (originalLevel != baseLevel) {
                DynamicDifficulty.LOGGER.debug("{} capped at max level: {} -> {}", 
                    entity.getType().getDescription().getString(), originalLevel, baseLevel);
            }
        }
        return baseLevel;
    }

    private static int calculateBonusLevels(LivingEntity entity) {
        int bonusLevels = 0;
        int playerBonus = 0;
        int structureBonus = 0;

        if (Config.COMMON.applyPlayerBasedLeveling.get() && entity.level() instanceof ServerLevel serverLevel) {
            playerBonus = LevelingAPI.getLevelsFromNearbyPlayers(serverLevel, entity);
            bonusLevels += playerBonus;
        }

        structureBonus = LevelingAPI.getStructureLevelBonus(entity);
        bonusLevels += structureBonus;

        if (playerBonus > 0 || structureBonus > 0) {
            DynamicDifficulty.LOGGER.debug("{} bonus levels: player={}, structure={}, total={}", 
                entity.getType().getDescription().getString(), playerBonus, structureBonus, bonusLevels);
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

        // Check if we have entity-specific settings with non-empty modifiers
        if (settings instanceof EntityLevelingSettings entitySettings) {
            Map<Attribute, AttributeModifier> entityModifiers = entitySettings.attributeModifiers();
            if (entityModifiers != null && !entityModifiers.isEmpty()) {
                modifiersToUse = convertAttributeMapToKeyMap(entityModifiers);
            } else {
                // Entity settings exist but modifiers are empty/null, check dimension settings
                ResourceKey<Level> dimension = entity.level().dimension();
                DimensionLevelingSettings dimSettings = DimensionsLevelingSettingsReloader.get(dimension);
                Map<Attribute, AttributeModifier> dimModifiers = dimSettings.attributeModifiers();
                
                if (dimModifiers != null && !dimModifiers.isEmpty()) {
                    modifiersToUse = convertAttributeMapToKeyMap(dimModifiers);
                } else {
                    // Fall back to global config
                    modifiersToUse = Config.getAttributeBonuses();
                }
            }
        } else if (settings instanceof DimensionLevelingSettings dimSettings) {
            Map<Attribute, AttributeModifier> dimModifiers = dimSettings.attributeModifiers();
            if (dimModifiers != null && !dimModifiers.isEmpty()) {
                modifiersToUse = convertAttributeMapToKeyMap(dimModifiers);
            } else {
                // Dimension settings exist but modifiers are empty/null, use global config
                modifiersToUse = Config.getAttributeBonuses();
            }
        } else {
            // No specific settings, use global config
            modifiersToUse = Config.getAttributeBonuses();
        }
        
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

        AttributeInstance instance = entity.getAttribute(attributeHolder);

        if (instance == null) {
            return;
        }
        instance.removeModifier(modifier.id());

        int level = getLevel(entity);

        if (level == 1 || modifier.amount() == 0) {
            return;
        }

        double scaledAmount = modifier.amount() * level;

        AttributeModifier newModifier = new AttributeModifier(modifier.id(), scaledAmount, modifier.operation());

        instance.addPermanentModifier(newModifier);

        if (attributeHolder == Attributes.MAX_HEALTH) {
            entity.setHealth(entity.getMaxHealth());
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
        if (entitySettings != null) {
            return entitySettings;
        }

        ResourceKey<Level> dimension = entity.level().dimension();
        DimensionLevelingSettings dimSettings = DimensionsLevelingSettingsReloader.get(dimension);
        return dimSettings;
    }

    /**
     * Gets the level contribution from nearby players within configured radius
     */
    public static int getLevelsFromNearbyPlayers(ServerLevel level, LivingEntity entity) {
        if (!Config.COMMON.applyPlayerBasedLeveling.get()) {
            DynamicDifficulty.LOGGER.debug("Player-based leveling disabled in config");
            return 0;
        }

        double radius = Config.COMMON.playerLevelRadius.get();
        List<ServerPlayer> nearbyPlayers = level.getEntitiesOfClass(ServerPlayer.class,
                entity.getBoundingBox().inflate(radius));

        if (nearbyPlayers.isEmpty()) {
            DynamicDifficulty.LOGGER.debug("No players within {} blocks of {}", radius, 
                entity.getType().getDescription().getString());
            return 0;
        }

        DynamicDifficulty.LOGGER.debug("Found {} players near {}: {}", 
            nearbyPlayers.size(), 
            entity.getType().getDescription().getString(),
            nearbyPlayers.stream().map(p -> p.getName().getString()).toList());

        int total = PlayerLevelProvider.getProviders().stream()
                .filter(PlayerLevelProvider::isEnabled)
                .mapToInt(provider -> {
                    int bonus = provider.calculateBonusLevels(nearbyPlayers);
                    DynamicDifficulty.LOGGER.debug("Provider {} calculated bonus: {}", 
                        provider.getClass().getSimpleName(), bonus);
                    return bonus;
                })
                .sum();

        DynamicDifficulty.LOGGER.debug("Total player bonus from {} providers: {}", 
            PlayerLevelProvider.getProviders().size(), total);

        // Apply global multiplier for tuning difficulty
        double multiplier = Config.COMMON.playerLevelMultiplier.get();
        int scaledBonus = (int) (total * multiplier);
        
        if (multiplier != 1.0) {
            DynamicDifficulty.LOGGER.debug("Player bonus scaled: {} * {} = {}", 
                total, multiplier, scaledBonus);
        }

        return scaledBonus;
    }

    /**
     * Gets the structure level bonus for an entity's current position
     */
    public static int getStructureLevelBonus(LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel serverLevel)) return 0;

        BlockPos pos = entity.blockPosition();
        Registry<Structure> structureRegistry = serverLevel.registryAccess()
                .registryOrThrow(Registries.STRUCTURE);

        int highestBonus = 0;
        for (Structure structure : structureRegistry) {
            StructureStart start = serverLevel.structureManager().getStructureAt(pos, structure);
            if (start != null && start.isValid()) {
                ResourceLocation structureId = structureRegistry.getKey(structure);
                if (structureId != null) {
                    int bonus = LevelingAPI.getStructureLevelBonus(structureId, structureRegistry);
                    highestBonus = Math.max(highestBonus, bonus);
                }
            }
        }

        return highestBonus;
    }
}