package dev.muon.dynamic_difficulty.client.render;

import dev.muon.dynamic_difficulty.config.Config;
import net.minecraft.world.level.dimension.DimensionType;

public class DimensionTitleRenderer extends TitleRenderer<DimensionType> {

    public DimensionTitleRenderer() {
        super(
                1, // Only track 1 recent dimension
                Config.CLIENT.showDimensionTitles,
                Config.CLIENT.dimensionTitleFadeInTime,
                Config.CLIENT.dimensionTitleDisplayTime,
                Config.CLIENT.dimensionTitleFadeOutTime,
                Config.CLIENT.dimensionTitleTextColor,
                Config.CLIENT.dimensionTitleRenderShadow,
                Config.CLIENT.dimensionTitleTextSize,
                Config.CLIENT.dimensionTitleAnchor,
                Config.CLIENT.dimensionTitleXOffset,
                Config.CLIENT.dimensionTitleYOffset
        );
    }
}

