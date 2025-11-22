package dev.muon.dynamic_difficulty.client.render;

import dev.muon.dynamic_difficulty.DynamicDifficulty;
import dev.muon.dynamic_difficulty.config.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.ARGB;

public class LevelInfoRenderer extends TitleRenderer<Void> {
    private Component displayedLevelInfo = null;
    
    public LevelInfoRenderer() {
        super(
            0, // No recent entries tracking for level info
            () -> true, // Always enabled (handled in renderText)
            () -> Config.CLIENT.levelInfoFadeInTime.get(),
            () -> Config.CLIENT.levelInfoDisplayTime.get(),
            () -> Config.CLIENT.levelInfoFadeOutTime.get(),
            () -> Config.CLIENT.levelInfoTextColor.get(),
            () -> Config.CLIENT.levelInfoRenderShadow.get(),
            () -> Config.CLIENT.levelInfoTextSize.get(),
            () -> Config.CLIENT.levelInfoAnchor.get(),
            () -> Config.CLIENT.levelInfoXOffset.get(),
            () -> Config.CLIENT.levelInfoYOffset.get()
        );
    }

    @Override
    protected int getTitleTextColor() {
        String colorStr = textColor.get();
        try {
            return (int) Long.parseLong(colorStr, 16);
        } catch (Exception e) {
            DynamicDifficulty.LOGGER.error("Level info text color {} is not a valid RGB color. Defaulting to white...", colorStr);
            return 0xFFFFFF;
        }
    }

    public void renderText(float partialTicks, GuiGraphics guiGraphics, 
                         DimensionTitleRenderer dimensionRenderer,
                         BiomeTitleRenderer biomeRenderer,
                         StructureTitleRenderer<?> structureRenderer) {
        // Render level info independently - it doesn't require a title to be active
        // Level info displays whenever we have it, regardless of whether titles are showing
        if (displayedLevelInfo == null || titleTimer <= 0) {
            return;
        }
        
        int opacity = getOpacity(partialTicks);
        if (opacity > 8) {
            guiGraphics.nextStratum();
            guiGraphics.pose().pushMatrix();
            
            // Calculate anchor position
            float[] anchorPos = getAnchorPosition(guiGraphics, anchor.get());
            guiGraphics.pose().translate(anchorPos[0], anchorPos[1]);
            
            int color = ARGB.color(opacity, getTitleTextColor());
            Font fontRenderer = Minecraft.getInstance().font;

            renderLevelInfo(guiGraphics, fontRenderer, color);
            guiGraphics.pose().popMatrix();
        }
    }

    private void renderLevelInfo(GuiGraphics guiGraphics, Font fontRenderer, int color) {
        guiGraphics.pose().pushMatrix();
        float textSizeValue = textSize.get().floatValue();
        guiGraphics.pose().scale(textSizeValue, textSizeValue);

        int levelInfoWidth = fontRenderer.width(displayedLevelInfo);
        // Derive alignment from anchor point
        // Note: alignmentOffset needs to account for scaled width
        Config.AnchorPoint anchorPoint = anchor.get();
        float alignmentOffset = getAlignmentOffset(anchorPoint, levelInfoWidth) * textSizeValue;
        
        int xOffsetValue = (int) ((alignmentOffset + xOffset.get()) / textSizeValue);
        // For TOP anchors, negative Y offset means "down from top", so flip sign
        // For BOTTOM/CENTER anchors, keep as is
        int rawYOffset = yOffset.get();
        int adjustedYOffset = isTopAnchor(anchorPoint) ? -rawYOffset : rawYOffset;
        int yOffsetValue = (int) (adjustedYOffset / textSizeValue);

        guiGraphics.drawString(fontRenderer, displayedLevelInfo, xOffsetValue, yOffsetValue, 
            color, renderShadow.get());
        guiGraphics.pose().popMatrix();
    }
    
    private boolean isTopAnchor(Config.AnchorPoint anchorPoint) {
        return anchorPoint == Config.AnchorPoint.TOP_LEFT ||
               anchorPoint == Config.AnchorPoint.TOP_CENTER ||
               anchorPoint == Config.AnchorPoint.TOP_RIGHT;
    }

    public void displayLevelInfo(int baseLevel, int structureBonus, int biomeBonus, int dimensionBonus, int playerBonus) {
        // Calculate base level (everything except player bonus)
        int displayLevel = baseLevel + structureBonus + biomeBonus + dimensionBonus;
        
        // Build level info: "Lv. n +p" format where n includes all bonuses except player
        MutableComponent levelInfo = Component.literal("Lv. " + displayLevel);
        
        // Only show player bonus separately if it exists
        if (playerBonus > 0) {
            levelInfo.append(Component.literal(" +" + playerBonus));
        }
        
        displayedLevelInfo = levelInfo;
        titleTimer = fadeInTime.get() + displayTime.get() + fadeOutTime.get();
    }

    @Override
    public void clearTimer() {
        super.clearTimer();
        displayedLevelInfo = null;
    }
}

