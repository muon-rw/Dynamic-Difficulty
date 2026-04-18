package dev.muon.dynamic_difficulty.client.render;

import dev.muon.dynamic_difficulty.config.Configs;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.biome.Biome;

import java.util.function.Supplier;

public class BiomeTitleRenderer extends TitleRenderer<Biome> {
    public int cooldownTimer = 0;

    // Dynamic cache size supplier - reads from config each time
    private final Supplier<Integer> maxRecentListSizeSupplier;

    public BiomeTitleRenderer() {
        super(
                Configs.CLIENT.biomeRecentCacheSize.get(), // Initial value, but we'll use supplier for dynamic updates
                Configs.CLIENT.showBiomeTitles,
                Configs.CLIENT.biomeTitleFadeInTime,
                Configs.CLIENT.biomeTitleDisplayTime,
                Configs.CLIENT.biomeTitleFadeOutTime,
                Configs.CLIENT.biomeTitleTextColor,
                Configs.CLIENT.biomeTitleRenderShadow,
                Configs.CLIENT.biomeTitleTextSize,
                Configs.CLIENT.biomeTitleAnchor,
                Configs.CLIENT.biomeTitleXOffset,
                Configs.CLIENT.biomeTitleYOffset
        );
        this.maxRecentListSizeSupplier = Configs.CLIENT.biomeRecentCacheSize;
    }

    @Override
    public void tick() {
        if (cooldownTimer > 0) {
            --cooldownTimer;
        }
        super.tick();

        // Dynamically adjust cache size if config changed
        int currentMaxSize = maxRecentListSizeSupplier.get();
        while (recentEntries.size() > currentMaxSize && !recentEntries.isEmpty()) {
            recentEntries.removeFirst();
        }
    }

    @Override
    public void displayTitle(Component titleText) {
        super.displayTitle(titleText);
        cooldownTimer = Configs.CLIENT.biomeTitleCooldownTime.get();
    }

    @Override
    public void addRecentEntry(Biome entry) {
        // Use dynamic cache size
        int maxSize = maxRecentListSizeSupplier.get();
        if (recentEntries.size() >= maxSize && !recentEntries.isEmpty()) {
            recentEntries.removeFirst();
        }
        if (maxSize > 0) {
            recentEntries.addLast(entry);
        }
    }
}

