package dev.muon.dynamic_difficulty.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import dev.muon.dynamic_difficulty.settings.DimensionLevelingSettings;
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
  // Store raw settings - they get resolved at lookup time with dimension fallback
  private static final Map<ResourceLocation, EntityLevelingSettings.RawSettings> INDIVIDUAL_SETTINGS = new HashMap<>();
  private static final Map<ResourceLocation, EntityLevelingSettings.RawSettings> TAG_SETTINGS = new HashMap<>();
  private final SimpleJsonResourceReloadListener jsonReloader;

  public EntityLevelingSettingsReloader() {
    this.jsonReloader = new SimpleJsonResourceReloadListener(GSON, "leveling_settings/entities") {
      @Override
      protected void apply(Map<ResourceLocation, JsonElement> prepared, @NotNull ResourceManager resourceManager, @NotNull ProfilerFiller profiler) {
        EntityLevelingSettingsReloader.this.apply(prepared, resourceManager, profiler);
      }
    };
  }

  /**
   * Gets resolved entity settings, falling back to dimension settings for any omitted fields.
   * Returns null if no entity-specific settings exist (caller should use dimension settings directly).
   */
  @Nullable
  public static EntityLevelingSettings get(EntityType<?> entityType, DimensionLevelingSettings dimSettings) {
    ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
    
    // Check individual entity settings first (they take precedence)
    if (INDIVIDUAL_SETTINGS.containsKey(entityId)) {
      return INDIVIDUAL_SETTINGS.get(entityId).resolve(dimSettings);
    }
    
    // Check entity tags
    Optional<Holder.Reference<EntityType<?>>> optHolder = BuiltInRegistries.ENTITY_TYPE.getHolder(entityId);
    if (optHolder.isPresent()) {
      Holder<EntityType<?>> entityHolder = optHolder.get();
      // Find the first matching tag (tags are checked in order, first match wins)
      for (Map.Entry<ResourceLocation, EntityLevelingSettings.RawSettings> tagEntry : TAG_SETTINGS.entrySet()) {
        TagKey<EntityType<?>> entityTag = TagKey.create(Registries.ENTITY_TYPE, tagEntry.getKey());
        if (entityHolder.is(entityTag)) {
          return tagEntry.getValue().resolve(dimSettings);
        }
      }
    }
    
    return null;
  }

  /**
   * Checks if an entity type has custom settings (individual or tag-based).
   */
  public static boolean hasCustomSettings(EntityType<?> entityType) {
    ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
    
    if (INDIVIDUAL_SETTINGS.containsKey(entityId)) {
      return true;
    }
    
    Optional<Holder.Reference<EntityType<?>>> optHolder = BuiltInRegistries.ENTITY_TYPE.getHolder(entityId);
    if (optHolder.isPresent()) {
      Holder<EntityType<?>> entityHolder = optHolder.get();
      for (Map.Entry<ResourceLocation, EntityLevelingSettings.RawSettings> tagEntry : TAG_SETTINGS.entrySet()) {
        TagKey<EntityType<?>> entityTag = TagKey.create(Registries.ENTITY_TYPE, tagEntry.getKey());
        if (entityHolder.is(entityTag)) {
          return true;
        }
      }
    }
    
    return false;
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
      EntityLevelingSettings.RAW_CODEC.decode(ops, entry.getValue())
          .result()
          .ifPresentOrElse(
              pair -> INDIVIDUAL_SETTINGS.put(entry.getKey(), pair.getFirst()),
              () -> LOGGER.error("Couldn't parse data file {}", entry.getKey())
          );
    }
    
    LOGGER.info("Loaded {} individual entity leveling settings from 'leveling_settings/entities'", INDIVIDUAL_SETTINGS.size());
  }
  
  public static void loadTagSettings(Map<ResourceLocation, EntityLevelingSettings.RawSettings> tagSettings) {
    TAG_SETTINGS.clear();
    TAG_SETTINGS.putAll(tagSettings);
    LOGGER.info("Loaded {} entity tag leveling settings from 'leveling_settings/entity_tags'", TAG_SETTINGS.size());
  }
}
