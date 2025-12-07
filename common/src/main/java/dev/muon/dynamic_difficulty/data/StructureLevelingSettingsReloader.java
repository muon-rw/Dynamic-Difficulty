package dev.muon.dynamic_difficulty.data;

import com.mojang.logging.LogUtils;
import dev.muon.dynamic_difficulty.settings.StructureBonusSettings;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
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
  private static final Map<ResourceLocation, StructureBonusSettings> INDIVIDUAL_SETTINGS = new HashMap<>();
  private static final Map<ResourceLocation, StructureBonusSettings> TAG_SETTINGS = new HashMap<>();

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
    return settings == null || settings.bypassesCap(); // Default to true for structures
  }
  
  public static Map<ResourceLocation, StructureBonusSettings> getIndividualSettings() {
    return new HashMap<>(INDIVIDUAL_SETTINGS);
  }
  
  public static Map<ResourceLocation, StructureBonusSettings> getTagSettings() {
    return new HashMap<>(TAG_SETTINGS);
  }

  /**
   * Load individual structure settings. Called by platform-specific reloaders.
   */
  public static void loadSettings(Map<ResourceLocation, StructureBonusSettings> settings) {
    INDIVIDUAL_SETTINGS.clear();
    INDIVIDUAL_SETTINGS.putAll(settings);
    LOGGER.info("Loaded {} individual structure leveling settings from 'leveling_settings/structures'", INDIVIDUAL_SETTINGS.size());
  }
  
  /**
   * Load tag-based structure settings. Called by platform-specific reloaders.
   */
  public static void loadTagSettings(Map<ResourceLocation, StructureBonusSettings> tagSettings) {
    TAG_SETTINGS.clear();
    TAG_SETTINGS.putAll(tagSettings);
    LOGGER.info("Loaded {} structure tag leveling settings from 'leveling_settings/structure_tags'", TAG_SETTINGS.size());
  }
}
