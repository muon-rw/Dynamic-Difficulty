package dev.muon.dynamic_difficulty.data;

import com.mojang.logging.LogUtils;
import dev.muon.dynamic_difficulty.settings.StructureBonusSettings;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Storage and lookup for structure leveling settings.
 * Platform-specific reloaders populate this via loadSettings/loadTagSettings.
 */
public class StructureLevelingSettingsReloader {
  private static final Logger LOGGER = LogUtils.getLogger();
  private static final Map<Identifier, StructureBonusSettings> INDIVIDUAL_SETTINGS = new HashMap<>();
  private static final Map<TagKey<Structure>, StructureBonusSettings> TAG_SETTINGS = new HashMap<>();

  @Nullable
  public static StructureBonusSettings get(Identifier structureId, Registry<Structure> structureRegistry) {
    StructureBonusSettings individual = INDIVIDUAL_SETTINGS.get(structureId);
    if (individual != null) {
      return individual;
    }

    if (TAG_SETTINGS.isEmpty()) {
      return null;
    }

    Optional<Holder.Reference<Structure>> optHolder = structureRegistry.get(structureId);
    if (optHolder.isEmpty()) {
      return null;
    }

    Holder<Structure> structureHolder = optHolder.get();
    StructureBonusSettings best = null;
    int highestBonus = 0;
    for (Map.Entry<TagKey<Structure>, StructureBonusSettings> tagEntry : TAG_SETTINGS.entrySet()) {
      if (structureHolder.is(tagEntry.getKey())) {
        StructureBonusSettings tagSettings = tagEntry.getValue();
        if (tagSettings.levelBonus() > highestBonus) {
          highestBonus = tagSettings.levelBonus();
          best = tagSettings;
        }
      }
    }
    return best;
  }

  /**
   * Load individual structure settings. Called by platform-specific reloaders.
   */
  public static void loadSettings(Map<Identifier, StructureBonusSettings> settings) {
    INDIVIDUAL_SETTINGS.clear();
    INDIVIDUAL_SETTINGS.putAll(settings);
    LOGGER.info("Loaded {} individual structure leveling settings from 'leveling_settings/structures'", INDIVIDUAL_SETTINGS.size());
  }

  /**
   * Load tag-based structure settings. Called by platform-specific reloaders.
   */
  public static void loadTagSettings(Map<Identifier, StructureBonusSettings> tagSettings) {
    TAG_SETTINGS.clear();
    tagSettings.forEach((id, settings) -> TAG_SETTINGS.put(TagKey.create(Registries.STRUCTURE, id), settings));
    LOGGER.info("Loaded {} structure tag leveling settings from 'leveling_settings/structure_tags'", TAG_SETTINGS.size());
  }
}
