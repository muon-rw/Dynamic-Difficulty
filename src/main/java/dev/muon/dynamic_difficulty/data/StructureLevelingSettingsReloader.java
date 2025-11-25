package dev.muon.dynamic_difficulty.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import dev.muon.dynamic_difficulty.DynamicDifficulty;
import dev.muon.dynamic_difficulty.settings.StructureBonusSettings;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.neoforged.neoforge.resource.ContextAwareReloadListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class StructureLevelingSettingsReloader extends ContextAwareReloadListener {
  private static final Logger LOGGER = LogUtils.getLogger();
  private static final Gson GSON = new Gson();
  private static final Map<ResourceLocation, StructureBonusSettings> INDIVIDUAL_SETTINGS = new HashMap<>();
  private static final Map<ResourceLocation, StructureBonusSettings> TAG_SETTINGS = new HashMap<>();
  private final SimpleJsonResourceReloadListener jsonReloader;

  public StructureLevelingSettingsReloader() {
    this.jsonReloader = new SimpleJsonResourceReloadListener(GSON, "leveling_settings/structures") {
      @Override
      protected void apply(Map<ResourceLocation, JsonElement> prepared, @NotNull ResourceManager resourceManager, @NotNull ProfilerFiller profiler) {
        StructureLevelingSettingsReloader.this.apply(prepared, resourceManager, profiler);
      }
    };
  }

  @Nullable
  public static StructureBonusSettings get(ResourceLocation structureId, Registry<Structure> structureRegistry) {
    // Check individual structure settings first (they take precedence)
    if (INDIVIDUAL_SETTINGS.containsKey(structureId)) {
      return INDIVIDUAL_SETTINGS.get(structureId);
    }
    
    // Check structure tags
    Optional<Holder.Reference<Structure>> optHolder = structureRegistry.getHolder(structureId);
    if (optHolder.isPresent()) {
      Holder<Structure> structureHolder = optHolder.get();
      // Find the highest bonus from matching tags
      int highestBonus = 0;
      StructureBonusSettings bestMatch = null;
      
      for (Map.Entry<ResourceLocation, StructureBonusSettings> tagEntry : TAG_SETTINGS.entrySet()) {
        TagKey<Structure> structureTag = TagKey.create(Registries.STRUCTURE, tagEntry.getKey());
        if (structureHolder.is(structureTag)) {
          StructureBonusSettings tagSettings = tagEntry.getValue();
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

  public static int getLevelBonus(ResourceLocation structureId, Registry<Structure> structureRegistry) {
    StructureBonusSettings settings = get(structureId, structureRegistry);
    return settings != null ? settings.levelBonus() : 0;
  }

  public static boolean bypassesCap(ResourceLocation structureId, Registry<Structure> structureRegistry) {
    StructureBonusSettings settings = get(structureId, structureRegistry);
    return settings != null ? settings.bypassesCap() : true; // Default to true for structures
  }
  
  public static Map<ResourceLocation, StructureBonusSettings> getIndividualSettings() {
    return new HashMap<>(INDIVIDUAL_SETTINGS);
  }
  
  public static Map<ResourceLocation, StructureBonusSettings> getTagSettings() {
    return new HashMap<>(TAG_SETTINGS);
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
    LOGGER.info("Loading structure leveling settings from 'leveling_settings/structures'");
    INDIVIDUAL_SETTINGS.clear();
    
    var ops = makeConditionalOps();
    for (Map.Entry<ResourceLocation, JsonElement> entry : prepared.entrySet()) {
      StructureBonusSettings.CODEC.decode(ops, entry.getValue())
          .result()
          .ifPresentOrElse(
              pair -> INDIVIDUAL_SETTINGS.put(entry.getKey(), pair.getFirst()),
              () -> LOGGER.error("Couldn't parse data file {}", entry.getKey())
          );
    }
    
    LOGGER.info("Loaded {} individual structure leveling settings from 'leveling_settings/structures'", INDIVIDUAL_SETTINGS.size());
  }
  
  public static void loadTagSettings(Map<ResourceLocation, StructureBonusSettings> tagSettings) {
    TAG_SETTINGS.clear();
    TAG_SETTINGS.putAll(tagSettings);
    LOGGER.info("Loaded {} structure tag leveling settings from 'leveling_settings/structure_tags'", TAG_SETTINGS.size());
  }
}
