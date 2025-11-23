package dev.muon.dynamic_difficulty.client.render;

import dev.muon.dynamic_difficulty.config.Config;

public class StructureTitleRenderer<T> extends TitleRenderer<T> {

    public StructureTitleRenderer(int maxRecentListSize) {
        super(
            maxRecentListSize,
                Config.CLIENT.showStructureTitles,
                Config.CLIENT.structureTitleFadeInTime,
                Config.CLIENT.structureTitleDisplayTime,
                Config.CLIENT.structureTitleFadeOutTime,
                Config.CLIENT.structureTitleTextColor,
                Config.CLIENT.structureTitleRenderShadow,
                Config.CLIENT.structureTitleTextSize,
                Config.CLIENT.structureTitleAnchor,
                Config.CLIENT.structureTitleXOffset,
                Config.CLIENT.structureTitleYOffset
        );
    }
}
