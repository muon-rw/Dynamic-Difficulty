package dev.muon.dynamic_difficulty.data;

import com.mojang.logging.LogUtils;
import dev.muon.dynamic_difficulty.settings.DimensionLevelingSettings;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DimensionLevelingSettingsStore {
  private static final Logger LOGGER = LogUtils.getLogger();
  private static final Map<ResourceLocation, DimensionLevelingSettings.RawSettings> INDIVIDUAL_SETTINGS = new HashMap<>();
  private static final List<TagSettings<Level, DimensionLevelingSettings.RawSettings>> TAG_SETTINGS = new ArrayList<>();

  @NotNull
  public static DimensionLevelingSettings get(Level level) {
    DimensionLevelingSettings settings = DimensionLevelingSettings.createDefault();
    for (DimensionLevelingSettings.RawSettings layer : layersFor(level)) {
      settings = layer.resolve(settings);
    }
    return settings;
  }

  public static boolean hasCustomSettings(Level level) {
    return !layersFor(level).isEmpty();
  }

  /** Matching tag entries in apply order, then the dimension's own entry, which applies last. */
  private static List<DimensionLevelingSettings.RawSettings> layersFor(Level level) {
    List<DimensionLevelingSettings.RawSettings> layers = new ArrayList<>();
    Holder<Level> dimensionHolder = TAG_SETTINGS.isEmpty() ? null : dimensionHolder(level);
    if (dimensionHolder != null) {
      for (TagSettings<Level, DimensionLevelingSettings.RawSettings> tagEntry : TAG_SETTINGS) {
        if (dimensionHolder.is(tagEntry.tag())) {
          layers.add(tagEntry.settings());
        }
      }
    }
    DimensionLevelingSettings.RawSettings individual = INDIVIDUAL_SETTINGS.get(level.dimension().location());
    if (individual != null) {
      layers.add(individual);
    }
    return layers;
  }

  // Registries.DIMENSION shares its id with the level stem registry, which a dedicated server never
  // sends to clients, so tag entries only match server side.
  @Nullable
  private static Holder<Level> dimensionHolder(Level level) {
    return level.registryAccess().registry(Registries.DIMENSION)
        .flatMap(registry -> registry.getHolder(level.dimension()))
        .orElse(null);
  }

  public static void loadSettings(Map<ResourceLocation, DimensionLevelingSettings.RawSettings> settings) {
    INDIVIDUAL_SETTINGS.clear();
    INDIVIDUAL_SETTINGS.putAll(settings);
    LOGGER.info("Loaded {} individual dimension leveling settings from 'leveling_settings/dimensions'", INDIVIDUAL_SETTINGS.size());
  }

  public static void loadTagSettings(Map<ResourceLocation, DimensionLevelingSettings.RawSettings> tagSettings) {
    TAG_SETTINGS.clear();
    TAG_SETTINGS.addAll(TagSettings.inApplyOrder(Registries.DIMENSION, tagSettings, DimensionLevelingSettings.RawSettings::priority));
    LOGGER.info("Loaded {} dimension tag leveling settings from 'leveling_settings/dimension_tags'", TAG_SETTINGS.size());
  }
}
