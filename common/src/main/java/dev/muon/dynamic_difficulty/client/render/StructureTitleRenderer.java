package dev.muon.dynamic_difficulty.client.render;

import dev.muon.dynamic_difficulty.config.Configs;

public class StructureTitleRenderer<T> extends TitleRenderer<T> {

    public StructureTitleRenderer(int maxRecentListSize) {
        super(
                maxRecentListSize,
                Configs.CLIENT.showStructureTitles,
                Configs.CLIENT.structureTitleFadeInTime,
                Configs.CLIENT.structureTitleDisplayTime,
                Configs.CLIENT.structureTitleFadeOutTime,
                Configs.CLIENT.structureTitleTextColor,
                Configs.CLIENT.structureTitleRenderShadow,
                Configs.CLIENT.structureTitleTextSize,
                Configs.CLIENT.structureTitleAnchor,
                Configs.CLIENT.structureTitleXOffset,
                Configs.CLIENT.structureTitleYOffset
        );
    }
}

