package dev.muon.dynamic_difficulty.data;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import dev.muon.dynamic_difficulty.DynamicDifficulty;
import dev.muon.dynamic_difficulty.settings.DimensionLevelingSettings;
import dev.muon.dynamic_difficulty.settings.EntityLevelingSettings;
import dev.muon.dynamic_difficulty.settings.LocationLevelingSettings;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public record LevelingSettingsSource<T>(
        ResourceLocation id,
        String directory,
        Codec<T> codec,
        Consumer<Map<ResourceLocation, T>> store) {

    public static final List<LevelingSettingsSource<?>> ALL = List.of(
            of("dimension_leveling_settings", "dimensions",
                    DimensionLevelingSettings.RAW_CODEC, DimensionLevelingSettingsStore::loadSettings),
            of("dimension_tag_leveling_settings", "dimension_tags",
                    DimensionLevelingSettings.RAW_CODEC, DimensionLevelingSettingsStore::loadTagSettings),
            of("entity_leveling_settings", "entities",
                    EntityLevelingSettings.RAW_CODEC, EntityLevelingSettingsStore::loadSettings),
            of("entity_tag_leveling_settings", "entity_tags",
                    EntityLevelingSettings.RAW_CODEC, EntityLevelingSettingsStore::loadTagSettings),
            of("biome_leveling_settings", "biomes",
                    LocationLevelingSettings.BIOME_RAW_CODEC, LocationLevelingSettingsStore.BIOMES::loadSettings),
            of("biome_tag_leveling_settings", "biome_tags",
                    LocationLevelingSettings.BIOME_RAW_CODEC, LocationLevelingSettingsStore.BIOMES::loadTagSettings),
            of("structure_leveling_settings", "structures",
                    LocationLevelingSettings.STRUCTURE_RAW_CODEC, LocationLevelingSettingsStore.STRUCTURES::loadSettings),
            of("structure_tag_leveling_settings", "structure_tags",
                    LocationLevelingSettings.STRUCTURE_RAW_CODEC, LocationLevelingSettingsStore.STRUCTURES::loadTagSettings));

    private static <T> LevelingSettingsSource<T> of(String id, String directory, Codec<T> codec,
                                                   Consumer<Map<ResourceLocation, T>> store) {
        return new LevelingSettingsSource<>(DynamicDifficulty.loc(id), "leveling_settings/" + directory, codec, store);
    }

    public void load(Map<ResourceLocation, JsonElement> jsonById, DynamicOps<JsonElement> ops) {
        Map<ResourceLocation, T> settingsById = new HashMap<>();
        jsonById.forEach((fileId, json) -> {
            DataResult<T> result = codec.parse(ops, json);
            result.error().ifPresent(error -> DynamicDifficulty.LOGGER.error(
                    "Skipping invalid leveling settings {} in '{}': {}", fileId, directory, error.message()));
            result.result().ifPresent(settings -> settingsById.put(fileId, settings));
        });
        store.accept(settingsById);
    }
}
