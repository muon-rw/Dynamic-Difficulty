package dev.muon.dynamic_difficulty.data;

import com.mojang.logging.LogUtils;
import dev.muon.dynamic_difficulty.settings.DimensionLevelingSettings;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

public class DimensionLevelingSettingsStore {
  private static final Logger LOGGER = LogUtils.getLogger();
  private static final Map<ResourceLocation, DimensionLevelingSettings> INDIVIDUAL_SETTINGS = new HashMap<>();
  private static final Map<TagKey<Level>, DimensionLevelingSettings> TAG_SETTINGS = new HashMap<>();

  @NotNull
  public static DimensionLevelingSettings get(Level level) {
    DimensionLevelingSettings settings = find(level);
    return settings != null ? settings : DimensionLevelingSettings.createDefault();
  }

  public static boolean hasCustomSettings(Level level) {
    return find(level) != null;
  }

  @Nullable
  private static DimensionLevelingSettings find(Level level) {
    DimensionLevelingSettings individual = INDIVIDUAL_SETTINGS.get(level.dimension().location());
    if (individual != null || TAG_SETTINGS.isEmpty()) {
      return individual;
    }

    // Registries.DIMENSION shares its id with the level stem registry, which a dedicated server never
    // sends to clients, so tag entries only match server side.
    Registry<Level> dimensionRegistry = level.registryAccess().registry(Registries.DIMENSION).orElse(null);
    if (dimensionRegistry == null) {
      return null;
    }
    Holder<Level> dimensionHolder = dimensionRegistry.getHolder(level.dimension()).orElse(null);
    if (dimensionHolder == null) {
      return null;
    }
    for (Map.Entry<TagKey<Level>, DimensionLevelingSettings> tagEntry : TAG_SETTINGS.entrySet()) {
      if (dimensionHolder.is(tagEntry.getKey())) {
        return tagEntry.getValue();
      }
    }
    return null;
  }

  public static void loadSettings(Map<ResourceLocation, DimensionLevelingSettings> settings) {
    INDIVIDUAL_SETTINGS.clear();
    INDIVIDUAL_SETTINGS.putAll(settings);
    LOGGER.info("Loaded {} individual dimension leveling settings from 'leveling_settings/dimensions'", INDIVIDUAL_SETTINGS.size());
  }

  public static void loadTagSettings(Map<ResourceLocation, DimensionLevelingSettings> tagSettings) {
    TAG_SETTINGS.clear();
    tagSettings.forEach((id, settings) -> TAG_SETTINGS.put(TagKey.create(Registries.DIMENSION, id), settings));
    LOGGER.info("Loaded {} dimension tag leveling settings from 'leveling_settings/dimension_tags'", TAG_SETTINGS.size());
  }
}
