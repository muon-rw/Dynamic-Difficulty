package dev.muon.dynamic_difficulty.client.render;

import dev.muon.dynamic_difficulty.config.Config;
import dev.muon.dynamic_difficulty.data.DimensionsLevelingSettingsReloader;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.dimension.DimensionType;

public class TitleRenderManager {
    private static TitleRenderManager instance;

    public final StructureTitleRenderer<ResourceLocation> structureTitleRenderer;
    public final BiomeTitleRenderer biomeTitleRenderer;
    public final DimensionTitleRenderer dimensionTitleRenderer;
    public final LevelInfoRenderer levelInfoRenderer;

    private static final String TRAVELERS_TITLES_MOD_ID = "travelerstitles";

    private DimensionType currentDimension = null;

    // Track final displayed values to detect actual changes (avoids retriggering on location change with same level)
    private int lastDisplayedLevel = -1;
    private int lastPlayerBonus = -1;

    public TitleRenderManager() {
        this.structureTitleRenderer = new StructureTitleRenderer<>(1); // Only track 1 recent structure
        this.biomeTitleRenderer = new BiomeTitleRenderer();
        this.dimensionTitleRenderer = new DimensionTitleRenderer();
        this.levelInfoRenderer = new LevelInfoRenderer();
    }

    public static TitleRenderManager getInstance() {
        if (instance == null) {
            instance = new TitleRenderManager();
        }
        return instance;
    }

    public void clientTick() {
        if (!Minecraft.getInstance().isPaused()) {
            structureTitleRenderer.tick();
            biomeTitleRenderer.tick();
            dimensionTitleRenderer.tick();
            levelInfoRenderer.tick();
        }
    }

    public void renderTitles(GuiGraphics guiGraphics, float partialTicks) {
        if (!Minecraft.getInstance().getDebugOverlay().showDebugScreen()) {
            dimensionTitleRenderer.renderText(partialTicks, guiGraphics);
            biomeTitleRenderer.renderText(partialTicks, guiGraphics);
            structureTitleRenderer.renderText(partialTicks, guiGraphics);
            levelInfoRenderer.renderText(partialTicks, guiGraphics,
                    dimensionTitleRenderer, biomeTitleRenderer, structureTitleRenderer);
        }
    }

    /**
     * Called on player tick to detect biome/dimension changes
     */
    public void playerTick(Player player) {
        if (player instanceof LocalPlayer && player.level().isLoaded(player.blockPosition())) {
            BlockPos playerPos = player.blockPosition();
            Level world = player.level();

            DimensionType newDimension = world.dimensionType();
            if (currentDimension != newDimension) {
                playerChangedDimension(player);
                currentDimension = newDimension;
            }

            updateDimensionTitle(world, player);
            updateBiomeTitle(world, playerPos, player);
        }
    }

    /**
     * Called when player changes dimension.
     * Resets biome cache since biome titles are dimension-specific.
     */
    private void playerChangedDimension(Player player) {
        if (Config.CLIENT.showBiomeTitles.get()) {
            biomeTitleRenderer.clearTimer();
            biomeTitleRenderer.recentEntries.clear();
            biomeTitleRenderer.displayedTitle = null;
        }
    }

    /**
     * Display structure title when notified by server
     */
    public void displayStructureTitle(ResourceLocation structureId, int structureBonus, int baseLevel, int playerBonus, int displayedLevel) {
        // Update level info only if displayed values changed
        if (shouldUpdateLevelInfo(displayedLevel, playerBonus)) {
            levelInfoRenderer.displayLevelInfo(displayedLevel, playerBonus);
            lastDisplayedLevel = displayedLevel;
            lastPlayerBonus = playerBonus;
        }
        
        if (Config.CLIENT.showStructureTitles.get()) {
            boolean shouldDisplay = !Config.CLIENT.structureTitleOnlyAnnounceIfModified.get() || structureBonus > 0;
            if (shouldDisplay && !structureTitleRenderer.matchesAnyRecentEntry(id -> id.equals(structureId))) {
                Component structureName = getStructureName(structureId);
                structureTitleRenderer.displayTitle(structureName);
                structureTitleRenderer.addRecentEntry(structureId);
            }
        }
    }

