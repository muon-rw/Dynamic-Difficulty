package dev.muon.dynamic_difficulty.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import dev.muon.dynamic_difficulty.DynamicDifficulty;
import dev.muon.dynamic_difficulty.settings.DimensionLevelingSettings;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class DimensionsLevelingSettingsReloader extends SimpleJsonResourceReloadListener implements IdentifiableResourceReloadListener {
  private static final Logger LOGGER = LogUtils.getLogger();
  private static final Gson GSON = new Gson();
  private static final Map<ResourceLocation, DimensionLevelingSettings> INDIVIDUAL_SETTINGS = new HashMap<>();
  private static final Map<ResourceLocation, DimensionLevelingSettings> TAG_SETTINGS = new HashMap<>();

  public DimensionsLevelingSettingsReloader() {
    super(GSON, "leveling_settings/dimensions");
  }

  @NotNull
  public static DimensionLevelingSettings get(ResourceKey<Level> dimension) {
    ResourceLocation dimensionId = dimension.location();
    
    // Check individual dimension settings first (they take precedence)
    if (INDIVIDUAL_SETTINGS.containsKey(dimensionId)) {
      return INDIVIDUAL_SETTINGS.get(dimensionId);
    }
    
    // Fall back to default if no match found
    return createDefaultSettings();
  }
  
  @NotNull
  public static DimensionLevelingSettings get(ResourceKey<Level> dimension, net.minecraft.core.Registry<Level> dimensionRegistry) {
    ResourceLocation dimensionId = dimension.location();
    
    // Check individual dimension settings first (they take precedence)
    if (INDIVIDUAL_SETTINGS.containsKey(dimensionId)) {
      return INDIVIDUAL_SETTINGS.get(dimensionId);
    }
    
    // Check dimension tags
    var optHolder = dimensionRegistry.getHolder(dimension);
    if (optHolder.isPresent()) {
      var dimensionHolder = optHolder.get();
      // Find the first matching tag (tags are checked in order, first match wins)
      for (Map.Entry<ResourceLocation, DimensionLevelingSettings> tagEntry : TAG_SETTINGS.entrySet()) {
        var dimensionTag = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.DIMENSION, tagEntry.getKey());
        if (dimensionHolder.is(dimensionTag)) {
          return tagEntry.getValue();
        }
      }
    }
    
    // Fall back to default if no match found
    return createDefaultSettings();
  }

  /**
   * Checks if a dimension has custom leveling settings (either individual or tag-based).
   * Used to determine if dimension titles should be displayed.
   */
  public static boolean hasCustomSettings(ResourceKey<Level> dimension, net.minecraft.core.Registry<Level> dimensionRegistry) {
    ResourceLocation dimensionId = dimension.location();
    
    // Check individual settings
    if (INDIVIDUAL_SETTINGS.containsKey(dimensionId)) {
      return true;
    }
    
    // Check tags
    Optional<net.minecraft.core.Holder.Reference<Level>> optHolder = dimensionRegistry.getHolder(dimension);
    if (optHolder.isPresent()) {
      net.minecraft.core.Holder<Level> dimensionHolder = optHolder.get();
      for (Map.Entry<ResourceLocation, DimensionLevelingSettings> tagEntry : TAG_SETTINGS.entrySet()) {
        net.minecraft.tags.TagKey<Level> dimensionTag = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.DIMENSION, tagEntry.getKey());
        if (dimensionHolder.is(dimensionTag)) {
          return true;
        }
      }
    }
    
    return false;
  }

  private static DimensionLevelingSettings createDefaultSettings() {
    return DimensionLevelingSettings.createDefault();
  }
  
  public static void loadTagSettings(Map<ResourceLocation, DimensionLevelingSettings> tagSettings) {
    TAG_SETTINGS.clear();
    TAG_SETTINGS.putAll(tagSettings);
    LOGGER.info("Loaded {} dimension tag leveling settings from 'leveling_settings/dimension_tags'", TAG_SETTINGS.size());
  }

  @Override
  public ResourceLocation getFabricId() {
    return ResourceLocation.fromNamespaceAndPath(DynamicDifficulty.MODID, "dimensions_leveling_settings");
  }

  @Override
  protected void apply(
      Map<ResourceLocation, JsonElement> jsonElements,
      @NotNull ResourceManager resourceManager,
      @NotNull ProfilerFiller profilerFiller) {
    INDIVIDUAL_SETTINGS.clear();
    jsonElements.forEach(this::loadSettings);
    LOGGER.info("Loaded {} individual dimension leveling settings from 'leveling_settings/dimensions'", INDIVIDUAL_SETTINGS.size());
  }

  private void loadSettings(ResourceLocation dimensionKey, JsonElement jsonElement) {
    try {
      var ops = com.mojang.serialization.JsonOps.INSTANCE;
      DimensionLevelingSettings.CODEC.decode(ops, jsonElement)
          .result()
          .ifPresentOrElse(
              pair -> {
                INDIVIDUAL_SETTINGS.put(dimensionKey, pair.getFirst());
                LOGGER.info("Loaded leveling settings for dimension {}", dimensionKey);
              },
              () -> LOGGER.error("Couldn't parse data file {}", dimensionKey)
          );
    } catch (Exception exception) {
      LOGGER.error("Couldn't load leveling settings for dimension {}", dimensionKey, exception);
    }
  }
}
