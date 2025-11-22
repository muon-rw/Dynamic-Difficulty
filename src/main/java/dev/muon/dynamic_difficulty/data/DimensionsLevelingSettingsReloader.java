package dev.muon.dynamic_difficulty.data;

import com.mojang.logging.LogUtils;
import dev.muon.dynamic_difficulty.DynamicDifficulty;
import dev.muon.dynamic_difficulty.config.Config;
import dev.muon.dynamic_difficulty.settings.DimensionLevelingSettings;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class DimensionsLevelingSettingsReloader extends SimpleJsonResourceReloadListener<DimensionLevelingSettings> {
  private static final Logger LOGGER = LogUtils.getLogger();
  private static final Map<ResourceLocation, DimensionLevelingSettings> INDIVIDUAL_SETTINGS = new HashMap<>();
  private static final Map<ResourceLocation, DimensionLevelingSettings> TAG_SETTINGS = new HashMap<>();
  private static final ResourceLocation RELOADER_ID = ResourceLocation.fromNamespaceAndPath(DynamicDifficulty.MODID, "dimensions_leveling_settings");

  public DimensionsLevelingSettingsReloader() {
    super(DimensionLevelingSettings.CODEC, net.minecraft.resources.FileToIdConverter.json("leveling_settings/dimensions"));
  }

  @NotNull
  public static DimensionLevelingSettings get(ResourceKey<Level> dimension) {
    ResourceLocation dimensionId = dimension.location();
    
    // Check individual dimension settings first (they take precedence)
    if (INDIVIDUAL_SETTINGS.containsKey(dimensionId)) {
      return INDIVIDUAL_SETTINGS.get(dimensionId);
    }
    
    // Check dimension tags (requires registry access, but we'll try to check if available)
    // Note: Dimension tags are less common, but we support them for consistency
    // For now, we'll check tags if we have access to the registry
    // In practice, most lookups will be from LevelingSystem which has entity context
    
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
    Optional<Holder.Reference<Level>> optHolder = dimensionRegistry.get(dimension);
    if (optHolder.isPresent()) {
      Holder<Level> dimensionHolder = optHolder.get();
      // Find the first matching tag (tags are checked in order, first match wins)
      for (Map.Entry<ResourceLocation, DimensionLevelingSettings> tagEntry : TAG_SETTINGS.entrySet()) {
        TagKey<Level> dimensionTag = TagKey.create(Registries.DIMENSION, tagEntry.getKey());
        if (dimensionHolder.is(dimensionTag)) {
          return tagEntry.getValue();
        }
      }
    }
    
    // Fall back to default if no match found
    return createDefaultSettings();
  }

  private static DimensionLevelingSettings createDefaultSettings() {
    return new DimensionLevelingSettings(
        Config.COMMON.startingLevel.get(),
        Config.COMMON.maxLevel.get(),
        Config.COMMON.levelsPerDistance.get().floatValue(),
        Config.COMMON.levelsPerDeepness.get().floatValue(),
        Config.COMMON.randomLevelBonus.get(),
        null,
        Map.of());
  }

  public static ResourceLocation getReloaderId() {
    return RELOADER_ID;
  }

  @Override
  protected void apply(
      Map<ResourceLocation, DimensionLevelingSettings> prepared,
      @NotNull ResourceManager resourceManager,
      @NotNull ProfilerFiller profilerFiller) {
    INDIVIDUAL_SETTINGS.clear();
    INDIVIDUAL_SETTINGS.putAll(prepared);
    LOGGER.info("Loaded {} individual dimension leveling settings from 'leveling_settings/dimensions'", INDIVIDUAL_SETTINGS.size());
  }
  
  public static void loadTagSettings(Map<ResourceLocation, DimensionLevelingSettings> tagSettings) {
    TAG_SETTINGS.clear();
    TAG_SETTINGS.putAll(tagSettings);
    LOGGER.info("Loaded {} dimension tag leveling settings from 'leveling_settings/dimension_tags'", TAG_SETTINGS.size());
  }
}
