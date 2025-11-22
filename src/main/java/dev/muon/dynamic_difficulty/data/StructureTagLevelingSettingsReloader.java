package dev.muon.dynamic_difficulty.data;

import com.mojang.logging.LogUtils;
import dev.muon.dynamic_difficulty.DynamicDifficulty;
import dev.muon.dynamic_difficulty.settings.StructureBonusSettings;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.Map;

public class StructureTagLevelingSettingsReloader extends SimpleJsonResourceReloadListener<StructureBonusSettings> {
  private static final Logger LOGGER = LogUtils.getLogger();
  private static final ResourceLocation RELOADER_ID = ResourceLocation.fromNamespaceAndPath(DynamicDifficulty.MODID, "structure_tag_leveling_settings");

  public StructureTagLevelingSettingsReloader() {
    super(StructureBonusSettings.CODEC, net.minecraft.resources.FileToIdConverter.json("leveling_settings/structure_tags"));
  }

  public static ResourceLocation getReloaderId() {
    return RELOADER_ID;
  }

  @Override
  protected void apply(
      Map<ResourceLocation, StructureBonusSettings> prepared,
      @NotNull ResourceManager resourceManager,
      @NotNull ProfilerFiller profilerFiller) {
    LOGGER.info("Loading structure tag leveling settings from 'leveling_settings/structure_tags'");
    StructureLevelingSettingsReloader.loadTagSettings(prepared);
  }
}

