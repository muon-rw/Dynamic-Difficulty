package dev.muon.dynamic_difficulty.data;

import com.mojang.logging.LogUtils;
import dev.muon.dynamic_difficulty.settings.EntityLevelingSettings;
import dev.muon.dynamic_difficulty.settings.LevelingSettings;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class EntityLevelingSettingsStore {
  private static final Logger LOGGER = LogUtils.getLogger();
  private static final Map<Identifier, EntityLevelingSettings.RawSettings> INDIVIDUAL_SETTINGS = new HashMap<>();
  private static final Map<TagKey<EntityType<?>>, EntityLevelingSettings.RawSettings> TAG_SETTINGS = new HashMap<>();

  /**
   * Gets resolved entity settings, falling back to the supplied prior tier for any omitted fields.
   * Prior is typically the chain-resolved settings (dimension → biome → structure).
   * Returns null if no entity-specific settings exist (caller should use the prior settings directly).
   */
  @Nullable
  public static EntityLevelingSettings get(EntityType<?> entityType, LevelingSettings prior) {
    EntityLevelingSettings.RawSettings raw = getRaw(entityType);
    return raw == null ? null : raw.resolve(prior);
  }

  /**
   * Gets the raw entity settings (Optional fields) without resolving against any prior tier.
   * Returns null if there is no individual or tag-based entry for this entity type.
   */
  @Nullable
  public static EntityLevelingSettings.RawSettings getRaw(EntityType<?> entityType) {
    Identifier entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);

    EntityLevelingSettings.RawSettings individual = INDIVIDUAL_SETTINGS.get(entityId);
    if (individual != null) {
      return individual;
    }

    if (TAG_SETTINGS.isEmpty()) {
      return null;
    }

    Optional<Holder.Reference<EntityType<?>>> optHolder = BuiltInRegistries.ENTITY_TYPE.get(entityId);
    if (optHolder.isEmpty()) {
      return null;
    }

    Holder<EntityType<?>> entityHolder = optHolder.get();
    for (Map.Entry<TagKey<EntityType<?>>, EntityLevelingSettings.RawSettings> tagEntry : TAG_SETTINGS.entrySet()) {
      if (entityHolder.is(tagEntry.getKey())) {
        return tagEntry.getValue();
      }
    }
    return null;
  }

  public static boolean hasCustomSettings(EntityType<?> entityType) {
    Identifier entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);

    if (INDIVIDUAL_SETTINGS.containsKey(entityId)) {
      return true;
    }

    if (TAG_SETTINGS.isEmpty()) {
      return false;
    }

    Optional<Holder.Reference<EntityType<?>>> optHolder = BuiltInRegistries.ENTITY_TYPE.get(entityId);
    if (optHolder.isEmpty()) {
      return false;
    }

    Holder<EntityType<?>> entityHolder = optHolder.get();
    for (TagKey<EntityType<?>> tag : TAG_SETTINGS.keySet()) {
      if (entityHolder.is(tag)) {
        return true;
      }
    }
    return false;
  }

  public static void loadSettings(Map<Identifier, EntityLevelingSettings.RawSettings> settings) {
    INDIVIDUAL_SETTINGS.clear();
    INDIVIDUAL_SETTINGS.putAll(settings);
    LOGGER.info("Loaded {} individual entity leveling settings from 'leveling_settings/entities'", INDIVIDUAL_SETTINGS.size());
  }

  public static void loadTagSettings(Map<Identifier, EntityLevelingSettings.RawSettings> tagSettings) {
    TAG_SETTINGS.clear();
    tagSettings.forEach((id, settings) -> TAG_SETTINGS.put(TagKey.create(Registries.ENTITY_TYPE, id), settings));
    LOGGER.info("Loaded {} entity tag leveling settings from 'leveling_settings/entity_tags'", TAG_SETTINGS.size());
  }
}