    /**
     * Display biome title when notified by server
     */
    public void displayBiomeTitle(ResourceLocation biomeId, int biomeBonus, int baseLevel, int playerBonus, int displayedLevel) {
        // Update level info only if displayed values changed
        if (shouldUpdateLevelInfo(displayedLevel, playerBonus)) {
            levelInfoRenderer.displayLevelInfo(displayedLevel, playerBonus);
            lastDisplayedLevel = displayedLevel;
            lastPlayerBonus = playerBonus;
        }
        
        if (Config.CLIENT.showBiomeTitles.get()) {
            boolean shouldDisplay = !Config.CLIENT.biomeTitleOnlyAnnounceIfModified.get() || biomeBonus > 0;
            if (shouldDisplay) {
                Component biomeName = getBiomeName(biomeId);
                if (biomeName != null) {
                    // Check if we've recently shown this biome by comparing displayed name
                    if (biomeTitleRenderer.displayedTitle == null ||
                            !biomeName.getString().equals(biomeTitleRenderer.displayedTitle.getString())) {

                        biomeTitleRenderer.displayTitle(biomeName);

                        if (Minecraft.getInstance().level != null) {
                            BlockPos playerPos = Minecraft.getInstance().player != null ?
                                    Minecraft.getInstance().player.blockPosition() : BlockPos.ZERO;
                            Holder<Biome> biomeHolder = Minecraft.getInstance().level.getBiome(playerPos);
                            biomeTitleRenderer.addRecentEntry(biomeHolder.value());
                        }
                    }
                }
            }
        }
    }

    /**
     * Display dimension title when notified by server
     * Note: Dimensions affect base level through settings, not bonuses
     */
    public void displayDimensionTitle(ResourceLocation dimensionId, int baseLevel, int playerBonus, int displayedLevel) {
        // Update level info only if displayed values changed
        if (shouldUpdateLevelInfo(displayedLevel, playerBonus)) {
            levelInfoRenderer.displayLevelInfo(displayedLevel, playerBonus);
            lastDisplayedLevel = displayedLevel;
            lastPlayerBonus = playerBonus;
        }
        
        if (Config.CLIENT.showDimensionTitles.get()) {
            boolean shouldDisplay = true;
            if (Config.CLIENT.dimensionTitleOnlyAnnounceIfModified.get()) {
                Level world = Minecraft.getInstance().level;
                if (world != null) {
                    shouldDisplay = DimensionsLevelingSettingsReloader.hasCustomSettings(
                            world.dimension(), world.registryAccess().registryOrThrow(Registries.DIMENSION));
                }
            }
            
            if (shouldDisplay) {
                Component dimensionName = getDimensionName(dimensionId);
                if (dimensionName != null) {
                    DimensionType currDimension = Minecraft.getInstance().level != null ?
                            Minecraft.getInstance().level.dimensionType() : null;
                    if (currDimension != null &&
                            !dimensionTitleRenderer.matchesAnyRecentEntry(d -> d == currDimension)) {

                        dimensionTitleRenderer.displayTitle(dimensionName);
                        dimensionTitleRenderer.addRecentEntry(currDimension);
                    }
                }
            }
        }
    }

    /**
     * Updates the dimension title if conditions are met
     */
    private void updateDimensionTitle(Level world, Player player) {
        if (!Config.CLIENT.showDimensionTitles.get()) {
            return;
        }

        boolean shouldDisplay = true;
        if (Config.CLIENT.dimensionTitleOnlyAnnounceIfModified.get()) {
            shouldDisplay = DimensionsLevelingSettingsReloader.hasCustomSettings(
                    world.dimension(), world.registryAccess().registryOrThrow(Registries.DIMENSION));
        }

        if (shouldDisplay) {
            DimensionType currDimension = world.dimensionType();
            if (!dimensionTitleRenderer.matchesAnyRecentEntry(d -> d == currDimension)) {
                ResourceLocation dimensionBaseKey = world.dimension().location();
                Component dimensionTitle = getDimensionName(dimensionBaseKey);

                if (dimensionTitle != null) {
                    dimensionTitleRenderer.displayTitle(dimensionTitle);
                    dimensionTitleRenderer.addRecentEntry(currDimension);
                }
            }
        }
    }

