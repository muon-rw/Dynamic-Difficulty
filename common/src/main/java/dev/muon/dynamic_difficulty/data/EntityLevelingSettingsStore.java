package dev.muon.dynamic_difficulty.data;

import com.mojang.logging.LogUtils;
import dev.muon.dynamic_difficulty.settings.EntityLevelingSettings;
import dev.muon.dynamic_difficulty.settings.LevelingSettings;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EntityLevelingSettingsStore {
  private static final Logger LOGGER = LogUtils.getLogger();
  private static final Map<ResourceLocation, EntityLevelingSettings.RawSettings> INDIVIDUAL_SETTINGS = new HashMap<>();
  private static final List<TagSettings<EntityType<?>, EntityLevelingSettings.RawSettings>> TAG_SETTINGS = new ArrayList<>();

  /** Null when the entity type has no settings of its own. */
  @Nullable
  public static EntityLevelingSettings get(EntityType<?> entityType, LevelingSettings prior) {
    EntityLevelingSettings resolved = null;
    for (EntityLevelingSettings.RawSettings layer : layersFor(entityType)) {
      resolved = layer.resolve(resolved != null ? resolved : prior);
    }
    return resolved;
  }

  /** Matching tag entries in apply order, then the entity's own entry, which applies last. */
  private static List<EntityLevelingSettings.RawSettings> layersFor(EntityType<?> entityType) {
    List<EntityLevelingSettings.RawSettings> layers = new ArrayList<>();
    for (TagSettings<EntityType<?>, EntityLevelingSettings.RawSettings> tagEntry : TAG_SETTINGS) {
      if (entityType.is(tagEntry.tag())) {
        layers.add(tagEntry.settings());
      }
    }
    EntityLevelingSettings.RawSettings individual = INDIVIDUAL_SETTINGS.get(EntityType.getKey(entityType));
    if (individual != null) {
      layers.add(individual);
    }
    return layers;
  }

  public static void loadSettings(Map<ResourceLocation, EntityLevelingSettings.RawSettings> settings) {
    INDIVIDUAL_SETTINGS.clear();
    INDIVIDUAL_SETTINGS.putAll(settings);
    LOGGER.info("Loaded {} individual entity leveling settings from 'leveling_settings/entities'", INDIVIDUAL_SETTINGS.size());
  }

  public static void loadTagSettings(Map<ResourceLocation, EntityLevelingSettings.RawSettings> tagSettings) {
    TAG_SETTINGS.clear();
    TAG_SETTINGS.addAll(TagSettings.inApplyOrder(Registries.ENTITY_TYPE, tagSettings, EntityLevelingSettings.RawSettings::priority));
    LOGGER.info("Loaded {} entity tag leveling settings from 'leveling_settings/entity_tags'", TAG_SETTINGS.size());
  }
}
