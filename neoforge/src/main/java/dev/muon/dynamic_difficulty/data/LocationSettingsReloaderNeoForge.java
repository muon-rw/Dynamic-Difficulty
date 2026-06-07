package dev.muon.dynamic_difficulty.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.mojang.serialization.Codec;
import dev.muon.dynamic_difficulty.settings.LocationLevelingSettings;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.neoforge.resource.ContextAwareReloadListener;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

abstract class LocationSettingsReloaderNeoForge extends ContextAwareReloadListener {
    private static final Gson GSON = new Gson();

    private final Codec<LocationLevelingSettings.RawSettings> codec;
    private final String directory;
    private final String name;
    private final Consumer<Map<Identifier, LocationLevelingSettings.RawSettings>> applier;

    protected LocationSettingsReloaderNeoForge(
            Codec<LocationLevelingSettings.RawSettings> codec,
            String directory,
            String name,
            Consumer<Map<Identifier, LocationLevelingSettings.RawSettings>> applier) {
        this.codec = codec;
        this.directory = directory;
        this.name = name;
        this.applier = applier;
    }

    @Override
    public CompletableFuture<Void> reload(
            PreparableReloadListener.SharedState sharedState,
            Executor executor,
            PreparableReloadListener.PreparationBarrier barrier,
            Executor applyExecutor) {
        ResourceManager resourceManager = sharedState.resourceManager();

        return CompletableFuture.supplyAsync(() -> {
            Map<Identifier, JsonElement> prepared = new HashMap<>();
            for (var entry : resourceManager.listResources(directory, location -> location.getPath().endsWith(".json")).entrySet()) {
                Identifier resourceLocation = entry.getKey();
                try (var reader = entry.getValue().openAsReader()) {
                    JsonElement jsonElement = GSON.fromJson(reader, JsonElement.class);
                    prepared.put(resourceLocation, jsonElement);
                } catch (Exception e) {
                    throw new JsonParseException("Failed to parse " + resourceLocation, e);
                }
            }
            return prepared;
        }, executor).thenCompose(barrier::wait).thenAcceptAsync(this::apply, applyExecutor);
    }

    @Override
    public String getName() {
        return name;
    }

    private void apply(Map<Identifier, JsonElement> prepared) {
        Map<Identifier, LocationLevelingSettings.RawSettings> settings = new HashMap<>();
        var ops = makeConditionalOps();
        for (Map.Entry<Identifier, JsonElement> entry : prepared.entrySet()) {
            codec.decode(ops, entry.getValue())
                    .result()
                    .ifPresent(pair -> settings.put(entry.getKey(), pair.getFirst()));
        }
        applier.accept(settings);
    }
}
