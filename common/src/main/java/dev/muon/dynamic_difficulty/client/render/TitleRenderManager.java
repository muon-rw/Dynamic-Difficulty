package dev.muon.dynamic_difficulty.client.render;

import dev.muon.dynamic_difficulty.DynamicDifficulty;
import dev.muon.dynamic_difficulty.config.Configs;
import dev.muon.dynamic_difficulty.data.DimensionLevelingSettingsStore;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.dimension.DimensionType;

import org.jetbrains.annotations.Nullable;

public class TitleRenderManager {
    private static TitleRenderManager instance;

    public final StructureTitleRenderer<Identifier> structureTitleRenderer;
    public final BiomeTitleRenderer biomeTitleRenderer;
    public final DimensionTitleRenderer dimensionTitleRenderer;
    public final LevelInfoRenderer levelInfoRenderer;

    private static final String TRAVELERS_TITLES_MOD_ID = "travelerstitles";

    private DimensionType currentDimension = null;

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

    public void renderTitles(GuiGraphicsExtractor guiGraphics, float partialTicks) {
        if (!Minecraft.getInstance().getDebugOverlay().showDebugScreen()) {
            dimensionTitleRenderer.renderText(partialTicks, guiGraphics);
            biomeTitleRenderer.renderText(partialTicks, guiGraphics);
            structureTitleRenderer.renderText(partialTicks, guiGraphics);
            levelInfoRenderer.renderText(partialTicks, guiGraphics,
                    dimensionTitleRenderer, biomeTitleRenderer, structureTitleRenderer);
        }
    }

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

    /** Clears the biome cache; biome titles are dimension-specific. */
    private void playerChangedDimension(Player player) {
        if (Configs.CLIENT.showBiomeTitles.get()) {
            biomeTitleRenderer.clearTimer();
            biomeTitleRenderer.recentEntries.clear();
            biomeTitleRenderer.displayedTitle = null;
        }
    }

    public void displayStructureTitle(Identifier structureId, int structureBonus, int baseLevel, int playerBonus, int displayedLevel) {
        updateLevelInfoIfChanged(displayedLevel, playerBonus);

        if (Configs.CLIENT.showStructureTitles.get()) {
            boolean shouldDisplay = !Configs.CLIENT.structureTitleOnlyAnnounceIfModified.get() || structureBonus > 0;
            if (shouldDisplay && !structureTitleRenderer.matchesAnyRecentEntry(id -> id.equals(structureId))) {
                Component structureName = getStructureName(structureId);
                structureTitleRenderer.displayTitle(structureName);
                structureTitleRenderer.addRecentEntry(structureId);
            }
        }
    }

