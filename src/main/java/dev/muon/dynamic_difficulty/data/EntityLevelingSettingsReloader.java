package dev.muon.dynamic_difficulty.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import dev.muon.dynamic_difficulty.DynamicDifficulty;
import dev.muon.dynamic_difficulty.settings.EntityLevelingSettings;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.resource.ContextAwareReloadListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class EntityLevelingSettingsReloader extends ContextAwareReloadListener {
  private static final Logger LOGGER = LogUtils.getLogger();
  private static final Gson GSON = new Gson();
  private static final Map<ResourceLocation, EntityLevelingSettings> INDIVIDUAL_SETTINGS = new HashMap<>();
  private static final Map<ResourceLocation, EntityLevelingSettings> TAG_SETTINGS = new HashMap<>();
  private final SimpleJsonResourceReloadListener jsonReloader;

  public EntityLevelingSettingsReloader() {
    this.jsonReloader = new SimpleJsonResourceReloadListener(GSON, "leveling_settings/entities") {
      @Override
      protected void apply(Map<ResourceLocation, JsonElement> prepared, @NotNull ResourceManager resourceManager, @NotNull ProfilerFiller profiler) {
        EntityLevelingSettingsReloader.this.apply(prepared, resourceManager, profiler);
      }
    };
  }

  @Nullable
  public static EntityLevelingSettings get(EntityType<?> entityType) {
    ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
    
    // Check individual entity settings first (they take precedence)
    if (INDIVIDUAL_SETTINGS.containsKey(entityId)) {
      return INDIVIDUAL_SETTINGS.get(entityId);
    }
    
    // Check entity tags
    Optional<Holder.Reference<EntityType<?>>> optHolder = BuiltInRegistries.ENTITY_TYPE.getHolder(entityId);
    if (optHolder.isPresent()) {
      Holder<EntityType<?>> entityHolder = optHolder.get();
      // Find the first matching tag (tags are checked in order, first match wins)
      for (Map.Entry<ResourceLocation, EntityLevelingSettings> tagEntry : TAG_SETTINGS.entrySet()) {
        TagKey<EntityType<?>> entityTag = TagKey.create(Registries.ENTITY_TYPE, tagEntry.getKey());
        if (entityHolder.is(entityTag)) {
          return tagEntry.getValue();
        }
      }
    }
    
    return null;
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
    LOGGER.info("Loading entity leveling settings from 'leveling_settings/entities'");
    INDIVIDUAL_SETTINGS.clear();
    
    var ops = makeConditionalOps();
    for (Map.Entry<ResourceLocation, JsonElement> entry : prepared.entrySet()) {
      EntityLevelingSettings.CODEC.decode(ops, entry.getValue())
          .result()
          .ifPresentOrElse(
              pair -> INDIVIDUAL_SETTINGS.put(entry.getKey(), pair.getFirst()),
              () -> LOGGER.error("Couldn't parse data file {}", entry.getKey())
          );
    }
    
    LOGGER.info("Loaded {} individual entity leveling settings from 'leveling_settings/entities'", INDIVIDUAL_SETTINGS.size());
  }
  
  public static void loadTagSettings(Map<ResourceLocation, EntityLevelingSettings> tagSettings) {
    TAG_SETTINGS.clear();
    TAG_SETTINGS.putAll(tagSettings);
    LOGGER.info("Loaded {} entity tag leveling settings from 'leveling_settings/entity_tags'", TAG_SETTINGS.size());
  }
}
