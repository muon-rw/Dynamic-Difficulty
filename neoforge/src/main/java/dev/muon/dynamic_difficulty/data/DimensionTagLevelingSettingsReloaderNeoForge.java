package dev.muon.dynamic_difficulty.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import dev.muon.dynamic_difficulty.settings.DimensionLevelingSettings;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.neoforge.resource.ContextAwareReloadListener;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class DimensionTagLevelingSettingsReloaderNeoForge extends ContextAwareReloadListener {
    private static final Gson GSON = new Gson();
    private static final String DIRECTORY = "leveling_settings/dimension_tags";

    @Override
    public CompletableFuture<Void> reload(
            PreparableReloadListener.SharedState sharedState,
            Executor executor,
            PreparableReloadListener.PreparationBarrier barrier,
            Executor applyExecutor) {
        ResourceManager resourceManager = sharedState.resourceManager();
        
        return CompletableFuture.supplyAsync(() -> {
            Map<ResourceLocation, JsonElement> prepared = new HashMap<>();
            for (var entry : resourceManager.listResources(DIRECTORY, location -> location.getPath().endsWith(".json")).entrySet()) {
                ResourceLocation resourceLocation = entry.getKey();
                try (var reader = entry.getValue().openAsReader()) {
                    JsonElement jsonElement = GSON.fromJson(reader, JsonElement.class);
                    prepared.put(resourceLocation, jsonElement);
                } catch (Exception e) {
                    throw new JsonParseException("Failed to parse " + resourceLocation, e);
                }
            }
            return prepared;
        }, executor).thenCompose(barrier::wait).thenAcceptAsync(prepared -> {
            apply(prepared);
        }, applyExecutor);
    }

    @Override
    public String getName() {
        return "Dimension Tag Leveling Settings";
    }

    protected void apply(Map<ResourceLocation, JsonElement> prepared) {
        Map<ResourceLocation, DimensionLevelingSettings> settings = new HashMap<>();
        var ops = makeConditionalOps();
        for (Map.Entry<ResourceLocation, JsonElement> entry : prepared.entrySet()) {
            DimensionLevelingSettings.CODEC.decode(ops, entry.getValue())
                    .result()
                    .ifPresent(pair -> settings.put(entry.getKey(), pair.getFirst()));
        }
        DimensionsLevelingSettingsReloader.loadTagSettings(settings);
    }
}
