package dev.muon.dynamic_difficulty.client.render;

import dev.muon.dynamic_difficulty.config.Configs;
import net.minecraft.world.level.dimension.DimensionType;

public class DimensionTitleRenderer extends TitleRenderer<DimensionType> {

    public DimensionTitleRenderer() {
        super(
                1, // Only track 1 recent dimension
                Configs.CLIENT.showDimensionTitles,
                Configs.CLIENT.dimensionTitleFadeInTime,
                Configs.CLIENT.dimensionTitleDisplayTime,
                Configs.CLIENT.dimensionTitleFadeOutTime,
                Configs.CLIENT.dimensionTitleTextColor,
                Configs.CLIENT.dimensionTitleRenderShadow,
                Configs.CLIENT.dimensionTitleTextSize,
                Configs.CLIENT.dimensionTitleAnchor,
                Configs.CLIENT.dimensionTitleXOffset,
                Configs.CLIENT.dimensionTitleYOffset
        );
    }
}

