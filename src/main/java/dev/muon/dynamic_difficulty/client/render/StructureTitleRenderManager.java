package dev.muon.dynamic_difficulty.client.render;

import dev.muon.dynamic_difficulty.config.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

public class StructureTitleRenderManager {
    private static StructureTitleRenderManager instance;
    
    public final TitleRenderer<ResourceLocation> structureTitleRenderer;
    
    public StructureTitleRenderManager() {
        this.structureTitleRenderer = new TitleRenderer<>(
            1, // Only track 1 recent structure
            Config.CLIENT.showStructureTitles.get(),
            Config.CLIENT.structureTitleFadeInTime.get(),
            Config.CLIENT.structureTitleDisplayTime.get(),
            Config.CLIENT.structureTitleFadeOutTime.get(),
            Config.CLIENT.structureTitleTextColor.get(),
            Config.CLIENT.structureTitleRenderShadow.get(),
            Config.CLIENT.structureTitleTextSize.get(),
            Config.CLIENT.structureTitleXOffset.get(),
            Config.CLIENT.structureTitleYOffset.get(),
            Config.CLIENT.structureSubtitleScale.get(),
            Config.CLIENT.structureSubtitleSpacing.get(),
            Config.CLIENT.structureTitleCenterText.get()
        );
    }
    
    public static StructureTitleRenderManager getInstance() {
        if (instance == null) {
            instance = new StructureTitleRenderManager();
        }
        return instance;
    }

    public void clientTick() {
        if (!Minecraft.getInstance().isPaused()) {
            structureTitleRenderer.tick();
        }
    }

    public void renderTitles(GuiGraphics guiGraphics, float partialTicks) {
        if (!Minecraft.getInstance().getDebugOverlay().showDebugScreen()) {
            structureTitleRenderer.renderText(partialTicks, guiGraphics);
        }
    }
    
    /**
     * Display structure title when notified by server
     */
    public void displayStructureTitle(ResourceLocation structureId, int structureBonus, int baseLevel, int playerBonus) {
        if (!structureTitleRenderer.enabled || structureBonus <= 0) {
            return;
        }
        
        // Check if we've recently shown this structure
        if (!structureTitleRenderer.matchesAnyRecentEntry(id -> id.equals(structureId))) {
            // Get structure name
            Component structureName = getStructureName(structureId);
            
            // Calculate total level
            int totalLevel = baseLevel + structureBonus;
            
            // Create subtitle with level info - show base+structure, then player bonus separately
            MutableComponent subtitle = Component.translatable("dynamic_difficulty.structure.level_info", totalLevel);
            
            // Append player bonus if present
            if (playerBonus > 0) {
                subtitle.append(Component.literal(" (+" + playerBonus + ")"));
            }
            
            // Display the title
            structureTitleRenderer.displayTitle(structureName, subtitle);
            structureTitleRenderer.addRecentEntry(structureId);
            
        }
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
}
