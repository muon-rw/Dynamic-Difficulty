package dev.muon.dynamic_difficulty.data;

import dev.muon.dynamic_difficulty.DynamicDifficulty;
import dev.muon.dynamic_difficulty.settings.EntityLevelingSettings;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class EntityTagLevelingSettingsReloaderFabric extends SimpleJsonResourceReloadListener<EntityLevelingSettings.RawSettings> implements IdentifiableResourceReloadListener {
    private static final Identifier RELOADER_ID = DynamicDifficulty.id("entity_tag_leveling_settings");
    private static final FileToIdConverter FILE_TO_ID = FileToIdConverter.json("leveling_settings/entity_tags");

    public EntityTagLevelingSettingsReloaderFabric() {
        super(EntityLevelingSettings.RAW_CODEC, FILE_TO_ID);
    }

    @Override
    public Identifier getFabricId() {
        return RELOADER_ID;
    }

    @Override
    protected void apply(
            Map<Identifier, EntityLevelingSettings.RawSettings> prepared,
            @NotNull ResourceManager resourceManager,
            @NotNull ProfilerFiller profiler) {
        EntityLevelingSettingsReloader.loadTagSettings(prepared);
    }
}
