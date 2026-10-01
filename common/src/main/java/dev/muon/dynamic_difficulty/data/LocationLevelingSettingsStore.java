package dev.muon.dynamic_difficulty.data;

import com.mojang.logging.LogUtils;
import dev.muon.dynamic_difficulty.settings.LocationLevelingSettings;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class LocationLevelingSettingsStore<T> {
  private static final Logger LOGGER = LogUtils.getLogger();

  public static final LocationLevelingSettingsStore<Structure> STRUCTURES =
          new LocationLevelingSettingsStore<>(Registries.STRUCTURE, "structures", "structure_tags");

  public static final LocationLevelingSettingsStore<Biome> BIOMES =
          new LocationLevelingSettingsStore<>(Registries.BIOME, "biomes", "biome_tags");

  private final ResourceKey<? extends Registry<T>> registryKey;
  private final String individualLogLabel;
  private final String tagLogLabel;
  private final Map<ResourceLocation, LocationLevelingSettings.RawSettings> individual = new HashMap<>();
  private final Map<TagKey<T>, LocationLevelingSettings.RawSettings> tags = new HashMap<>();

  private LocationLevelingSettingsStore(ResourceKey<? extends Registry<T>> registryKey,
                                        String individualLogLabel, String tagLogLabel) {
    this.registryKey = registryKey;
    this.individualLogLabel = individualLogLabel;
    this.tagLogLabel = tagLogLabel;
  }

  /** The individual entry, then every matching tag entry, unmerged. */
  public List<LocationLevelingSettings.RawSettings> getMatching(ResourceLocation id, Registry<T> registry) {
    List<LocationLevelingSettings.RawSettings> result = new ArrayList<>();
    LocationLevelingSettings.RawSettings ind = individual.get(id);
    if (ind != null) result.add(ind);

    if (tags.isEmpty()) return result;

    Optional<Holder.Reference<T>> optHolder = registry.getHolder(id);
    if (optHolder.isEmpty()) return result;

    Holder<T> holder = optHolder.get();
    for (Map.Entry<TagKey<T>, LocationLevelingSettings.RawSettings> tagEntry : tags.entrySet()) {
      if (holder.is(tagEntry.getKey())) {
        result.add(tagEntry.getValue());
      }
    }
    return result;
  }

  public void loadSettings(Map<ResourceLocation, LocationLevelingSettings.RawSettings> settings) {
    individual.clear();
    individual.putAll(settings);
    LOGGER.info("Loaded {} individual leveling settings from 'leveling_settings/{}'", individual.size(), individualLogLabel);
  }

  public void loadTagSettings(Map<ResourceLocation, LocationLevelingSettings.RawSettings> tagSettings) {
    tags.clear();
    tagSettings.forEach((id, settings) -> tags.put(TagKey.create(registryKey, id), settings));
    LOGGER.info("Loaded {} tag leveling settings from 'leveling_settings/{}'", tags.size(), tagLogLabel);
  }
}
