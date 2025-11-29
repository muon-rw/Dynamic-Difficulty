package dev.muon.dynamic_difficulty.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import dev.muon.dynamic_difficulty.DynamicDifficulty;
import dev.muon.dynamic_difficulty.config.Config;
import dev.muon.dynamic_difficulty.settings.DimensionLevelingSettings;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.resource.ContextAwareReloadListener;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class DimensionsLevelingSettingsReloader extends ContextAwareReloadListener {
  private static final Logger LOGGER = LogUtils.getLogger();
  private static final Gson GSON = new Gson();
  private static final Map<ResourceLocation, DimensionLevelingSettings> INDIVIDUAL_SETTINGS = new HashMap<>();
  private static final Map<ResourceLocation, DimensionLevelingSettings> TAG_SETTINGS = new HashMap<>();
  private final SimpleJsonResourceReloadListener jsonReloader;

  public DimensionsLevelingSettingsReloader() {
    this.jsonReloader = new SimpleJsonResourceReloadListener(GSON, "leveling_settings/dimensions") {
      @Override
      protected void apply(Map<ResourceLocation, JsonElement> prepared, @NotNull ResourceManager resourceManager, @NotNull ProfilerFiller profiler) {
        DimensionsLevelingSettingsReloader.this.apply(prepared, resourceManager, profiler);
      }
    };
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
  public static DimensionLevelingSettings get(ResourceKey<Level> dimension, Registry<Level> dimensionRegistry) {
    ResourceLocation dimensionId = dimension.location();
    
    // Check individual dimension settings first (they take precedence)
    if (INDIVIDUAL_SETTINGS.containsKey(dimensionId)) {
      return INDIVIDUAL_SETTINGS.get(dimensionId);
    }
    
    // Check dimension tags
    Optional<Holder.Reference<Level>> optHolder = dimensionRegistry.getHolder(dimension);
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
    return DimensionLevelingSettings.createDefault();
  }

  @Override
  public CompletableFuture<Void> reload(
      PreparableReloadListener.PreparationBarrier stage,
      ResourceManager resourceManager,
      ProfilerFiller preparationsProfiler,
      ProfilerFiller reloadProfiler,
      Executor backgroundExecutor,
      Executor gameExecutor) {
    return jsonReloader.reload(stage, resourceManager, preparationsProfiler, reloadProfiler, backgroundExecutor, gameExecutor);
  }

  @Override
  public String getName() {
    return jsonReloader.getName();
  }

  protected void apply(
      Map<ResourceLocation, JsonElement> prepared,
      @NotNull ResourceManager resourceManager,
      @NotNull ProfilerFiller profilerFiller) {
    INDIVIDUAL_SETTINGS.clear();
    
    var ops = makeConditionalOps();
    for (Map.Entry<ResourceLocation, JsonElement> entry : prepared.entrySet()) {
      DimensionLevelingSettings.CODEC.decode(ops, entry.getValue())
          .result()
          .ifPresentOrElse(
              pair -> INDIVIDUAL_SETTINGS.put(entry.getKey(), pair.getFirst()),
              () -> LOGGER.error("Couldn't parse data file {}", entry.getKey())
          );
    }
    
    LOGGER.info("Loaded {} individual dimension leveling settings from 'leveling_settings/dimensions'", INDIVIDUAL_SETTINGS.size());
  }
  
  public static void loadTagSettings(Map<ResourceLocation, DimensionLevelingSettings> tagSettings) {
    TAG_SETTINGS.clear();
    TAG_SETTINGS.putAll(tagSettings);
    LOGGER.info("Loaded {} dimension tag leveling settings from 'leveling_settings/dimension_tags'", TAG_SETTINGS.size());
  }

  /**
   * Checks if a dimension has custom leveling settings (either individual or tag-based).
   * Returns true if the dimension has non-default settings, false otherwise.
   */
  public static boolean hasCustomSettings(ResourceKey<Level> dimension, Registry<Level> dimensionRegistry) {
    ResourceLocation dimensionId = dimension.location();
    
    // Check if dimension has individual settings
    if (INDIVIDUAL_SETTINGS.containsKey(dimensionId)) {
      return true;
    }
    
    // Check if dimension matches any tag with settings
    Optional<Holder.Reference<Level>> optHolder = dimensionRegistry.getHolder(dimension);
    if (optHolder.isPresent()) {
      Holder<Level> dimensionHolder = optHolder.get();
      for (Map.Entry<ResourceLocation, DimensionLevelingSettings> tagEntry : TAG_SETTINGS.entrySet()) {
        TagKey<Level> dimensionTag = TagKey.create(Registries.DIMENSION, tagEntry.getKey());
        if (dimensionHolder.is(dimensionTag)) {
          return true;
        }
      }
    }
    
    return false;
  }
}