    /**
     * Updates the biome title if conditions are met
     */
    private void updateBiomeTitle(Level world, BlockPos playerPos, Player player) {
        if (!Config.CLIENT.showBiomeTitles.get() || biomeTitleRenderer.cooldownTimer > 0) {
            return;
        }

        Holder<Biome> biomeHolder = world.getBiome(playerPos);
        ResourceLocation biomeBaseKey = world.registryAccess().registryOrThrow(Registries.BIOME).getKey(biomeHolder.value());

        if (biomeBaseKey != null &&
                !biomeTitleRenderer.matchesAnyRecentEntry(b -> {
                    ResourceLocation bKey = world.registryAccess().registryOrThrow(Registries.BIOME).getKey(b);
                    return bKey != null && bKey.equals(biomeBaseKey);
                })) {

            Component biomeTitle = getBiomeName(biomeBaseKey);

            if (biomeTitle != null) {
                if (biomeTitleRenderer.displayedTitle != null &&
                        biomeTitle.getString().equals(biomeTitleRenderer.displayedTitle.getString())) {
                    return;
                }

                biomeTitleRenderer.displayTitle(biomeTitle);
                biomeTitleRenderer.addRecentEntry(biomeHolder.value());
            }
        }
    }

    /**
     * Check if level info should be updated based on the final displayed values.
     * Only triggers when the actual displayed level or player bonus changes,
     * avoiding unnecessary updates when location changes but level stays the same.
     */
    private boolean shouldUpdateLevelInfo(int displayedLevel, int playerBonus) {
        // Always update on first call
        if (lastDisplayedLevel == -1) {
            return true;
        }
        
        // Only update if the displayed values actually changed
        return lastDisplayedLevel != displayedLevel || lastPlayerBonus != playerBonus;
    }

    private Component getStructureName(ResourceLocation structureId) {
        String structureNameKey = "structure." + structureId.getNamespace() + "." + structureId.getPath();

        if (Language.getInstance().has(structureNameKey)) {
            return Component.translatable(structureNameKey);
        }

        String name = structureId.getPath()
                .replace('_', ' ')
                .replace('/', ' ')
                .trim();

        String[] words = name.split(" ");
        StringBuilder formattedName = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                formattedName.append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1))
                        .append(" ");
            }
        }

        return Component.literal(formattedName.toString().trim());
    }

    /**
     * Get dimension name with Traveler's Titles preference.
     * Falls back to standard dimension key, then formatted ID if no translation found.
     */
    private Component getDimensionName(ResourceLocation dimensionBaseKey) {
        Language language = Language.getInstance();

        String travelersTitlesKey = Util.makeDescriptionId(TRAVELERS_TITLES_MOD_ID, dimensionBaseKey);
        if (language.has(travelersTitlesKey)) {
            return Component.translatable(travelersTitlesKey);
        }

        String dimensionKey = Util.makeDescriptionId("dimension", dimensionBaseKey);
        if (language.has(dimensionKey)) {
            return Component.translatable(dimensionKey);
        }

        String name = dimensionBaseKey.getPath()
                .replace('_', ' ')
                .replace('/', ' ')
                .trim();

        String[] words = name.split(" ");
        StringBuilder formattedName = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                formattedName.append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1))
                        .append(" ");
            }
        }

        return Component.literal(formattedName.toString().trim());
    }

    /**
     * Get biome name with Traveler's Titles preference.
     * Falls back to standard biome key. Returns null if no translation found.
     */
    private Component getBiomeName(ResourceLocation biomeBaseKey) {
        Language language = Language.getInstance();

        String travelersTitlesOverrideKey = Util.makeDescriptionId(TRAVELERS_TITLES_MOD_ID + ".biome", biomeBaseKey);
        if (language.has(travelersTitlesOverrideKey)) {
            return Component.translatable(travelersTitlesOverrideKey);
        }

        String normalBiomeKey = Util.makeDescriptionId("biome", biomeBaseKey);
        if (language.has(normalBiomeKey)) {
            return Component.translatable(normalBiomeKey);
        }

        return null;
    }

    /**
     * Clear all cached state (called on disconnect)
     */
    public void clearCache() {
        structureTitleRenderer.recentEntries.clear();
        structureTitleRenderer.displayedTitle = null;
        structureTitleRenderer.clearTimer();

        biomeTitleRenderer.recentEntries.clear();
        biomeTitleRenderer.displayedTitle = null;
        biomeTitleRenderer.clearTimer();

        dimensionTitleRenderer.recentEntries.clear();
        dimensionTitleRenderer.displayedTitle = null;
        dimensionTitleRenderer.clearTimer();

        levelInfoRenderer.clearTimer();

        lastDisplayedLevel = -1;
        lastPlayerBonus = -1;
    }
}

