package dev.muon.dynamic_difficulty.data;

import com.mojang.logging.LogUtils;
import dev.muon.dynamic_difficulty.settings.DimensionLevelingSettings;
import dev.muon.dynamic_difficulty.settings.EntityLevelingSettings;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Storage and lookup for entity leveling settings.
 * Platform-specific reloaders populate this via loadSettings/loadTagSettings.
 */
public class EntityLevelingSettingsReloader {
  private static final Logger LOGGER = LogUtils.getLogger();
  // Store raw settings - they get resolved at lookup time with dimension fallback
  private static final Map<Identifier, EntityLevelingSettings.RawSettings> INDIVIDUAL_SETTINGS = new HashMap<>();
  private static final Map<TagKey<EntityType<?>>, EntityLevelingSettings.RawSettings> TAG_SETTINGS = new HashMap<>();

  /**
   * Gets resolved entity settings, falling back to dimension settings for any omitted fields.
   * Returns null if no entity-specific settings exist (caller should use dimension settings directly).
   */
  @Nullable
  public static EntityLevelingSettings get(EntityType<?> entityType, DimensionLevelingSettings dimSettings) {
    Identifier entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);

    EntityLevelingSettings.RawSettings individual = INDIVIDUAL_SETTINGS.get(entityId);
    if (individual != null) {
      return individual.resolve(dimSettings);
    }

    if (TAG_SETTINGS.isEmpty()) {
      return null;
    }

    Optional<Holder.Reference<EntityType<?>>> optHolder = BuiltInRegistries.ENTITY_TYPE.get(entityId);
    if (optHolder.isEmpty()) {
      return null;
    }

    Holder<EntityType<?>> entityHolder = optHolder.get();
    for (Map.Entry<TagKey<EntityType<?>>, EntityLevelingSettings.RawSettings> tagEntry : TAG_SETTINGS.entrySet()) {
      if (entityHolder.is(tagEntry.getKey())) {
        return tagEntry.getValue().resolve(dimSettings);
      }
    }
    return null;
  }

  /**
   * Checks if an entity type has custom settings (individual or tag-based).
   */
  public static boolean hasCustomSettings(EntityType<?> entityType) {
    Identifier entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);

    if (INDIVIDUAL_SETTINGS.containsKey(entityId)) {
      return true;
    }

    if (TAG_SETTINGS.isEmpty()) {
      return false;
    }

    Optional<Holder.Reference<EntityType<?>>> optHolder = BuiltInRegistries.ENTITY_TYPE.get(entityId);
    if (optHolder.isEmpty()) {
      return false;
    }

    Holder<EntityType<?>> entityHolder = optHolder.get();
    for (TagKey<EntityType<?>> tag : TAG_SETTINGS.keySet()) {
      if (entityHolder.is(tag)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Load individual entity settings. Called by platform-specific reloaders.
   */
  public static void loadSettings(Map<Identifier, EntityLevelingSettings.RawSettings> settings) {
    INDIVIDUAL_SETTINGS.clear();
    INDIVIDUAL_SETTINGS.putAll(settings);
    LOGGER.info("Loaded {} individual entity leveling settings from 'leveling_settings/entities'", INDIVIDUAL_SETTINGS.size());
  }

  /**
   * Load tag-based entity settings. Called by platform-specific reloaders.
   */
  public static void loadTagSettings(Map<Identifier, EntityLevelingSettings.RawSettings> tagSettings) {
    TAG_SETTINGS.clear();
    tagSettings.forEach((id, settings) -> TAG_SETTINGS.put(TagKey.create(Registries.ENTITY_TYPE, id), settings));
    LOGGER.info("Loaded {} entity tag leveling settings from 'leveling_settings/entity_tags'", TAG_SETTINGS.size());
  }
}
