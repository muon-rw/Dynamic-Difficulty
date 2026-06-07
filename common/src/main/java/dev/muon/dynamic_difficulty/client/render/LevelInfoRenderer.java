package dev.muon.dynamic_difficulty.client.render;

import dev.muon.dynamic_difficulty.DynamicDifficulty;
import dev.muon.dynamic_difficulty.config.ConfigClient;
import dev.muon.dynamic_difficulty.config.Configs;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.ARGB;

public class LevelInfoRenderer extends TitleRenderer<Void> {
    private static final int NO_RECENT_ENTRY_TRACKING = 0;

    private Component displayedLevelInfo = null;

    public LevelInfoRenderer() {
        super(
                NO_RECENT_ENTRY_TRACKING,
                () -> true, // Always enabled (handled in renderText)
                Configs.CLIENT.levelInfoFadeInTime,
                Configs.CLIENT.levelInfoDisplayTime,
                Configs.CLIENT.levelInfoFadeOutTime,
                Configs.CLIENT.levelInfoTextColor,
                Configs.CLIENT.levelInfoRenderShadow,
                Configs.CLIENT.levelInfoTextSize,
                Configs.CLIENT.levelInfoAnchor,
                Configs.CLIENT.levelInfoXOffset,
                Configs.CLIENT.levelInfoYOffset
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

    public void renderText(float partialTicks, GuiGraphicsExtractor guiGraphics,
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

    private void renderLevelInfo(GuiGraphicsExtractor guiGraphics, Font fontRenderer, int color) {
        guiGraphics.pose().pushMatrix();
        float textSizeValue = textSize.get().floatValue();
        guiGraphics.pose().scale(textSizeValue, textSizeValue);

        int levelInfoWidth = fontRenderer.width(displayedLevelInfo);
        // Note: alignmentOffset needs to account for scaled width
        ConfigClient.AnchorPoint anchorPoint = anchor.get();
        float alignmentOffset = getAlignmentOffset(anchorPoint, levelInfoWidth) * textSizeValue;

        int xOffsetValue = (int) ((alignmentOffset + xOffset.get()) / textSizeValue);
        // For TOP anchors, negative Y offset means "down from top", so flip sign
        // For BOTTOM/CENTER anchors, keep as is
        int rawYOffset = yOffset.get();
        int adjustedYOffset = isTopAnchor(anchorPoint) ? -rawYOffset : rawYOffset;
        int yOffsetValue = (int) (adjustedYOffset / textSizeValue);

        guiGraphics.text(fontRenderer, displayedLevelInfo, xOffsetValue, yOffsetValue,
                color, renderShadow.get());
        guiGraphics.pose().popMatrix();
    }

    public void displayLevelInfo(int displayedLevel, int playerBonus) {
        int totalLevel = displayedLevel + playerBonus;
        MutableComponent levelInfo = Component.literal("Lv. " + totalLevel);

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

