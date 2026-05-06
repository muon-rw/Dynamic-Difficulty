package dev.muon.dynamic_difficulty.data;

import com.mojang.logging.LogUtils;
import dev.muon.dynamic_difficulty.settings.LocationLevelingSettings;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Generic storage and lookup for location-scoped (structure or biome) leveling settings.
 *
 * <p>Stores individual settings keyed by {@link Identifier} and tag-keyed settings keyed by
 * {@link TagKey}. Lookup returns settings already merged across the individual entry plus all
 * matching tags via per-field max. Callers who need the underlying entries (for the bonus path's
 * bucket-max model) should use {@link #getMatching}.
 */
public final class LocationLevelingSettingsStore<T> {
  private static final Logger LOGGER = LogUtils.getLogger();

  /** Store for structure-scoped settings (data/[ns]/leveling_settings/structures + structure_tags). */
  public static final LocationLevelingSettingsStore<Structure> STRUCTURES =
          new LocationLevelingSettingsStore<>(Registries.STRUCTURE, "structures", "structure_tags");

  /** Store for biome-scoped settings (data/[ns]/leveling_settings/biomes + biome_tags). */
  public static final LocationLevelingSettingsStore<Biome> BIOMES =
          new LocationLevelingSettingsStore<>(Registries.BIOME, "biomes", "biome_tags");

  private final ResourceKey<? extends Registry<T>> registryKey;
  private final String individualLogLabel;
  private final String tagLogLabel;
  private final Map<Identifier, LocationLevelingSettings.RawSettings> individual = new HashMap<>();
  private final Map<TagKey<T>, LocationLevelingSettings.RawSettings> tags = new HashMap<>();

  private LocationLevelingSettingsStore(ResourceKey<? extends Registry<T>> registryKey,
                                        String individualLogLabel, String tagLogLabel) {
    this.registryKey = registryKey;
    this.individualLogLabel = individualLogLabel;
    this.tagLogLabel = tagLogLabel;
  }

  /**
   * Returns settings for the given id, merging any individual entry with matching tag entries
   * via per-field max. Returns {@code null} if nothing matches.
   */
  @Nullable
  public LocationLevelingSettings.RawSettings get(Identifier id, Registry<T> registry) {
    List<LocationLevelingSettings.RawSettings> matching = getMatching(id, registry);
    if (matching.isEmpty()) return null;
    LocationLevelingSettings.RawSettings merged = matching.get(0);
    for (int i = 1; i < matching.size(); i++) {
      merged = merged.merge(matching.get(i));
    }
    return merged;
  }

  /**
   * Returns every settings entry that applies to this id (individual + each matching tag),
   * unmerged. Used by the bonus path to apply the bucket-max model over each entry independently.
   */
  public List<LocationLevelingSettings.RawSettings> getMatching(Identifier id, Registry<T> registry) {
    List<LocationLevelingSettings.RawSettings> result = new ArrayList<>();
    LocationLevelingSettings.RawSettings ind = individual.get(id);
    if (ind != null) result.add(ind);

    if (tags.isEmpty()) return result;

    Optional<Holder.Reference<T>> optHolder = registry.get(id);
    if (optHolder.isEmpty()) return result;

    Holder<T> holder = optHolder.get();
    for (Map.Entry<TagKey<T>, LocationLevelingSettings.RawSettings> tagEntry : tags.entrySet()) {
      if (holder.is(tagEntry.getKey())) {
        result.add(tagEntry.getValue());
      }
    }
    return result;
  }

  /**
   * Load individual settings. Called by platform-specific reloaders.
   */
  public void loadSettings(Map<Identifier, LocationLevelingSettings.RawSettings> settings) {
    individual.clear();
    individual.putAll(settings);
    LOGGER.info("Loaded {} individual leveling settings from 'leveling_settings/{}'", individual.size(), individualLogLabel);
  }

  /**
   * Load tag-based settings. Called by platform-specific reloaders.
   */
  public void loadTagSettings(Map<Identifier, LocationLevelingSettings.RawSettings> tagSettings) {
    tags.clear();
    tagSettings.forEach((id, settings) -> tags.put(TagKey.create(registryKey, id), settings));
    LOGGER.info("Loaded {} tag leveling settings from 'leveling_settings/{}'", tags.size(), tagLogLabel);
  }
}
