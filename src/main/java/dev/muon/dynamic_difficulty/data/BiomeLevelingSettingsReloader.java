package dev.muon.dynamic_difficulty.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import dev.muon.dynamic_difficulty.DynamicDifficulty;
import dev.muon.dynamic_difficulty.settings.BiomeBonusSettings;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.neoforge.resource.ContextAwareReloadListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class BiomeLevelingSettingsReloader extends ContextAwareReloadListener {
  private static final Logger LOGGER = LogUtils.getLogger();
  private static final Gson GSON = new Gson();
  private static final Map<ResourceLocation, BiomeBonusSettings> INDIVIDUAL_SETTINGS = new HashMap<>();
  private static final Map<ResourceLocation, BiomeBonusSettings> TAG_SETTINGS = new HashMap<>();
  private final SimpleJsonResourceReloadListener jsonReloader;

  public BiomeLevelingSettingsReloader() {
    this.jsonReloader = new SimpleJsonResourceReloadListener(GSON, "leveling_settings/biomes") {
      @Override
      protected void apply(Map<ResourceLocation, JsonElement> prepared, @NotNull ResourceManager resourceManager, @NotNull ProfilerFiller profiler) {
        BiomeLevelingSettingsReloader.this.apply(prepared, resourceManager, profiler);
      }
    };
  }

  @Nullable
  public static BiomeBonusSettings get(ResourceLocation biomeId, Registry<Biome> biomeRegistry) {
    // Check individual biome settings first (they take precedence)
    if (INDIVIDUAL_SETTINGS.containsKey(biomeId)) {
      return INDIVIDUAL_SETTINGS.get(biomeId);
    }
    
    // Check biome tags
    Optional<Holder.Reference<Biome>> optHolder = biomeRegistry.getHolder(biomeId);
    if (optHolder.isPresent()) {
      Holder<Biome> biomeHolder = optHolder.get();
      // Find the highest bonus from matching tags
      int highestBonus = 0;
      BiomeBonusSettings bestMatch = null;
      
      for (Map.Entry<ResourceLocation, BiomeBonusSettings> tagEntry : TAG_SETTINGS.entrySet()) {
        TagKey<Biome> biomeTag = TagKey.create(Registries.BIOME, tagEntry.getKey());
        if (biomeHolder.is(biomeTag)) {
          BiomeBonusSettings tagSettings = tagEntry.getValue();
          if (tagSettings.levelBonus() > highestBonus) {
            highestBonus = tagSettings.levelBonus();
            bestMatch = tagSettings;
          }
        }
      }
      
      return bestMatch;
    }
    
    return null;
  }

  public static int getLevelBonus(ResourceLocation biomeId, Registry<Biome> biomeRegistry) {
    BiomeBonusSettings settings = get(biomeId, biomeRegistry);
    return settings != null ? settings.levelBonus() : 0;
  }

  public static boolean bypassesCap(ResourceLocation biomeId, Registry<Biome> biomeRegistry) {
    BiomeBonusSettings settings = get(biomeId, biomeRegistry);
    return settings != null ? settings.bypassesCap() : false; // Default to false for biomes
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
    LOGGER.info("Loading biome leveling settings from 'leveling_settings/biomes'");
    INDIVIDUAL_SETTINGS.clear();
    
    var ops = makeConditionalOps();
    for (Map.Entry<ResourceLocation, JsonElement> entry : prepared.entrySet()) {
      BiomeBonusSettings.CODEC.decode(ops, entry.getValue())
          .result()
          .ifPresentOrElse(
              pair -> INDIVIDUAL_SETTINGS.put(entry.getKey(), pair.getFirst()),
              () -> LOGGER.error("Couldn't parse data file {}", entry.getKey())
          );
    }
    
    LOGGER.info("Loaded {} individual biome leveling settings from 'leveling_settings/biomes'", INDIVIDUAL_SETTINGS.size());
  }
  
  public static void loadTagSettings(Map<ResourceLocation, BiomeBonusSettings> tagSettings) {
    TAG_SETTINGS.clear();
    TAG_SETTINGS.putAll(tagSettings);
    LOGGER.info("Loaded {} biome tag leveling settings from 'leveling_settings/biome_tags'", TAG_SETTINGS.size());
  }
}
