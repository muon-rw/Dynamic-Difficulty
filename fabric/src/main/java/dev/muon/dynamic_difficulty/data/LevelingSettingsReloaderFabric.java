package dev.muon.dynamic_difficulty.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class LevelingSettingsReloaderFabric extends SimpleJsonResourceReloadListener implements IdentifiableResourceReloadListener {
    private static final Gson GSON = new Gson();

    private final LevelingSettingsSource<?> source;

    public LevelingSettingsReloaderFabric(LevelingSettingsSource<?> source) {
        super(GSON, source.directory());
        this.source = source;
    }

    @Override
    public ResourceLocation getFabricId() {
        return source.id();
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> prepared, @NotNull ResourceManager resourceManager, @NotNull ProfilerFiller profiler) {
        source.load(prepared, JsonOps.INSTANCE);
    }
}
