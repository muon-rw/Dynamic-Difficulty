package dev.muon.dynamic_difficulty.client.render;

import dev.muon.dynamic_difficulty.DynamicDifficulty;
import dev.muon.dynamic_difficulty.config.Config;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FastColor;

public class LevelInfoRenderer extends TitleRenderer<Void> {
    private Component displayedLevelInfo = null;

    public LevelInfoRenderer() {
        super(
                0, // No recent entries tracking for level info
                () -> true, // Always enabled (handled in renderText)
                Config.CLIENT.levelInfoFadeInTime,
                Config.CLIENT.levelInfoDisplayTime,
                Config.CLIENT.levelInfoFadeOutTime,
                Config.CLIENT.levelInfoTextColor,
                Config.CLIENT.levelInfoRenderShadow,
                Config.CLIENT.levelInfoTextSize,
                Config.CLIENT.levelInfoAnchor,
                Config.CLIENT.levelInfoXOffset,
                Config.CLIENT.levelInfoYOffset
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
            guiGraphics.pose().pushPose();

            // Calculate anchor position
            float[] anchorPos = getAnchorPosition(guiGraphics, anchor.get());
            guiGraphics.pose().translate(anchorPos[0], anchorPos[1], 0.0f);

            int color = FastColor.ARGB32.color(opacity, getTitleTextColor());
            Font fontRenderer = Minecraft.getInstance().font;

            renderLevelInfo(guiGraphics, fontRenderer, color);
            guiGraphics.pose().popPose();
        }
    }

    private void renderLevelInfo(GuiGraphics guiGraphics, Font fontRenderer, int color) {
        guiGraphics.pose().pushPose();
        float textSizeValue = textSize.get().floatValue();
        guiGraphics.pose().scale(textSizeValue, textSizeValue, 1.0f);

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
        guiGraphics.pose().popPose();
    }

    /**
     * Display level info using the calculated displayed level from the server.
     * The server calculates this with proper max level cap and bypassing bonus handling.
     * 
     * @param displayedLevel The final calculated level without player bonus (from server)
     * @param playerBonus The player bonus to show separately
     */
    public void displayLevelInfo(int displayedLevel, int playerBonus) {
        int totalLevel = displayedLevel + playerBonus;
        MutableComponent levelInfo = Component.literal("Lv. " + totalLevel);

        // Only show breakdown if advanced tooltips is enabled and there's a player bonus
        if (Minecraft.getInstance().options.advancedItemTooltips && playerBonus > 0) {
            levelInfo.append(Component.translatable("dynamic_difficulty.level_info.breakdown", displayedLevel, playerBonus)
                    .withStyle(ChatFormatting.GRAY));
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

