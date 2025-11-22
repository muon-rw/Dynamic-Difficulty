package dev.muon.dynamic_difficulty.client.render;

import dev.muon.dynamic_difficulty.config.Config;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.biome.Biome;

import java.util.function.Supplier;

public class BiomeTitleRenderer extends TitleRenderer<Biome> {
    public int cooldownTimer = 0;
    
    // Dynamic cache size supplier - reads from config each time
    private final Supplier<Integer> maxRecentListSizeSupplier;
    
    public BiomeTitleRenderer() {
        super(
            Config.CLIENT.biomeRecentCacheSize.get(), // Initial value, but we'll use supplier for dynamic updates
            () -> Config.CLIENT.showBiomeTitles.get(),
            () -> Config.CLIENT.biomeTitleFadeInTime.get(),
            () -> Config.CLIENT.biomeTitleDisplayTime.get(),
            () -> Config.CLIENT.biomeTitleFadeOutTime.get(),
            () -> Config.CLIENT.biomeTitleTextColor.get(),
            () -> Config.CLIENT.biomeTitleRenderShadow.get(),
            () -> Config.CLIENT.biomeTitleTextSize.get(),
            () -> Config.CLIENT.biomeTitleAnchor.get(),
            () -> Config.CLIENT.biomeTitleXOffset.get(),
            () -> Config.CLIENT.biomeTitleYOffset.get()
        );
        this.maxRecentListSizeSupplier = () -> Config.CLIENT.biomeRecentCacheSize.get();
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
        cooldownTimer = Config.CLIENT.biomeTitleCooldownTime.get();
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

