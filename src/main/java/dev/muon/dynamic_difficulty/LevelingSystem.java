package dev.muon.dynamic_difficulty;

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
import dev.muon.dynamic_difficulty.util.LocationBonusCache;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.ApiStatus;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 * - Level is used for display (shown above head, color-coding mob difficulty) and
 *   for calculating mob level bonuses based on nearby player proximity
 * - Player levels do NOT grant attribute bonuses
 * - Player levels are calculated from PlayerLevelProvider implementations (e.g., skill points)
 * - Cannot use setAndUpdateLevel() on players (throws exception)
 */
public class LevelingSystem {
    private static final TagKey<EntityType<?>> FIXED_LEVEL_ENTITIES = TagKey.create(Registries.ENTITY_TYPE,
            DynamicDifficulty.loc("fixed_level_entities"));

    public static boolean hasLevel(Entity entity) {
        if (entity instanceof LivingEntity living) {
            // Check if entity has attachment data explicitly set (not just default value)
            // Use getExistingDataOrNull to check if level was actually set, not just defaulted
            return living.getExistingDataOrNull(EntityLevelAttachment.LEVEL) != null;
        }
        return false;
    }

    /**
     * Gets the level of any living entity, including players.
     * 
     * IMPORTANT: Player levels are used for display (name tags, color-coding difficulty)
     * and for calculating mob level bonuses based on nearby player proximity.
     * Player levels do NOT grant attribute bonuses - players use the PlayerLevelProvider
     * system to contribute to mob difficulty, not to gain power themselves.
     * 
     * Non-player entity levels DO grant attribute bonuses and are used for combat scaling.
     * 
     * @param entity The entity to get the level for
     * @return The entity's level (1 if no level set)
     */
    public static int getLevel(LivingEntity entity) {
        // Use attachment on both client and server - it's the single source of truth
        // On client, if attachment hasn't been set yet (entity not synced), defaults to 1
        return entity.getData(EntityLevelAttachment.LEVEL);
    }

    /**
     * Internal: Sets the level attachment without updating attributes or syncing.
     * Use setAndUpdateLevel() for runtime level changes.
     */
    @ApiStatus.Internal
    public static void setLevelAttachment(LivingEntity entity, int level) {
        entity.setData(EntityLevelAttachment.LEVEL, level);
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
        setLevelAttachment(entity, newLevel);
        
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
        
        // Non-bypassing bonuses are applied before the cap, so they can be limited
        int nonBypassingBonuses = calculateNonBypassingBonuses(entity);
        baseLevel += nonBypassingBonuses;
        
        LevelingSettings settings = getLevelingSettings(entity);
        int maxLevel = settings.maxLevel();
        if (maxLevel > 1) {
            int originalLevel = baseLevel;
            baseLevel = Math.min(baseLevel, maxLevel);
            if (originalLevel != baseLevel) {
                DynamicDifficulty.LOGGER.debug("{} capped at max level: {} -> {}", 
                    entity.getType().getDescription().getString(), originalLevel, baseLevel);
            }
        }
        
        // Bypassing bonuses are applied after the cap, allowing them to exceed max level
        int bypassingBonuses = calculateBypassingBonuses(entity);
        int finalLevel = Math.max(1, baseLevel + bypassingBonuses);
        DynamicDifficulty.LOGGER.debug("{} level calculated: base={}, non-bypassing={}, capped={}, bypassing={}, final={}", 
            entity.getType().getDescription().getString(), 
            baseLevel - nonBypassingBonuses, nonBypassingBonuses, baseLevel, bypassingBonuses, finalLevel);
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
        
        return baseLevel;
    }

    /**
     * Calculates bonuses that do NOT bypass the max level cap.
     * These are applied before the cap check, so they can be limited by maxLevel.
     * Examples: biome bonuses with bypasses_cap=false, structure bonuses with bypasses_cap=false
     */
    private static int calculateNonBypassingBonuses(LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel serverLevel)) return 0;
        
        BlockPos pos = entity.blockPosition();
        BonusResults results = calculateLocationBonuses(serverLevel, pos);
        
        if (results.structureNonBypassing > 0 || results.biomeNonBypassing > 0) {
            DynamicDifficulty.LOGGER.debug("{} non-bypassing bonuses: biome={}, structure={}", 
                entity.getType().getDescription().getString(), results.biomeNonBypassing, results.structureNonBypassing);
        }
        
        return results.biomeNonBypassing + results.structureNonBypassing;
    }

    /**
     * Calculates bonuses that DO bypass the max level cap.
     * These are applied after the cap check, allowing them to exceed maxLevel.
     * Examples: structure bonuses with bypasses_cap=true, player bonuses, biome bonuses with bypasses_cap=true
     */
    private static int calculateBypassingBonuses(LivingEntity entity) {
        int bonusLevels = 0;
        int playerBonus = 0;
        
        if (Config.COMMON.applyPlayerBasedLeveling.get() && entity.level() instanceof ServerLevel serverLevel) {
            playerBonus = LevelingAPI.getLevelsFromNearbyPlayers(serverLevel, entity);
            bonusLevels += playerBonus;
        }
        
        // Calculate structure and biome bonuses
        if (entity.level() instanceof ServerLevel serverLevel) {
            BlockPos pos = entity.blockPosition();
            BonusResults results = calculateLocationBonuses(serverLevel, pos);
            bonusLevels += results.structureBypassing + results.biomeBypassing;
            
            if (playerBonus > 0 || results.structureBypassing > 0 || results.biomeBypassing > 0) {
                DynamicDifficulty.LOGGER.debug("{} bypassing bonuses: player={}, structure={}, biome={}, total={}", 
                    entity.getType().getDescription().getString(), playerBonus, results.structureBypassing, results.biomeBypassing, bonusLevels);
            }
        }
        
        return bonusLevels;
    }
    
    /**
     * Calculates both structure and biome bonuses using the shared cache.
     * The cache handles computation and caching internally with proper granularity
     * (chunk-level for structures, 4x4x4 for biomes) and LRU eviction with TTL.
     */
    private static BonusResults calculateLocationBonuses(ServerLevel serverLevel, BlockPos pos) {
        LocationBonusCache.StructureBonusResult structureResult = LocationBonusCache.getStructureBonuses(serverLevel, pos);
        LocationBonusCache.BiomeBonusResult biomeResult = LocationBonusCache.getBiomeBonuses(serverLevel, pos);
        
        return new BonusResults(
            structureResult.nonBypassingBonus(), structureResult.bypassingBonus(),
            biomeResult.nonBypassingBonus(), biomeResult.bypassingBonus()
        );
    }
    
    /**
     * Helper record to hold bonus calculation results.
     */
    private record BonusResults(
        int structureNonBypassing,
        int structureBypassing,
        int biomeNonBypassing,
        int biomeBypassing
    ) {}
    

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
     * Gets the level contribution from nearby players within configured radius.
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
     * Gets the structure level bonus for an entity's current position.
     */
    public static int getStructureLevelBonus(LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel serverLevel)) return 0;
        
        LocationBonusCache.StructureBonusResult result = LocationBonusCache.getStructureBonuses(serverLevel, entity.blockPosition());
        return result.totalBonus();
    }

    /**
     * Gets the biome level bonus for an entity's current position.
     */
    public static int getBiomeLevelBonus(LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel serverLevel)) return 0;
        
        LocationBonusCache.BiomeBonusResult result = LocationBonusCache.getBiomeBonuses(serverLevel, entity.blockPosition());
        return result.totalBonus();
    }
}