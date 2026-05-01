package dev.muon.dynamic_difficulty.data;

import com.mojang.logging.LogUtils;
import dev.muon.dynamic_difficulty.settings.BiomeBonusSettings;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
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
  private static final Map<Identifier, BiomeBonusSettings> INDIVIDUAL_SETTINGS = new HashMap<>();
  private static final Map<TagKey<Biome>, BiomeBonusSettings> TAG_SETTINGS = new HashMap<>();

  @Nullable
  public static BiomeBonusSettings get(Identifier biomeId, Registry<Biome> biomeRegistry) {
    BiomeBonusSettings individual = INDIVIDUAL_SETTINGS.get(biomeId);
    if (individual != null) {
      return individual;
    }

    if (TAG_SETTINGS.isEmpty()) {
      return null;
    }

    Optional<Holder.Reference<Biome>> optHolder = biomeRegistry.get(biomeId);
    if (optHolder.isEmpty()) {
      return null;
    }

    Holder<Biome> biomeHolder = optHolder.get();
    BiomeBonusSettings best = null;
    int highestBonus = 0;
    for (Map.Entry<TagKey<Biome>, BiomeBonusSettings> tagEntry : TAG_SETTINGS.entrySet()) {
      if (biomeHolder.is(tagEntry.getKey())) {
        BiomeBonusSettings tagSettings = tagEntry.getValue();
        if (tagSettings.levelBonus() > highestBonus) {
          highestBonus = tagSettings.levelBonus();
          best = tagSettings;
        }
      }
    }
    return best;
  }

  /**
   * Load individual biome settings. Called by platform-specific reloaders.
   */
  public static void loadSettings(Map<Identifier, BiomeBonusSettings> settings) {
    INDIVIDUAL_SETTINGS.clear();
    INDIVIDUAL_SETTINGS.putAll(settings);
    LOGGER.info("Loaded {} individual biome leveling settings from 'leveling_settings/biomes'", INDIVIDUAL_SETTINGS.size());
  }

  /**
   * Load tag-based biome settings. Called by platform-specific reloaders.
   */
  public static void loadTagSettings(Map<Identifier, BiomeBonusSettings> tagSettings) {
    TAG_SETTINGS.clear();
    tagSettings.forEach((id, settings) -> TAG_SETTINGS.put(TagKey.create(Registries.BIOME, id), settings));
    LOGGER.info("Loaded {} biome tag leveling settings from 'leveling_settings/biome_tags'", TAG_SETTINGS.size());
  }
}