    public void displayBiomeTitle(Identifier biomeId, int biomeBonus, int baseLevel, int playerBonus, int displayedLevel) {
        updateLevelInfoIfChanged(displayedLevel, playerBonus);

        if (Configs.CLIENT.showBiomeTitles.get()) {
            boolean shouldDisplay = !Configs.CLIENT.biomeTitleOnlyAnnounceIfModified.get() || biomeBonus > 0;
            if (shouldDisplay) {
                Component biomeName = getBiomeName(biomeId);
                if (biomeName != null) {
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

    /** Dimensions affect base level through settings, not bonuses. */
    public void displayDimensionTitle(Identifier dimensionId, int baseLevel, int playerBonus, int displayedLevel) {
        updateLevelInfoIfChanged(displayedLevel, playerBonus);

        if (Configs.CLIENT.showDimensionTitles.get()) {
            boolean shouldDisplay = true;
            if (Configs.CLIENT.dimensionTitleOnlyAnnounceIfModified.get()) {
                Level world = Minecraft.getInstance().level;
                if (world != null) {
                    shouldDisplay = DimensionLevelingSettingsStore.hasCustomSettings(
                            world.dimension(), world.registryAccess().lookupOrThrow(Registries.DIMENSION));
                }
            }
            
            if (shouldDisplay) {
                DimensionTitle dimensionTitle = getDimensionTitle(dimensionId);
                DimensionType currDimension = Minecraft.getInstance().level != null ?
                        Minecraft.getInstance().level.dimensionType() : null;
                if (currDimension != null &&
                        !dimensionTitleRenderer.matchesAnyRecentEntry(d -> d == currDimension)) {

                    dimensionTitleRenderer.displayTitle(dimensionTitle.name(), dimensionTitle.overrideColor());
                    dimensionTitleRenderer.addRecentEntry(currDimension);
                }
            }
        }
    }

    private void updateDimensionTitle(Level world, Player player) {
        if (!Configs.CLIENT.showDimensionTitles.get()) {
            return;
        }

        boolean shouldDisplay = true;
        if (Configs.CLIENT.dimensionTitleOnlyAnnounceIfModified.get()) {
            shouldDisplay = DimensionLevelingSettingsStore.hasCustomSettings(
                    world.dimension(), world.registryAccess().lookupOrThrow(Registries.DIMENSION));
        }

        if (shouldDisplay) {
            DimensionType currDimension = world.dimensionType();
            if (!dimensionTitleRenderer.matchesAnyRecentEntry(d -> d == currDimension)) {
                Identifier dimensionBaseKey = world.dimension().identifier();
                DimensionTitle dimensionTitle = getDimensionTitle(dimensionBaseKey);

                dimensionTitleRenderer.displayTitle(dimensionTitle.name(), dimensionTitle.overrideColor());
                dimensionTitleRenderer.addRecentEntry(currDimension);
            }
        }
    }

    private void updateBiomeTitle(Level world, BlockPos playerPos, Player player) {
        if (!Configs.CLIENT.showBiomeTitles.get() || biomeTitleRenderer.cooldownTimer > 0) {
            return;
        }

        Holder<Biome> biomeHolder = world.getBiome(playerPos);
        Identifier biomeBaseKey = world.registryAccess().lookupOrThrow(Registries.BIOME).getKey(biomeHolder.value());

        if (biomeBaseKey != null &&
                !biomeTitleRenderer.matchesAnyRecentEntry(b -> {
                    Identifier bKey = world.registryAccess().lookupOrThrow(Registries.BIOME).getKey(b);
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

    private void updateLevelInfoIfChanged(int displayedLevel, int playerBonus) {
        if (shouldUpdateLevelInfo(displayedLevel, playerBonus)) {
            levelInfoRenderer.displayLevelInfo(displayedLevel, playerBonus);
            lastDisplayedLevel = displayedLevel;
            lastPlayerBonus = playerBonus;
        }
    }

    private boolean shouldUpdateLevelInfo(int displayedLevel, int playerBonus) {
        if (lastDisplayedLevel == -1) {
            return true;
        }
        return lastDisplayedLevel != displayedLevel || lastPlayerBonus != playerBonus;
    }

    private Component getStructureName(Identifier structureId) {
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
     * Resolved dimension title text plus an optional color override sourced from a
     * Visual Traveler's Titles-style resource pack (`travelerstitles.namespace.path.color`).
     */
    public record DimensionTitle(Component name, @Nullable Integer overrideColor) {}

    /** Lookup order: Traveler's Titles key, then standard dimension key, then formatted ID. */
    private DimensionTitle getDimensionTitle(Identifier dimensionBaseKey) {
        Language language = Language.getInstance();

        String travelersTitlesKey = Util.makeDescriptionId(TRAVELERS_TITLES_MOD_ID, dimensionBaseKey);
        if (language.has(travelersTitlesKey)) {
            Integer overrideColor = parseColorKey(language, travelersTitlesKey + ".color");
            return new DimensionTitle(Component.translatable(travelersTitlesKey), overrideColor);
        }

        String dimensionKey = Util.makeDescriptionId("dimension", dimensionBaseKey);
        if (language.has(dimensionKey)) {
            return new DimensionTitle(Component.translatable(dimensionKey), null);
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

        return new DimensionTitle(Component.literal(formattedName.toString().trim()), null);
    }

    @Nullable
    private static Integer parseColorKey(Language language, String key) {
        if (!language.has(key)) {
            return null;
        }
        String hex = language.getOrDefault(key);
        try {
            return (int) Long.parseLong(hex.trim(), 16);
        } catch (NumberFormatException e) {
            DynamicDifficulty.LOGGER.warn("Invalid hex color '{}' for lang key {}", hex, key);
            return null;
        }
    }

    private Component getBiomeName(Identifier biomeBaseKey) {
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

