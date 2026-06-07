package dev.muon.dynamic_difficulty.client.render;

import dev.muon.dynamic_difficulty.DynamicDifficulty;
import dev.muon.dynamic_difficulty.config.ConfigClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

import java.util.LinkedList;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class TitleRenderer<T> {
    public final LinkedList<T> recentEntries = new LinkedList<>();
    public Component displayedTitle = null;
    public int titleTimer = 0;
    public int cooldownTimer = 0;
    protected Integer overrideColor = null;

    protected final int maxRecentListSize;
    protected final Supplier<Boolean> enabled;
    protected final Supplier<Integer> fadeInTime;
    protected final Supplier<Integer> displayTime;
    protected final Supplier<Integer> fadeOutTime;
    protected final Supplier<String> textColor;
    protected final Supplier<Boolean> renderShadow;
    protected final Supplier<Double> textSize;
    protected final Supplier<ConfigClient.AnchorPoint> anchor;
    protected final Supplier<Integer> xOffset;
    protected final Supplier<Integer> yOffset;

    public TitleRenderer(
            int maxRecentListSize,
            Supplier<Boolean> enabled,
            Supplier<Integer> fadeInTime,
            Supplier<Integer> displayTime,
            Supplier<Integer> fadeOutTime,
            Supplier<String> textColor,
            Supplier<Boolean> renderShadow,
            Supplier<Double> textSize,
            Supplier<ConfigClient.AnchorPoint> anchor,
            Supplier<Integer> xOffset,
            Supplier<Integer> yOffset
    ) {
        this.maxRecentListSize = maxRecentListSize;
        this.enabled = enabled;
        this.fadeInTime = fadeInTime;
        this.displayTime = displayTime;
        this.fadeOutTime = fadeOutTime;
        this.textColor = textColor;
        this.renderShadow = renderShadow;
        this.textSize = textSize;
        this.anchor = anchor;
        this.xOffset = xOffset;
        this.yOffset = yOffset;
    }

    protected int getTitleTextColor() {
        if (overrideColor != null) {
            return overrideColor;
        }
        String colorStr = textColor.get();
        try {
            return (int) Long.parseLong(colorStr, 16);
        } catch (Exception e) {
            DynamicDifficulty.LOGGER.error("Text color {} is not a valid RGB color. Defaulting to white...", colorStr);
            return 0xFFFFFF;
        }
    }

    public void renderText(float partialTicks, GuiGraphicsExtractor guiGraphics) {
        if (!enabled.get() || displayedTitle == null || titleTimer <= 0) {
            return;
        }

        int opacity = getOpacity(partialTicks);
        if (opacity > 8) {
            guiGraphics.nextStratum();
            guiGraphics.pose().pushMatrix();

            float[] anchorPos = getAnchorPosition(guiGraphics, anchor.get());
            guiGraphics.pose().translate(anchorPos[0], anchorPos[1]);

            int color = ARGB.color(opacity, getTitleTextColor());
            Font fontRenderer = Minecraft.getInstance().font;

            renderTitle(guiGraphics, fontRenderer, color);

            guiGraphics.pose().popMatrix();
        }
    }

    protected float[] getAnchorPosition(GuiGraphicsExtractor guiGraphics, ConfigClient.AnchorPoint anchorPoint) {
        float screenWidth = guiGraphics.guiWidth();
        float screenHeight = guiGraphics.guiHeight();
        float x = 0, y = 0;

        switch (anchorPoint) {
            case TOP_LEFT:
                x = 0;
                y = 0;
                break;
            case TOP_CENTER:
                x = screenWidth / 2.0F;
                y = 0;
                break;
            case TOP_RIGHT:
                x = screenWidth;
                y = 0;
                break;
            case CENTER_LEFT:
                x = 0;
                y = screenHeight / 2.0F;
                break;
            case CENTER:
                x = screenWidth / 2.0F;
                y = screenHeight / 2.0F;
                break;
            case CENTER_RIGHT:
                x = screenWidth;
                y = screenHeight / 2.0F;
                break;
            case BOTTOM_LEFT:
                x = 0;
                y = screenHeight;
                break;
            case BOTTOM_CENTER:
                x = screenWidth / 2.0F;
                y = screenHeight;
                break;
            case BOTTOM_RIGHT:
                x = screenWidth;
                y = screenHeight;
                break;
        }

        return new float[]{x, y};
    }

    protected float getAlignmentOffset(ConfigClient.AnchorPoint anchorPoint, int textWidth) {
        return switch (anchorPoint) {
            case TOP_LEFT, CENTER_LEFT, BOTTOM_LEFT -> 0;
            case TOP_CENTER, CENTER, BOTTOM_CENTER -> -textWidth / 2.0F;
            case TOP_RIGHT, CENTER_RIGHT, BOTTOM_RIGHT -> -textWidth;
        };
    }

    protected int getOpacity(float partialTicks) {
        float age = (float) titleTimer - partialTicks;
        int opacity = 255;
        int fadeOutTicks = fadeOutTime.get();
        int displayTimeTicks = displayTime.get();
        int fadeInTicks = fadeInTime.get();

        if (titleTimer > fadeOutTicks + displayTimeTicks) {
            float r = (float) (fadeInTicks + displayTimeTicks + fadeOutTicks) - age;
            opacity = (int) (r * 255.0F / (float) fadeInTicks);
        }

        if (titleTimer <= fadeOutTicks) {
            opacity = (int) (age * 255.0F / (float) fadeOutTicks);
        }

        opacity = Mth.clamp(opacity, 0, 255);
        return opacity;
    }

    private void renderTitle(GuiGraphicsExtractor guiGraphics, Font fontRenderer, int color) {
        guiGraphics.pose().pushMatrix();
        float textSizeValue = textSize.get().floatValue();
        guiGraphics.pose().scale(textSizeValue, textSizeValue);
        int titleWidth = fontRenderer.width(displayedTitle);

        ConfigClient.AnchorPoint anchorPoint = anchor.get();
        // Alignment offset must account for scaled width since we're rendering in scaled space
        float alignmentOffset = getAlignmentOffset(anchorPoint, titleWidth) * textSizeValue;

        int xOffsetValue = (int) ((alignmentOffset + xOffset.get()) / textSizeValue);
        // For TOP anchors, negative Y offset means "down from top", so flip sign
        // For BOTTOM/CENTER anchors, keep as is
        int rawYOffset = yOffset.get();
        int adjustedYOffset = isTopAnchor(anchorPoint) ? -rawYOffset : rawYOffset;
        int yOffsetValue = (int) (adjustedYOffset / textSizeValue);

        guiGraphics.text(fontRenderer, displayedTitle, xOffsetValue, yOffsetValue,
                color, renderShadow.get());
        guiGraphics.pose().popMatrix();
    }

    protected boolean isTopAnchor(ConfigClient.AnchorPoint anchorPoint) {
        return anchorPoint == ConfigClient.AnchorPoint.TOP_LEFT ||
                anchorPoint == ConfigClient.AnchorPoint.TOP_CENTER ||
                anchorPoint == ConfigClient.AnchorPoint.TOP_RIGHT;
    }

    public void tick() {
        if (cooldownTimer > 0) {
            --cooldownTimer;
        }
        if (titleTimer > 0) {
            --titleTimer;
            if (titleTimer <= 0) {
                clearTimer();
            }
        }
    }

    public void displayTitle(Component titleText) {
        displayTitle(titleText, null);
    }

    public void displayTitle(Component titleText, Integer overrideColor) {
        displayedTitle = titleText;
        this.overrideColor = overrideColor;
        titleTimer = fadeInTime.get() + displayTime.get() + fadeOutTime.get();
    }

    public void clearTimer() {
        titleTimer = 0;
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

    public boolean isRendering() {
        return titleTimer > 0;
    }
}
