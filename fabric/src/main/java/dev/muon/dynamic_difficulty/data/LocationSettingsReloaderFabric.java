package dev.muon.dynamic_difficulty.data;

import com.mojang.serialization.Codec;
import dev.muon.dynamic_difficulty.settings.LocationLevelingSettings;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Shared base for the four fabric reload listeners that load
 * {@link LocationLevelingSettings.RawSettings} from a directory under {@code data/<ns>/leveling_settings/}.
 * Concrete subclasses supply the codec, directory path, reloader id, and the apply target.
 */
abstract class LocationSettingsReloaderFabric
        extends SimpleJsonResourceReloadListener<LocationLevelingSettings.RawSettings>
        implements IdentifiableResourceReloadListener {

    private final Identifier reloaderId;
    private final Consumer<Map<Identifier, LocationLevelingSettings.RawSettings>> applier;

    protected LocationSettingsReloaderFabric(
            Codec<LocationLevelingSettings.RawSettings> codec,
            String path,
            Identifier reloaderId,
            Consumer<Map<Identifier, LocationLevelingSettings.RawSettings>> applier) {
        super(codec, FileToIdConverter.json(path));
        this.reloaderId = reloaderId;
        this.applier = applier;
    }

    @Override
    public Identifier getFabricId() {
        return reloaderId;
    }

    @Override
    protected void apply(
            Map<Identifier, LocationLevelingSettings.RawSettings> prepared,
            @NotNull ResourceManager resourceManager,
            @NotNull ProfilerFiller profiler) {
        applier.accept(prepared);
    }
}
