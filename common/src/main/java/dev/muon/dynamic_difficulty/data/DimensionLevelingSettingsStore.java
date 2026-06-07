package dev.muon.dynamic_difficulty.data;

import com.mojang.logging.LogUtils;
import dev.muon.dynamic_difficulty.settings.DimensionLevelingSettings;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class DimensionLevelingSettingsStore {
  private static final Logger LOGGER = LogUtils.getLogger();
  private static final Map<Identifier, DimensionLevelingSettings> INDIVIDUAL_SETTINGS = new HashMap<>();
  private static final Map<TagKey<Level>, DimensionLevelingSettings> TAG_SETTINGS = new HashMap<>();

  @NotNull
  public static DimensionLevelingSettings get(ResourceKey<Level> dimension) {
    DimensionLevelingSettings individual = INDIVIDUAL_SETTINGS.get(dimension.identifier());
    return individual != null ? individual : DimensionLevelingSettings.createDefault();
  }

  @NotNull
  public static DimensionLevelingSettings get(ResourceKey<Level> dimension, Registry<Level> dimensionRegistry) {
    DimensionLevelingSettings individual = INDIVIDUAL_SETTINGS.get(dimension.identifier());
    if (individual != null) {
      return individual;
    }

    if (!TAG_SETTINGS.isEmpty()) {
      Optional<Holder.Reference<Level>> optHolder = dimensionRegistry.get(dimension);
      if (optHolder.isPresent()) {
        Holder<Level> dimensionHolder = optHolder.get();
        for (Map.Entry<TagKey<Level>, DimensionLevelingSettings> tagEntry : TAG_SETTINGS.entrySet()) {
          if (dimensionHolder.is(tagEntry.getKey())) {
            return tagEntry.getValue();
          }
        }
      }
    }

    return DimensionLevelingSettings.createDefault();
  }

  public static boolean hasCustomSettings(ResourceKey<Level> dimension, Registry<Level> dimensionRegistry) {
    if (INDIVIDUAL_SETTINGS.containsKey(dimension.identifier())) {
      return true;
    }

    if (TAG_SETTINGS.isEmpty()) {
      return false;
    }

    Optional<Holder.Reference<Level>> optHolder = dimensionRegistry.get(dimension);
    if (optHolder.isEmpty()) {
      return false;
    }

    Holder<Level> dimensionHolder = optHolder.get();
    for (TagKey<Level> tag : TAG_SETTINGS.keySet()) {
      if (dimensionHolder.is(tag)) {
        return true;
      }
    }
    return false;
  }

  public static void loadSettings(Map<Identifier, DimensionLevelingSettings> settings) {
    INDIVIDUAL_SETTINGS.clear();
    INDIVIDUAL_SETTINGS.putAll(settings);
    LOGGER.info("Loaded {} individual dimension leveling settings from 'leveling_settings/dimensions'", INDIVIDUAL_SETTINGS.size());
  }

  public static void loadTagSettings(Map<Identifier, DimensionLevelingSettings> tagSettings) {
    TAG_SETTINGS.clear();
    tagSettings.forEach((id, settings) -> TAG_SETTINGS.put(TagKey.create(Registries.DIMENSION, id), settings));
    LOGGER.info("Loaded {} dimension tag leveling settings from 'leveling_settings/dimension_tags'", TAG_SETTINGS.size());
  }
}
