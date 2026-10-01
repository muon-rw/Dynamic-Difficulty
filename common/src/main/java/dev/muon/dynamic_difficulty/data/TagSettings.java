package dev.muon.dynamic_difficulty.data;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

record TagSettings<T, S>(TagKey<T> tag, S settings) {

  /** Ascending priority, then tag id; later entries override the fields they set. */
  static <T, S> List<TagSettings<T, S>> inApplyOrder(ResourceKey<? extends Registry<T>> registry,
                                                     Map<ResourceLocation, S> settingsById,
                                                     ToIntFunction<S> priority) {
    return settingsById.entrySet().stream()
        .sorted(Comparator.<Map.Entry<ResourceLocation, S>>comparingInt(entry -> priority.applyAsInt(entry.getValue()))
            .thenComparing(Map.Entry::getKey))
        .map(entry -> new TagSettings<>(TagKey.create(registry, entry.getKey()), entry.getValue()))
        .toList();
  }
}
