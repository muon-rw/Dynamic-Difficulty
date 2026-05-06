package dev.muon.dynamic_difficulty;

import dev.muon.dynamic_difficulty.api.BiomeBonus;
import dev.muon.dynamic_difficulty.api.LevelingAPI;
import dev.muon.dynamic_difficulty.api.PlayerLevelProvider;
import dev.muon.dynamic_difficulty.api.StructureBonus;
import dev.muon.dynamic_difficulty.config.ConfigSync;
import dev.muon.dynamic_difficulty.config.Configs;
import dev.muon.dynamic_difficulty.data.DimensionsLevelingSettingsReloader;
import dev.muon.dynamic_difficulty.data.EntityLevelingSettingsReloader;
import dev.muon.dynamic_difficulty.network.NetworkDispatcher;
import dev.muon.dynamic_difficulty.settings.DimensionLevelingSettings;
import dev.muon.dynamic_difficulty.settings.EntityLevelingSettings;
import dev.muon.dynamic_difficulty.settings.LevelingSettings;
import dev.muon.dynamic_difficulty.util.LevelingUtils;
import dev.muon.dynamic_difficulty.util.LocationBonusUtils;
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
import net.minecraft.world.DifficultyInstance;
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
            DynamicDifficulty.id("fixed_level_entities"));

    public static boolean hasLevel(Entity entity) {
        if (entity instanceof LivingEntity living) {
            // Check if entity has attachment data explicitly set (not just default value)
            return DynamicDifficulty.getHelper().getLevelAttachmentHelper().hasLevel(living);
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
        return DynamicDifficulty.getHelper().getLevelAttachmentHelper().getLevel(entity);
    }

    /**
     * Internal: Sets the level attachment without updating attributes or syncing.
     * Use setAndUpdateLevel() for runtime level changes.
     */
    @ApiStatus.Internal
    public static void setLevelAttachment(LivingEntity entity, int level) {
        DynamicDifficulty.getHelper().getLevelAttachmentHelper().setLevel(entity, level);
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

        if (entity.getType().builtInRegistryHolder().is(FIXED_LEVEL_ENTITIES)) {
            int fixedLevel = getFixedLevel(entity);
            DynamicDifficulty.LOGGER.debug("{} has fixed level: {}",
                entity.getType().getDescription().getString(), fixedLevel);
            return fixedLevel;
        }

        LevelingSettings settings = getLevelingSettings(entity);
        int initialLevel = calculateInitialLevel(entity, settings);
        BonusBreakdown bonuses = calculateAllBonuses(entity, settings);

        int beforeCap = initialLevel + bonuses.nonBypassing();
        int maxLevel = settings.maxLevel();
        int capped = (maxLevel > 1) ? Math.min(beforeCap, maxLevel) : beforeCap;
        if (maxLevel > 1 && capped != beforeCap) {
            DynamicDifficulty.LOGGER.debug("{} capped at max level: {} -> {}",
                entity.getType().getDescription().getString(), beforeCap, capped);
        }

        int finalLevel = Math.max(1, capped + bonuses.bypassing());
        DynamicDifficulty.LOGGER.debug("{} level calculated: base={}, non-bypassing={}, capped={}, bypassing={}, final={}",
            entity.getType().getDescription().getString(),
            initialLevel, bonuses.nonBypassing(), capped, bonuses.bypassing(), finalLevel);
        return finalLevel;
    }

    private static int getFixedLevel(LivingEntity entity) {
        return getLevelingSettings(entity).startingLevel();
    }

    private static int calculateInitialLevel(LivingEntity entity, LevelingSettings settings) {
        DimensionLevelingSettings dimSettings = DimensionsLevelingSettingsReloader.get(entity.level().dimension());
        BlockPos spawnPos = LevelingUtils.getEffectiveSpawnPos(entity.level(), dimSettings);
        BlockPos entityPos = entity.blockPosition();
        double distanceToSpawn = LevelingUtils.horizontalDistance(spawnPos, entityPos);

        int startingLevel = settings.startingLevel();
        int baseLevel = startingLevel;

        int distanceBonus = LevelingUtils.calculateDistanceFactors(dimSettings.seaLevel(), entity.getY(), distanceToSpawn, settings);
        baseLevel += distanceBonus;

        int dayBonus = 0;
        int localDifficultyBonus = 0;
        if (entity.level() instanceof ServerLevel serverLevel) {
            long days = serverLevel.getOverworldClockTime() / 24000L;
            dayBonus = (int) (days * settings.levelsPerDay());
            baseLevel += dayBonus;

            DifficultyInstance difficulty = serverLevel.getCurrentDifficultyAt(entityPos);
            localDifficultyBonus = (int) (difficulty.getEffectiveDifficulty() * settings.levelsPerLocalDifficulty());
            baseLevel += localDifficultyBonus;
        }

        int randomBonus = 0;
        int randomBonusValue = settings.randomLevelBonus();
        if (randomBonusValue > 0) {
            randomBonus = entity.getRandom().nextInt(randomBonusValue + 1);
            baseLevel += randomBonus;
        }

        DynamicDifficulty.LOGGER.debug("{} base factors: starting={}, distance={}, day={}, localDifficulty={}, random={}, subtotal={}",
            entity.getType().getDescription().getString(),
            startingLevel, distanceBonus, dayBonus, localDifficultyBonus, randomBonus, baseLevel);

        return Math.max(1, baseLevel);
    }

    /**
     * Computes structure, biome, and player bonuses in a single pass and splits them
     * into bypassing vs non-bypassing totals.
     *
     * <p>Structure and biome detection each fire one lookup; player aggregation runs once.
     * Player bonus lands in the bypassing or non-bypassing slot per {@code playerLevelBypassesCap}.
     */
    private static BonusBreakdown calculateAllBonuses(LivingEntity entity, LevelingSettings settings) {
        if (!(entity.level() instanceof ServerLevel serverLevel)) return BonusBreakdown.EMPTY;

        DimensionLevelingSettings.ApplyLevelBonuses applyBonuses = settings.applyLevelBonuses();
        boolean applyBiome = applyBonuses == null || applyBonuses.biome();
        boolean applyStructure = applyBonuses == null || applyBonuses.structure();
        boolean applyPlayer = applyBonuses == null || applyBonuses.player();

        BlockPos pos = entity.blockPosition();
        StructureBonus structureResult = LocationBonusUtils.getStructureAt(serverLevel, pos, true);
        BiomeBonus biomeResult = LocationBonusUtils.getBiomeAt(serverLevel, pos);

        int nonBypassing = 0;
        int bypassing = 0;
        if (applyStructure) {
            nonBypassing += structureResult.nonBypassingBonus();
            bypassing += structureResult.bypassingBonus();
        }
        if (applyBiome) {
            nonBypassing += biomeResult.nonBypassingBonus();
            bypassing += biomeResult.bypassingBonus();
        }

        int playerBonus = 0;
        if (applyPlayer && Configs.SYNC.applyPlayerBasedLeveling.get()) {
            playerBonus = getLevelsFromNearbyPlayers(serverLevel, entity, settings);
            if (Configs.SYNC.playerLevelBypassesCap.get()) {
                bypassing += playerBonus;
            } else {
                nonBypassing += playerBonus;
            }
        }

        if (nonBypassing > 0 || bypassing > 0) {
            DynamicDifficulty.LOGGER.debug("{} bonuses: structure(nb={}, b={}), biome(nb={}, b={}), player={} (applyB={}, applyS={}, applyP={})",
                entity.getType().getDescription().getString(),
                applyStructure ? structureResult.nonBypassingBonus() : 0, applyStructure ? structureResult.bypassingBonus() : 0,
                applyBiome ? biomeResult.nonBypassingBonus() : 0, applyBiome ? biomeResult.bypassingBonus() : 0,
                playerBonus, applyBiome, applyStructure, applyPlayer);
        }

        return new BonusBreakdown(nonBypassing, bypassing);
    }

    private record BonusBreakdown(int nonBypassing, int bypassing) {
        static final BonusBreakdown EMPTY = new BonusBreakdown(0, 0);
    }



    public static void applyAllLevelAttributes(LivingEntity entity) {
        getAttributeBonuses(entity).forEach((attributeKey, modifier) -> {
            Optional<? extends Holder<Attribute>> optAttributeHolder = BuiltInRegistries.ATTRIBUTE.get(attributeKey);
            if (optAttributeHolder.isPresent()) {
                applyAttributeBonus(entity, optAttributeHolder.get(), modifier);
            } else {
                DynamicDifficulty.LOGGER.warn("Entity {}: Could not find attribute holder for key {} when applying all attributes.",
                                            EntityType.getKey(entity.getType()), attributeKey.identifier());
            }
        });
    }

    public static Map<ResourceKey<Attribute>, AttributeModifier> getAttributeBonuses(LivingEntity entity) {
        LevelingSettings settings = getLevelingSettings(entity);

        // Get modifiers from settings (already resolved through dim → biome → structure → entity)
        Map<Attribute, AttributeModifier> modifiers = settings.attributeModifiers();

        // null = field was omitted at all levels, fall back to config
        // empty map = explicitly set to [] (disable modifiers)
        // non-empty map = use these modifiers
        if (modifiers == null) {
            return ConfigSync.getAttributeBonuses();
        }

        return convertAttributeMapToKeyMap(modifiers);
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

        instance.addPermanentModifier(new AttributeModifier(
                modifier.id(), modifier.amount() * level, modifier.operation()));

        if (attributeHolder == Attributes.MAX_HEALTH && entity.getHealth() < entity.getMaxHealth()) {
            entity.setHealth(entity.getMaxHealth());
        }
    }

    /**
     * Resolves the leveling settings for an entity through the full chain:
     * {@code dimension → biome → structure → entity}, position-aware. Each tier overrides the
     * prior. The result implements {@link LevelingSettings} and is fully resolved (no fallthroughs).
     */
    public static LevelingSettings getLevelingSettings(LivingEntity entity) {
        LevelingSettings prior;
        if (entity.level() instanceof ServerLevel serverLevel) {
            prior = LocationBonusUtils.resolveLocationSettings(serverLevel, entity.blockPosition());
        } else {
            prior = DimensionsLevelingSettingsReloader.get(entity.level().dimension());
        }

        EntityLevelingSettings entityResolved = EntityLevelingSettingsReloader.get(entity.getType(), prior);
        return entityResolved != null ? entityResolved : prior;
    }

    /**
     * Gets the level contribution from nearby players. Resolves the entity's leveling chain
     * internally; callers that already have resolved settings should use the
     * {@link #getLevelsFromNearbyPlayers(ServerLevel, LivingEntity, LevelingSettings)} overload.
     */
    public static int getLevelsFromNearbyPlayers(ServerLevel level, LivingEntity entity) {
        return getLevelsFromNearbyPlayers(level, entity, getLevelingSettings(entity));
    }

    /**
     * Gets the level contribution from nearby players using already-resolved settings.
     */
    public static int getLevelsFromNearbyPlayers(ServerLevel level, LivingEntity entity, LevelingSettings settings) {
        if (!Configs.SYNC.applyPlayerBasedLeveling.get()) {
            DynamicDifficulty.LOGGER.debug("Player-based leveling disabled in config");
            return 0;
        }

        double radius = Configs.SYNC.playerLevelRadius.get();
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

        int total = PlayerLevelProvider.sumBonusLevels(nearbyPlayers);
        DynamicDifficulty.LOGGER.debug("Total player bonus from {} providers: {}",
            PlayerLevelProvider.getProviders().size(), total);

        Double multiplierOverride = settings.playerLevelMultiplier();
        double multiplier = multiplierOverride != null ? multiplierOverride : Configs.SYNC.playerLevelMultiplier.get();
        int scaledBonus = (int) (total * multiplier);

        if (multiplier != 1.0 || multiplierOverride != null) {
            DynamicDifficulty.LOGGER.debug("Player bonus scaled: {} * {} = {} (override={})",
                total, multiplier, scaledBonus, multiplierOverride);
        }

        return scaledBonus;
    }

    /**
     * Gets the structure bonus for an entity's current position.
     */
    public static StructureBonus getStructureBonus(LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel serverLevel)) return StructureBonus.EMPTY;

        return LocationBonusUtils.getStructureAt(serverLevel, entity.blockPosition(), true);
    }

    /**
     * Gets the biome bonus for an entity's current position.
     */
    public static BiomeBonus getBiomeBonus(LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel serverLevel)) return BiomeBonus.EMPTY;

        return LocationBonusUtils.getBiomeAt(serverLevel, entity.blockPosition());
    }
}
