package dev.muon.dynamic_difficulty.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import dev.muon.dynamic_difficulty.DynamicDifficulty;
import dev.muon.dynamic_difficulty.settings.BiomeBonusSettings;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * Fabric-specific reload listener for biome leveling settings.
 */
public class BiomeLevelingSettingsReloaderFabric extends SimpleJsonResourceReloadListener implements IdentifiableResourceReloadListener {
    private static final Gson GSON = new Gson();
    private static final ResourceLocation RELOADER_ID = DynamicDifficulty.loc("biome_leveling_settings");

    public BiomeLevelingSettingsReloaderFabric() {
        super(GSON, "leveling_settings/biomes");
    }

    @Override
    public ResourceLocation getFabricId() {
        return RELOADER_ID;
    }

    @Override
    protected void apply(
            Map<ResourceLocation, JsonElement> prepared,
            @NotNull ResourceManager resourceManager,
            @NotNull ProfilerFiller profiler) {
        Map<ResourceLocation, BiomeBonusSettings> settings = new HashMap<>();
        var ops = com.mojang.serialization.JsonOps.INSTANCE;
        
        for (Map.Entry<ResourceLocation, JsonElement> entry : prepared.entrySet()) {
            BiomeBonusSettings.CODEC.decode(ops, entry.getValue())
                    .result()
                    .ifPresent(pair -> settings.put(entry.getKey(), pair.getFirst()));
        }
        
        BiomeLevelingSettingsReloader.loadSettings(settings);
    }
}
