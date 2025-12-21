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

/**
 * Storage and lookup for dimension leveling settings.
 * Platform-specific reloaders populate this via loadSettings/loadTagSettings.
 */
public class DimensionsLevelingSettingsReloader {
  private static final Logger LOGGER = LogUtils.getLogger();
  private static final Map<Identifier, DimensionLevelingSettings> INDIVIDUAL_SETTINGS = new HashMap<>();
  private static final Map<Identifier, DimensionLevelingSettings> TAG_SETTINGS = new HashMap<>();

  @NotNull
  public static DimensionLevelingSettings get(ResourceKey<Level> dimension) {
    Identifier dimensionId = dimension.identifier();
    
    // Check individual dimension settings first (they take precedence)
    if (INDIVIDUAL_SETTINGS.containsKey(dimensionId)) {
      return INDIVIDUAL_SETTINGS.get(dimensionId);
    }
    
    // Fall back to default if no match found
    return DimensionLevelingSettings.createDefault();
  }
  
  @NotNull
  public static DimensionLevelingSettings get(ResourceKey<Level> dimension, Registry<Level> dimensionRegistry) {
    Identifier dimensionId = dimension.identifier();
    
    // Check individual dimension settings first (they take precedence)
    if (INDIVIDUAL_SETTINGS.containsKey(dimensionId)) {
      return INDIVIDUAL_SETTINGS.get(dimensionId);
    }
    
    // Check dimension tags
    Optional<Holder.Reference<Level>> optHolder = dimensionRegistry.get(dimension);
    if (optHolder.isPresent()) {
      Holder<Level> dimensionHolder = optHolder.get();
      // Find the first matching tag (tags are checked in order, first match wins)
      for (Map.Entry<Identifier, DimensionLevelingSettings> tagEntry : TAG_SETTINGS.entrySet()) {
        TagKey<Level> dimensionTag = TagKey.create(Registries.DIMENSION, tagEntry.getKey());
        if (dimensionHolder.is(dimensionTag)) {
          return tagEntry.getValue();
        }
      }
    }
    
    // Fall back to default if no match found
    return DimensionLevelingSettings.createDefault();
  }

  /**
   * Checks if a dimension has custom leveling settings (either individual or tag-based).
   */
  public static boolean hasCustomSettings(ResourceKey<Level> dimension, Registry<Level> dimensionRegistry) {
    Identifier dimensionId = dimension.identifier();
    
    if (INDIVIDUAL_SETTINGS.containsKey(dimensionId)) {
      return true;
    }
    
    Optional<Holder.Reference<Level>> optHolder = dimensionRegistry.get(dimension);
    if (optHolder.isPresent()) {
      Holder<Level> dimensionHolder = optHolder.get();
      for (Map.Entry<Identifier, DimensionLevelingSettings> tagEntry : TAG_SETTINGS.entrySet()) {
        TagKey<Level> dimensionTag = TagKey.create(Registries.DIMENSION, tagEntry.getKey());
        if (dimensionHolder.is(dimensionTag)) {
          return true;
        }
      }
    }
    
    return false;
  }

  /**
   * Load individual dimension settings. Called by platform-specific reloaders.
   */
  public static void loadSettings(Map<Identifier, DimensionLevelingSettings> settings) {
    INDIVIDUAL_SETTINGS.clear();
    INDIVIDUAL_SETTINGS.putAll(settings);
    LOGGER.info("Loaded {} individual dimension leveling settings from 'leveling_settings/dimensions'", INDIVIDUAL_SETTINGS.size());
  }
  
  /**
   * Load tag-based dimension settings. Called by platform-specific reloaders.
   */
  public static void loadTagSettings(Map<Identifier, DimensionLevelingSettings> tagSettings) {
    TAG_SETTINGS.clear();
    TAG_SETTINGS.putAll(tagSettings);
    LOGGER.info("Loaded {} dimension tag leveling settings from 'leveling_settings/dimension_tags'", TAG_SETTINGS.size());
  }
}
