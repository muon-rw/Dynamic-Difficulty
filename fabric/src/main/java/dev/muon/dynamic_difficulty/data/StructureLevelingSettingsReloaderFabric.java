package dev.muon.dynamic_difficulty.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import dev.muon.dynamic_difficulty.DynamicDifficulty;
import dev.muon.dynamic_difficulty.settings.StructureBonusSettings;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class StructureLevelingSettingsReloaderFabric extends SimpleJsonResourceReloadListener implements IdentifiableResourceReloadListener {
    private static final Gson GSON = new Gson();
    private static final ResourceLocation RELOADER_ID = DynamicDifficulty.loc("structure_leveling_settings");

    public StructureLevelingSettingsReloaderFabric() {
        super(GSON, "leveling_settings/structures");
    }

    @Override
    public ResourceLocation getFabricId() {
        return RELOADER_ID;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> prepared, @NotNull ResourceManager resourceManager, @NotNull ProfilerFiller profiler) {
        Map<ResourceLocation, StructureBonusSettings> settings = new HashMap<>();
        var ops = com.mojang.serialization.JsonOps.INSTANCE;
        for (Map.Entry<ResourceLocation, JsonElement> entry : prepared.entrySet()) {
            StructureBonusSettings.CODEC.decode(ops, entry.getValue())
                    .result()
                    .ifPresent(pair -> settings.put(entry.getKey(), pair.getFirst()));
        }
        StructureLevelingSettingsReloader.loadSettings(settings);
    }
}
