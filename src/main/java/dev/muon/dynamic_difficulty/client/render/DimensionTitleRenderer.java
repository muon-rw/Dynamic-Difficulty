package dev.muon.dynamic_difficulty.client.render;

import dev.muon.dynamic_difficulty.config.Config;
import net.minecraft.world.level.dimension.DimensionType;

public class DimensionTitleRenderer extends TitleRenderer<DimensionType> {
    
    public DimensionTitleRenderer() {
        super(
            1, // Only track 1 recent dimension
            () -> Config.CLIENT.showDimensionTitles.get(),
            () -> Config.CLIENT.dimensionTitleFadeInTime.get(),
            () -> Config.CLIENT.dimensionTitleDisplayTime.get(),
            () -> Config.CLIENT.dimensionTitleFadeOutTime.get(),
            () -> Config.CLIENT.dimensionTitleTextColor.get(),
            () -> Config.CLIENT.dimensionTitleRenderShadow.get(),
            () -> Config.CLIENT.dimensionTitleTextSize.get(),
            () -> Config.CLIENT.dimensionTitleAnchor.get(),
            () -> Config.CLIENT.dimensionTitleXOffset.get(),
            () -> Config.CLIENT.dimensionTitleYOffset.get()
        );
    }
}

