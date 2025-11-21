package dev.muon.dynamic_difficulty.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import dev.muon.dynamic_difficulty.DynamicDifficulty;
import dev.muon.dynamic_difficulty.settings.StructureBonusSettings;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

public class StructureTagLevelingSettingsReloader extends SimpleJsonResourceReloadListener implements IdentifiableResourceReloadListener {
  private static final Logger LOGGER = LogUtils.getLogger();
  private static final Gson GSON = new Gson();
  private static final ResourceLocation RELOADER_ID = ResourceLocation.fromNamespaceAndPath(DynamicDifficulty.MODID, "structure_tag_leveling_settings");

  public StructureTagLevelingSettingsReloader() {
    super(GSON, "leveling_settings/structure_tags");
  }

  @Override
  public ResourceLocation getFabricId() {
    return RELOADER_ID;
  }

  public static ResourceLocation getReloaderId() {
    return RELOADER_ID;
  }

  @Override
  protected void apply(
      Map<ResourceLocation, JsonElement> prepared,
      @NotNull ResourceManager resourceManager,
      @NotNull ProfilerFiller profilerFiller) {
    LOGGER.info("Loading structure tag leveling settings from 'leveling_settings/structure_tags'");
    Map<ResourceLocation, StructureBonusSettings> tagSettings = new HashMap<>();
    var ops = com.mojang.serialization.JsonOps.INSTANCE;
    for (Map.Entry<ResourceLocation, JsonElement> entry : prepared.entrySet()) {
      StructureBonusSettings.CODEC.decode(ops, entry.getValue())
          .result()
          .ifPresentOrElse(
              pair -> tagSettings.put(entry.getKey(), pair.getFirst()),
              () -> LOGGER.error("Couldn't parse data file {}", entry.getKey())
          );
    }
    StructureLevelingSettingsReloader.loadTagSettings(tagSettings);
  }
}
