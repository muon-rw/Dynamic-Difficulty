package dev.muon.dynamic_difficulty.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import dev.muon.dynamic_difficulty.DynamicDifficulty;
import dev.muon.dynamic_difficulty.settings.StructureBonusSettings;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.resource.ContextAwareReloadListener;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class StructureTagLevelingSettingsReloader extends ContextAwareReloadListener {
  private static final Logger LOGGER = LogUtils.getLogger();
  private static final Gson GSON = new Gson();
  private final SimpleJsonResourceReloadListener jsonReloader;

  public StructureTagLevelingSettingsReloader() {
    this.jsonReloader = new SimpleJsonResourceReloadListener(GSON, "leveling_settings/structure_tags") {
      @Override
      protected void apply(Map<ResourceLocation, JsonElement> prepared, @NotNull ResourceManager resourceManager, @NotNull ProfilerFiller profiler) {
        StructureTagLevelingSettingsReloader.this.apply(prepared, resourceManager, profiler);
      }
    };
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
    LOGGER.info("Loading structure tag leveling settings from 'leveling_settings/structure_tags'");
    
    Map<ResourceLocation, StructureBonusSettings> parsed = new HashMap<>();
    var ops = makeConditionalOps();
    for (Map.Entry<ResourceLocation, JsonElement> entry : prepared.entrySet()) {
      StructureBonusSettings.CODEC.decode(ops, entry.getValue())
          .result()
          .ifPresentOrElse(
              pair -> parsed.put(entry.getKey(), pair.getFirst()),
              () -> LOGGER.error("Couldn't parse data file {}", entry.getKey())
          );
    }
    
    StructureLevelingSettingsReloader.loadTagSettings(parsed);
  }
}
