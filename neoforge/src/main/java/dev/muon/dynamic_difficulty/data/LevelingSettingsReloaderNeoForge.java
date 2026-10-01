package dev.muon.dynamic_difficulty.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class LevelingSettingsReloaderNeoForge extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new Gson();

    private final LevelingSettingsSource<?> source;

    public LevelingSettingsReloaderNeoForge(LevelingSettingsSource<?> source) {
        super(GSON, source.directory());
        this.source = source;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> prepared, @NotNull ResourceManager resourceManager, @NotNull ProfilerFiller profiler) {
        source.load(prepared, makeConditionalOps());
    }

    @Override
    public String getName() {
        return source.id().toString();
    }
}
