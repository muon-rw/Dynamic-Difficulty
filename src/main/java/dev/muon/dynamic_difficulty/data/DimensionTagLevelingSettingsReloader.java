package dev.muon.dynamic_difficulty.data;

import com.mojang.logging.LogUtils;
import dev.muon.dynamic_difficulty.DynamicDifficulty;
import dev.muon.dynamic_difficulty.settings.DimensionLevelingSettings;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.Map;

public class DimensionTagLevelingSettingsReloader extends SimpleJsonResourceReloadListener<DimensionLevelingSettings> {
  private static final Logger LOGGER = LogUtils.getLogger();
  private static final ResourceLocation RELOADER_ID = ResourceLocation.fromNamespaceAndPath(DynamicDifficulty.MODID, "dimension_tag_leveling_settings");

  public DimensionTagLevelingSettingsReloader() {
    super(DimensionLevelingSettings.CODEC, net.minecraft.resources.FileToIdConverter.json("leveling_settings/dimension_tags"));
  }

  public static ResourceLocation getReloaderId() {
    return RELOADER_ID;
  }

  @Override
  protected void apply(
      Map<ResourceLocation, DimensionLevelingSettings> prepared,
      @NotNull ResourceManager resourceManager,
      @NotNull ProfilerFiller profilerFiller) {
    LOGGER.info("Loading dimension tag leveling settings from 'leveling_settings/dimension_tags'");
    DimensionsLevelingSettingsReloader.loadTagSettings(prepared);
  }
}

