package dev.muon.dynamic_difficulty.data;

import com.mojang.logging.LogUtils;
import dev.muon.dynamic_difficulty.settings.BiomeBonusSettings;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Storage and lookup for biome leveling settings.
 * Platform-specific reloaders populate this via loadSettings/loadTagSettings.
 */
public class BiomeLevelingSettingsReloader {
  private static final Logger LOGGER = LogUtils.getLogger();
  private static final Map<ResourceLocation, BiomeBonusSettings> INDIVIDUAL_SETTINGS = new HashMap<>();
  private static final Map<ResourceLocation, BiomeBonusSettings> TAG_SETTINGS = new HashMap<>();

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
    return settings != null && settings.bypassesCap();
  }

  /**
   * Load individual biome settings. Called by platform-specific reloaders.
   */
  public static void loadSettings(Map<ResourceLocation, BiomeBonusSettings> settings) {
    INDIVIDUAL_SETTINGS.clear();
    INDIVIDUAL_SETTINGS.putAll(settings);
    LOGGER.info("Loaded {} individual biome leveling settings from 'leveling_settings/biomes'", INDIVIDUAL_SETTINGS.size());
  }
  
  /**
   * Load tag-based biome settings. Called by platform-specific reloaders.
   */
  public static void loadTagSettings(Map<ResourceLocation, BiomeBonusSettings> tagSettings) {
    TAG_SETTINGS.clear();
    TAG_SETTINGS.putAll(tagSettings);
    LOGGER.info("Loaded {} biome tag leveling settings from 'leveling_settings/biome_tags'", TAG_SETTINGS.size());
  }
}
