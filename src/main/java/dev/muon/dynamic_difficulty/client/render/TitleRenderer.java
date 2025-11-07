package dev.muon.dynamic_difficulty.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.muon.dynamic_difficulty.DynamicDifficulty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import javax.annotation.Nullable;
import java.util.LinkedList;
import java.util.function.Predicate;

public class TitleRenderer<T> {
    public final LinkedList<T> recentEntries = new LinkedList<>();
    public Component displayedTitle = null;
    public Component displayedSubTitle = null;
    public int titleTimer = 0;

    // User-customizable text effects
    public int maxRecentListSize;
    public boolean enabled;
    public int titleFadeInTicks;
    public int titleDisplayTime;
    public int titleFadeOutTicks;
    public int titleTextColor;
    public String titleDefaultTextColor;
    public boolean showTextShadow;
    public float titleTextSize;
    public int titleXOffset;
    public int titleYOffset;
    public float subTitleScale;
    public int subTitleSpacing;
    public boolean isTextCentered;

    public TitleRenderer(
        int maxRecentListSize,
        boolean enabled,
        int fadeInTicks,
        int displayTicks,
        int fadeOutTicks,
        String textColor,
        boolean showTextShadow,
        double textSize,
        int xOffset,
        int yOffset,
        float subTitleScale,
        int subTitleSpacing,
        boolean centerText
    ) {
        this.maxRecentListSize = maxRecentListSize;
        this.enabled = enabled;
        this.titleFadeInTicks = fadeInTicks;
        this.titleDisplayTime = displayTicks;
        this.titleFadeOutTicks = fadeOutTicks;
        this.setColor(textColor);
        this.titleDefaultTextColor = textColor;
        this.showTextShadow = showTextShadow;
        this.titleTextSize = (float)textSize;
        this.titleXOffset = xOffset;
        this.titleYOffset = yOffset;
        this.subTitleScale = subTitleScale;
        this.subTitleSpacing = subTitleSpacing;
        this.isTextCentered = centerText;
    }

    public void renderText(float partialTicks, GuiGraphics guiGraphics) {
        if (displayedTitle != null && titleTimer > 0) {
            int opacity = getOpacity(partialTicks);
            if (opacity > 8) {
                // Set up render system
                guiGraphics.pose().pushPose();
                if (this.isTextCentered) {
                    guiGraphics.pose().translate(Minecraft.getInstance().getWindow().getGuiScaledWidth() / 2D,
                                                 (Minecraft.getInstance().getWindow().getGuiScaledHeight() / 2D), 0);
                }
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                int alpha = opacity << 24 & 0xFF000000;
                Font fontRenderer = Minecraft.getInstance().font;

                renderTitle(guiGraphics, fontRenderer, alpha);
                renderSubtitle(guiGraphics, fontRenderer, alpha);

                RenderSystem.disableBlend();
                guiGraphics.pose().popPose();
            }
        }
    }

    private int getOpacity(float partialTicks) {
        float age = (float) titleTimer - partialTicks;
        int opacity = 255;

        // Fade in
        if (titleTimer > titleFadeOutTicks + titleDisplayTime) {
            float r = (float) (titleFadeInTicks + titleDisplayTime + titleFadeOutTicks) - age;
            opacity = (int) (r * 255.0F / (float) titleFadeInTicks);
        }

        // Fade out
        if (titleTimer <= titleFadeOutTicks) {
            opacity = (int) (age * 255.0F / (float) titleFadeOutTicks);
        }

        opacity = Mth.clamp(opacity, 0, 255);
        return opacity;
    }

    private void renderTitle(GuiGraphics guiGraphics, Font fontRenderer, int alpha) {
        // Render title
        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(titleTextSize, titleTextSize, titleTextSize);
        int titleWidth = fontRenderer.width(displayedTitle);

        int xOffset = this.isTextCentered
            ? this.titleXOffset + (-titleWidth / 2)
            : this.titleXOffset;

        guiGraphics.drawString(fontRenderer, displayedTitle, xOffset, titleYOffset, titleTextColor | alpha, showTextShadow);
        guiGraphics.pose().popPose();
    }

    private void renderSubtitle(GuiGraphics guiGraphics, Font fontRenderer, int alpha) {
        if (displayedSubTitle != null) {
            guiGraphics.pose().pushPose();

            float subTitleTextSize = titleTextSize * subTitleScale;
            guiGraphics.pose().scale(subTitleTextSize, subTitleTextSize, subTitleTextSize);

            int subtitleWidth = (int) (fontRenderer.width(displayedSubTitle) * subTitleScale);
            int subXOffset = (int) ((this.isTextCentered
                    ? this.titleXOffset + ((float) -subtitleWidth / 2)
                    : this.titleXOffset)
                    / subTitleScale);
            int subYOffset = (int) ((titleYOffset + subTitleSpacing) / subTitleScale);

            guiGraphics.drawString(fontRenderer, displayedSubTitle, subXOffset, subYOffset, titleTextColor | alpha, showTextShadow);
            guiGraphics.pose().popPose();
        }
    }

    public void tick() {
        if (titleTimer > 0) {
            --titleTimer;
            if (titleTimer <= 0) {
                clearTimer();
            }
        }
    }

    public void displayTitle(Component titleText, @Nullable Component subtitleText) {
        displayedTitle = titleText;
        displayedSubTitle = subtitleText;
        titleTimer = titleFadeInTicks + titleDisplayTime + titleFadeOutTicks;
    }

    public void clearTimer() {
        titleTimer = 0;
    }

    public void setColor(String textColor) {
        try {
            this.titleTextColor = (int) Long.parseLong(textColor, 16);
        } catch (Exception e) {
            DynamicDifficulty.LOGGER.error("Text color {} is not a valid RGB color. Defaulting to white...", textColor);
            DynamicDifficulty.LOGGER.error(e.toString());
            this.titleTextColor = 0xFFFFFF;
        }
    }

    public void addRecentEntry(T entry) {
        if (this.recentEntries.size() >= this.maxRecentListSize && !this.recentEntries.isEmpty()) {
            this.recentEntries.removeFirst();
        }
        if (this.maxRecentListSize > 0) {
            recentEntries.addLast(entry);
        }
    }

    public boolean matchesAnyRecentEntry(Predicate<T> entryMatchPredicate) {
        return this.recentEntries.stream().anyMatch(entryMatchPredicate);
    }
}
