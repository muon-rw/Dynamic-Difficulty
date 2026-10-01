package dev.muon.dynamic_difficulty.data;

import com.mojang.logging.LogUtils;
import dev.muon.dynamic_difficulty.settings.EntityLevelingSettings;
import dev.muon.dynamic_difficulty.settings.LevelingSettings;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

public class EntityLevelingSettingsStore {
  private static final Logger LOGGER = LogUtils.getLogger();
  private static final Map<ResourceLocation, EntityLevelingSettings.RawSettings> INDIVIDUAL_SETTINGS = new HashMap<>();
  private static final Map<TagKey<EntityType<?>>, EntityLevelingSettings.RawSettings> TAG_SETTINGS = new HashMap<>();

  /** Null when the entity type has no settings of its own. */
  @Nullable
  public static EntityLevelingSettings get(EntityType<?> entityType, LevelingSettings prior) {
    EntityLevelingSettings.RawSettings raw = find(entityType);
    return raw == null ? null : raw.resolve(prior);
  }

  @Nullable
  private static EntityLevelingSettings.RawSettings find(EntityType<?> entityType) {
    EntityLevelingSettings.RawSettings individual = INDIVIDUAL_SETTINGS.get(EntityType.getKey(entityType));
    if (individual != null) {
      return individual;
    }

    for (Map.Entry<TagKey<EntityType<?>>, EntityLevelingSettings.RawSettings> tagEntry : TAG_SETTINGS.entrySet()) {
      if (entityType.is(tagEntry.getKey())) {
        return tagEntry.getValue();
      }
    }
    return null;
  }

  public static void loadSettings(Map<ResourceLocation, EntityLevelingSettings.RawSettings> settings) {
    INDIVIDUAL_SETTINGS.clear();
    INDIVIDUAL_SETTINGS.putAll(settings);
    LOGGER.info("Loaded {} individual entity leveling settings from 'leveling_settings/entities'", INDIVIDUAL_SETTINGS.size());
  }

  public static void loadTagSettings(Map<ResourceLocation, EntityLevelingSettings.RawSettings> tagSettings) {
    TAG_SETTINGS.clear();
    tagSettings.forEach((id, settings) -> TAG_SETTINGS.put(TagKey.create(Registries.ENTITY_TYPE, id), settings));
    LOGGER.info("Loaded {} entity tag leveling settings from 'leveling_settings/entity_tags'", TAG_SETTINGS.size());
  }
}
