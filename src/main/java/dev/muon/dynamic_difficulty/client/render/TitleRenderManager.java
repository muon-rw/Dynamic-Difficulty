package dev.muon.dynamic_difficulty.client.render;

import dev.muon.dynamic_difficulty.config.Config;
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
    
    // Track current dimension for change detection
    private DimensionType currentDimension = null;
    
    // Track last displayed level bonuses (excluding player bonus)
    private int lastBaseLevel = -1;
    private int lastStructureBonus = -1;
    private int lastBiomeBonus = -1;
    private int lastDimensionBonus = -1;
    
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
            // Render in order: dimension, biome, structure, level info
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
            
            // Detect dimension change
            DimensionType newDimension = world.dimensionType();
            if (currentDimension != newDimension) {
                playerChangedDimension(player);
                currentDimension = newDimension;
            }
            
            // Update dimension title
            updateDimensionTitle(world, player);
            
            // Update biome title
            updateBiomeTitle(world, playerPos, player);
        }
    }
    
    /**
     * Called when player changes dimension
     */
    private void playerChangedDimension(Player player) {
        // Reset biome cache on dimension change
        if (Config.CLIENT.showBiomeTitles.get()) {
            biomeTitleRenderer.clearTimer();
            biomeTitleRenderer.recentEntries.clear();
            biomeTitleRenderer.displayedTitle = null;
        }
    }
    
    /**
     * Display structure title when notified by server
     */
    public void displayStructureTitle(ResourceLocation structureId, int structureBonus, int baseLevel, int playerBonus) {
        // Only display level info if the non-player bonuses have changed
        int biomeBonus = lastBiomeBonus >= 0 ? lastBiomeBonus : 0;
        int dimensionBonus = lastDimensionBonus >= 0 ? lastDimensionBonus : 0;
        if (shouldUpdateLevelInfo(baseLevel, structureBonus, biomeBonus, dimensionBonus)) {
            levelInfoRenderer.displayLevelInfo(baseLevel, structureBonus, biomeBonus, dimensionBonus, playerBonus);
            updateTrackedLevelValues(baseLevel, structureBonus, biomeBonus, dimensionBonus);
        }
        
        // Display title only if enabled and bonus > 0
        if (Config.CLIENT.showStructureTitles.get() && structureBonus > 0) {
            // Check if we've recently shown this structure
            if (!structureTitleRenderer.matchesAnyRecentEntry(id -> id.equals(structureId))) {
                // Get structure name
                Component structureName = getStructureName(structureId);
                
                // Display the title (level info is separate)
                structureTitleRenderer.displayTitle(structureName);
                structureTitleRenderer.addRecentEntry(structureId);
            }
        }
    }
    
    /**
     * Display biome title when notified by server
     */
    public void displayBiomeTitle(ResourceLocation biomeId, int biomeBonus, int baseLevel, int playerBonus) {
        // Only display level info if the non-player bonuses have changed
        int structureBonus = lastStructureBonus >= 0 ? lastStructureBonus : 0;
        int dimensionBonus = lastDimensionBonus >= 0 ? lastDimensionBonus : 0;
        if (shouldUpdateLevelInfo(baseLevel, structureBonus, biomeBonus, dimensionBonus)) {
            levelInfoRenderer.displayLevelInfo(baseLevel, structureBonus, biomeBonus, dimensionBonus, playerBonus);
            updateTrackedLevelValues(baseLevel, structureBonus, biomeBonus, dimensionBonus);
        }
        
        // Display title only if enabled and bonus > 0
        if (Config.CLIENT.showBiomeTitles.get() && biomeBonus > 0) {
            // Get biome name
            Component biomeName = getBiomeName(biomeId);
            if (biomeName != null) {
                // Check if we've recently shown this biome (by checking if name matches)
                if (biomeTitleRenderer.displayedTitle == null || 
                    !biomeName.getString().equals(biomeTitleRenderer.displayedTitle.getString())) {
                    
                    // Display the title
                    biomeTitleRenderer.displayTitle(biomeName);
                    
                    // Try to add biome to recent entries if we have access to the level
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
    
    /**
     * Display dimension title when notified by server
     */
    public void displayDimensionTitle(ResourceLocation dimensionId, int dimensionBonus, int baseLevel, int playerBonus) {
        // Only display level info if the non-player bonuses have changed
        int structureBonus = lastStructureBonus >= 0 ? lastStructureBonus : 0;
        int biomeBonus = lastBiomeBonus >= 0 ? lastBiomeBonus : 0;
        if (shouldUpdateLevelInfo(baseLevel, structureBonus, biomeBonus, dimensionBonus)) {
            levelInfoRenderer.displayLevelInfo(baseLevel, structureBonus, biomeBonus, dimensionBonus, playerBonus);
            updateTrackedLevelValues(baseLevel, structureBonus, biomeBonus, dimensionBonus);
        }
        
        // Display title only if enabled
        if (Config.CLIENT.showDimensionTitles.get()) {
            // Get dimension name
            Component dimensionName = getDimensionName(dimensionId);
            if (dimensionName != null) {
                // Check if we've recently shown this dimension
                DimensionType currDimension = Minecraft.getInstance().level != null ? 
                    Minecraft.getInstance().level.dimensionType() : null;
                if (currDimension != null && 
                    !dimensionTitleRenderer.matchesAnyRecentEntry(d -> d == currDimension)) {
                    
                    // Display the title
                    dimensionTitleRenderer.displayTitle(dimensionName);
                    dimensionTitleRenderer.addRecentEntry(currDimension);
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
    
    /**
     * Updates the biome title if conditions are met
     */
    private void updateBiomeTitle(Level world, BlockPos playerPos, Player player) {
        if (!Config.CLIENT.showBiomeTitles.get() || biomeTitleRenderer.cooldownTimer > 0) {
            return;
        }
        
        Holder<Biome> biomeHolder = world.getBiome(playerPos);
        ResourceLocation biomeBaseKey = world.registryAccess().lookupOrThrow(Registries.BIOME).getKey(biomeHolder.value());
        
        if (biomeBaseKey != null && 
            !biomeTitleRenderer.matchesAnyRecentEntry(b -> {
                ResourceLocation bKey = world.registryAccess().lookupOrThrow(Registries.BIOME).getKey(b);
                return bKey != null && bKey.equals(biomeBaseKey);
            })) {
            
            Component biomeTitle = getBiomeName(biomeBaseKey);
            
            if (biomeTitle != null) {
                // Don't display if title hasn't changed
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
     * Check if level info should be updated based on non-player bonuses
     */
    private boolean shouldUpdateLevelInfo(int baseLevel, int structureBonus, int biomeBonus, int dimensionBonus) {
        return lastBaseLevel != baseLevel || 
               lastStructureBonus != structureBonus || 
               lastBiomeBonus != biomeBonus || 
               lastDimensionBonus != dimensionBonus;
    }
    
    private Component getStructureName(ResourceLocation structureId) {
        // Try to get localized name
        String structureNameKey = "structure." + structureId.getNamespace() + "." + structureId.getPath();
        
        if (Language.getInstance().has(structureNameKey)) {
            return Component.translatable(structureNameKey);
        }
        
        // Fallback to a formatted version of the ID
        String name = structureId.getPath()
                .replace('_', ' ')
                .replace('/', ' ')
                .trim();
        
        // Capitalize first letter of each word
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
     * Get dimension name with Traveler's Titles preference
     */
    private Component getDimensionName(ResourceLocation dimensionBaseKey) {
        Language language = Language.getInstance();
        
        // First, check for Traveler's Titles override
        String travelersTitlesKey = Util.makeDescriptionId(TRAVELERS_TITLES_MOD_ID, dimensionBaseKey);
        if (language.has(travelersTitlesKey)) {
            return Component.translatable(travelersTitlesKey);
        }
        
        // Fallback to standard dimension key
        String dimensionKey = Util.makeDescriptionId("dimension", dimensionBaseKey);
        if (language.has(dimensionKey)) {
            return Component.translatable(dimensionKey);
        }
        
        // Last resort: formatted ID
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
     * Get biome name with Traveler's Titles preference
     */
    private Component getBiomeName(ResourceLocation biomeBaseKey) {
        Language language = Language.getInstance();
        
        // First, check for Traveler's Titles override
        String travelersTitlesOverrideKey = Util.makeDescriptionId(TRAVELERS_TITLES_MOD_ID + ".biome", biomeBaseKey);
        if (language.has(travelersTitlesOverrideKey)) {
            return Component.translatable(travelersTitlesOverrideKey);
        }
        
        // Next, check for standard biome key
        String normalBiomeKey = Util.makeDescriptionId("biome", biomeBaseKey);
        if (language.has(normalBiomeKey)) {
            return Component.translatable(normalBiomeKey);
        }
        
        // No entry found - return null to skip display
        return null;
    }
    
    /**
     * Update tracked level values
     */
    private void updateTrackedLevelValues(int baseLevel, int structureBonus, int biomeBonus, int dimensionBonus) {
        lastBaseLevel = baseLevel;
        lastStructureBonus = structureBonus;
        lastBiomeBonus = biomeBonus;
        lastDimensionBonus = dimensionBonus;
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
        
        // Reset tracked level values
        lastBaseLevel = -1;
        lastStructureBonus = -1;
        lastBiomeBonus = -1;
        lastDimensionBonus = -1;
    }
}
