package dev.muon.dynamic_difficulty.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import dev.muon.dynamic_difficulty.DynamicDifficulty;
import dev.muon.dynamic_difficulty.settings.DimensionLevelingSettings;
import dev.muon.dynamic_difficulty.settings.EntityLevelingSettings;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EntityType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

public class EntityLevelingSettingsReloader extends SimpleJsonResourceReloadListener implements IdentifiableResourceReloadListener {
  private static final Logger LOGGER = LogUtils.getLogger();
  private static final Gson GSON = new Gson();
  // Store raw settings - they get resolved at lookup time with dimension fallback
  private static final Map<ResourceLocation, EntityLevelingSettings.RawSettings> INDIVIDUAL_SETTINGS = new HashMap<>();
  private static final Map<ResourceLocation, EntityLevelingSettings.RawSettings> TAG_SETTINGS = new HashMap<>();

  public EntityLevelingSettingsReloader() {
    super(GSON, "leveling_settings/entities");
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
    var optHolder = BuiltInRegistries.ENTITY_TYPE.getHolder(entityId);
    if (optHolder.isPresent()) {
      var entityHolder = optHolder.get();
      // Find the first matching tag (tags are checked in order, first match wins)
      for (Map.Entry<ResourceLocation, EntityLevelingSettings.RawSettings> tagEntry : TAG_SETTINGS.entrySet()) {
        var entityTag = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE, tagEntry.getKey());
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
    
    var optHolder = BuiltInRegistries.ENTITY_TYPE.getHolder(entityId);
    if (optHolder.isPresent()) {
      var entityHolder = optHolder.get();
      for (Map.Entry<ResourceLocation, EntityLevelingSettings.RawSettings> tagEntry : TAG_SETTINGS.entrySet()) {
        var entityTag = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE, tagEntry.getKey());
        if (entityHolder.is(entityTag)) {
          return true;
        }
      }
    }
    
    return false;
  }

  @Override
  public ResourceLocation getFabricId() {
    return ResourceLocation.fromNamespaceAndPath(DynamicDifficulty.MODID, "entity_leveling_settings");
  }

  @Override
  protected void apply(Map<ResourceLocation, JsonElement> map, @NotNull ResourceManager resourceManager, @NotNull ProfilerFiller profiler) {
    LOGGER.info("Loading entity leveling settings from 'leveling_settings/entities'");
    INDIVIDUAL_SETTINGS.clear();
    map.forEach(this::loadSettings);
    LOGGER.info("Loaded {} individual entity leveling settings from 'leveling_settings/entities'", INDIVIDUAL_SETTINGS.size());
  }
  
  public static void loadTagSettings(Map<ResourceLocation, EntityLevelingSettings.RawSettings> tagSettings) {
    TAG_SETTINGS.clear();
    TAG_SETTINGS.putAll(tagSettings);
    LOGGER.info("Loaded {} entity tag leveling settings from 'leveling_settings/entity_tags'", TAG_SETTINGS.size());
  }

  private void loadSettings(ResourceLocation entityKey, JsonElement jsonElement) {
    try {
      var ops = com.mojang.serialization.JsonOps.INSTANCE;
      EntityLevelingSettings.RAW_CODEC.decode(ops, jsonElement)
          .result()
          .ifPresentOrElse(
              pair -> {
                INDIVIDUAL_SETTINGS.put(entityKey, pair.getFirst());
                LOGGER.info("Loaded leveling settings for entity {}", entityKey);
              },
              () -> LOGGER.error("Couldn't parse data file {}", entityKey)
          );
    } catch (Exception exception) {
      LOGGER.error("Couldn't load leveling settings for entity {}", entityKey, exception);
    }
  }
}
