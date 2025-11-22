package dev.muon.dynamic_difficulty.client.render;

import dev.muon.dynamic_difficulty.config.Config;

public class StructureTitleRenderer<T> extends TitleRenderer<T> {

    public StructureTitleRenderer(int maxRecentListSize) {
        super(
            maxRecentListSize,
            () -> Config.CLIENT.showStructureTitles.get(),
            () -> Config.CLIENT.structureTitleFadeInTime.get(),
            () -> Config.CLIENT.structureTitleDisplayTime.get(),
            () -> Config.CLIENT.structureTitleFadeOutTime.get(),
            () -> Config.CLIENT.structureTitleTextColor.get(),
            () -> Config.CLIENT.structureTitleRenderShadow.get(),
            () -> Config.CLIENT.structureTitleTextSize.get(),
            () -> Config.CLIENT.structureTitleAnchor.get(),
            () -> Config.CLIENT.structureTitleXOffset.get(),
            () -> Config.CLIENT.structureTitleYOffset.get()
        );
    }
}
