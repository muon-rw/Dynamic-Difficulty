package dev.muon.dynamic_difficulty;

import dev.muon.dynamic_difficulty.api.LevelingAPI;
import dev.muon.dynamic_difficulty.api.PlayerLevelProvider;
import dev.muon.dynamic_difficulty.config.ConfigSync;
import dev.muon.dynamic_difficulty.config.Configs;
import dev.muon.dynamic_difficulty.data.DimensionLevelingSettingsStore;
import dev.muon.dynamic_difficulty.data.EntityLevelingSettingsStore;
import dev.muon.dynamic_difficulty.network.NetworkDispatcher;
import dev.muon.dynamic_difficulty.settings.EntityLevelingSettings;
import dev.muon.dynamic_difficulty.settings.LevelingSettings;
import dev.muon.dynamic_difficulty.util.LevelingUtils;
import dev.muon.dynamic_difficulty.util.LocationBonusUtils;
import dev.muon.dynamic_difficulty.util.LocationBonusUtils.ResolvedLocation;
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
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LevelingSystem {
    private static final TagKey<EntityType<?>> FIXED_LEVEL_ENTITIES = TagKey.create(Registries.ENTITY_TYPE,
            DynamicDifficulty.loc("fixed_level_entities"));

    public static boolean hasLevel(Entity entity) {
        if (entity instanceof LivingEntity living) {
            return DynamicDifficulty.getHelper().getLevelAttachmentHelper().hasLevel(living);
        }
        return false;
    }

    /**
     * @return the entity's level, or 1 if no level is set.
     */
    public static int getLevel(LivingEntity entity) {
        return DynamicDifficulty.getHelper().getLevelAttachmentHelper().getLevel(entity);
    }

    /**
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
            throw new IllegalArgumentException("Cannot set levels on players; use the PlayerLevelProvider system instead");
        }

        if (newLevel < 0) {
            throw new IllegalArgumentException("Level cannot be negative");
        }

        if (!LevelingAPI.canHaveLevel(entity)) {
            throw new IllegalArgumentException("Entity type " + entity.getType().getDescription().getString() + " cannot have levels");
        }

        int oldLevel = getLevel(entity);
        setLevelAttachment(entity, newLevel);

        applyAllLevelAttributes(entity);

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

    public static int calculateLevelForEntity(LivingEntity entity) {
        if (!LevelingAPI.canHaveLevel(entity)) {
            return 1;
        }
        ResolvedLocation location = resolveLocation(entity);
        return calculateLevel(entity, resolveEntitySettings(entity, location), location);
    }

    /** Resolves the settings chain once for both the level and its attribute bonuses. */
    @ApiStatus.Internal
    public static void initializeLevel(LivingEntity entity) {
        ResolvedLocation location = resolveLocation(entity);
        LevelingSettings settings = resolveEntitySettings(entity, location);
        setLevelAttachment(entity, calculateLevel(entity, settings, location));
        applyAllLevelAttributes(entity, settings);
    }

    private static int calculateLevel(LivingEntity entity, LevelingSettings settings, @Nullable ResolvedLocation location) {
        String entityName = entity.getType().getDescription().getString();
        if (entity.getType().is(FIXED_LEVEL_ENTITIES)) {
            DynamicDifficulty.LOGGER.debug("{} has fixed level: {}", entityName, settings.startingLevel());
            return settings.startingLevel();
        }
        if (location == null || !(entity.level() instanceof ServerLevel serverLevel)) {
            return Math.max(1, settings.startingLevel());
        }

        int baseLevel = LevelingUtils.calculateBaseEntityLevel(serverLevel, entity.blockPosition(), settings, location.dimension());
        int randomBonus = settings.randomLevelBonus() > 0 ? entity.getRandom().nextInt(settings.randomLevelBonus() + 1) : 0;
        int playerBonus = settings.appliesPlayerBonus() ? getLevelsFromNearbyPlayers(serverLevel, entity, settings) : 0;

        int finalLevel = LevelingUtils.calculateFinalLevel(baseLevel + randomBonus, settings,
                location.structureBonus(), location.biomeBonus(), playerBonus);
        DynamicDifficulty.LOGGER.debug("{} level calculated: base={}, random={}, {}, {}, player={}, max={}, final={}",
                entityName, baseLevel, randomBonus, location.structureBonus(), location.biomeBonus(),
                playerBonus, settings.maxLevel(), finalLevel);
        return finalLevel;
    }

    @Nullable
    private static ResolvedLocation resolveLocation(LivingEntity entity) {
        return entity.level() instanceof ServerLevel serverLevel
                ? LocationBonusUtils.resolveLocation(serverLevel, entity.blockPosition())
                : null;
    }

    private static LevelingSettings resolveEntitySettings(LivingEntity entity, @Nullable ResolvedLocation location) {
        LevelingSettings prior = location != null ? location.settings() : DimensionLevelingSettingsStore.get(entity.level());
        EntityLevelingSettings entitySettings = EntityLevelingSettingsStore.get(entity.getType(), prior);
        return entitySettings != null ? entitySettings : prior;
    }

    /** Dimension, biome, structure, then entity; on the client only the dimension and entity tiers. */
    public static LevelingSettings getLevelingSettings(LivingEntity entity) {
        return resolveEntitySettings(entity, resolveLocation(entity));
    }

    public static void applyAllLevelAttributes(LivingEntity entity) {
        applyAllLevelAttributes(entity, getLevelingSettings(entity));
    }

    private static void applyAllLevelAttributes(LivingEntity entity, LevelingSettings settings) {
        Map<Holder<Attribute>, AttributeModifier> wantedModifiers = scaleAttributeBonuses(settings, getLevel(entity));
        BuiltInRegistries.ATTRIBUTE.holders().forEach(attribute -> {
            AttributeInstance instance = entity.getAttribute(attribute);
            if (instance != null) {
                reconcileLevelingModifier(entity, attribute, instance, wantedModifiers.get(attribute));
            }
        });
    }

    private static Map<Holder<Attribute>, AttributeModifier> scaleAttributeBonuses(LevelingSettings settings, int level) {
        Map<Holder<Attribute>, AttributeModifier> scaled = new HashMap<>();
        if (level == 1) {
            return scaled;
        }
        getAttributeBonuses(settings).forEach((attributeKey, modifier) -> {
            if (modifier.amount() == 0) {
                return;
            }
            BuiltInRegistries.ATTRIBUTE.getHolder(attributeKey).ifPresentOrElse(
                    attribute -> scaled.put(attribute, new AttributeModifier(
                            modifier.id(), modifier.amount() * level, modifier.operation())),
                    () -> DynamicDifficulty.LOGGER.warn("Could not find attribute {} for a leveling bonus", attributeKey.location()));
        });
        return scaled;
    }

    // Settings tiers prefix their modifier ids differently, so any of our modifiers but the wanted one is stale.
    // Leaving an unchanged modifier in place also keeps a reloaded entity from healing to full.
    private static void reconcileLevelingModifier(LivingEntity entity, Holder<Attribute> attribute,
                                                  AttributeInstance instance, @Nullable AttributeModifier wanted) {
        for (AttributeModifier existing : List.copyOf(instance.getModifiers())) {
            if (existing.id().getNamespace().equals(DynamicDifficulty.MODID) && !existing.equals(wanted)) {
                instance.removeModifier(existing.id());
            }
        }
        if (wanted == null || instance.hasModifier(wanted.id())) {
            return;
        }
        instance.addPermanentModifier(wanted);

        if (attribute == Attributes.MAX_HEALTH && entity.getHealth() < entity.getMaxHealth()) {
            entity.setHealth(entity.getMaxHealth());
        }
    }

    public static Map<ResourceKey<Attribute>, AttributeModifier> getAttributeBonuses(LivingEntity entity) {
        return getAttributeBonuses(getLevelingSettings(entity));
    }

    private static Map<ResourceKey<Attribute>, AttributeModifier> getAttributeBonuses(LevelingSettings settings) {
        Map<Attribute, AttributeModifier> modifiers = settings.attributeModifiers();
        if (modifiers == null) {
            return ConfigSync.getAttributeBonuses();
        }

        Map<ResourceKey<Attribute>, AttributeModifier> keyMap = new HashMap<>();
        modifiers.forEach((attribute, modifier) -> BuiltInRegistries.ATTRIBUTE.getResourceKey(attribute)
                .ifPresent(key -> keyMap.put(key, modifier)));
        return keyMap;
    }

    public static int getLevelsFromNearbyPlayers(ServerLevel level, LivingEntity entity) {
        return getLevelsFromNearbyPlayers(level, entity, getLevelingSettings(entity));
    }

    private static int getLevelsFromNearbyPlayers(ServerLevel level, LivingEntity entity, LevelingSettings settings) {
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

        return scalePlayerBonus(total, settings);
    }

    public static int scalePlayerBonus(int rawBonus, LevelingSettings settings) {
        Double multiplierOverride = settings.playerLevelMultiplier();
        double multiplier = multiplierOverride != null ? multiplierOverride : Configs.SYNC.playerLevelMultiplier.get();
        return (int) (rawBonus * multiplier);
    }
}
