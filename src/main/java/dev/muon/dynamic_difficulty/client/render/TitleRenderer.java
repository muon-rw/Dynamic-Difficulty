package dev.muon.dynamic_difficulty.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.muon.dynamic_difficulty.DynamicDifficulty;
import dev.muon.dynamic_difficulty.config.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import org.jetbrains.annotations.Nullable;
import java.util.LinkedList;
import java.util.function.Predicate;

public class TitleRenderer<T> {
    public final LinkedList<T> recentEntries = new LinkedList<>();
    public Component displayedTitle = null;
    public Component displayedSubTitle = null;
    public int titleTimer = 0;

    // User-customizable text effects
    public final int maxRecentListSize;

    public TitleRenderer(int maxRecentListSize) {
        this.maxRecentListSize = maxRecentListSize;
    }

    private int getTitleTextColor() {
        String colorStr = Config.CLIENT.structureTitleTextColor.get();
        try {
            return (int) Long.parseLong(colorStr, 16);
        } catch (Exception e) {
            DynamicDifficulty.LOGGER.error("Text color {} is not a valid RGB color. Defaulting to white...", colorStr);
            return 0xFFFFFF;
        }
    }

    public void renderText(float partialTicks, GuiGraphics guiGraphics) {
        if (displayedTitle != null && titleTimer > 0) {
            int opacity = getOpacity(partialTicks);
            if (opacity > 8) {
                // Set up render system
                guiGraphics.pose().pushPose();
                if (Config.CLIENT.structureTitleCenterText.get()) {
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
        int fadeOutTicks = Config.CLIENT.structureTitleFadeOutTime.get();
        int displayTime = Config.CLIENT.structureTitleDisplayTime.get();
        int fadeInTicks = Config.CLIENT.structureTitleFadeInTime.get();

        // Fade in
        if (titleTimer > fadeOutTicks + displayTime) {
            float r = (float) (fadeInTicks + displayTime + fadeOutTicks) - age;
            opacity = (int) (r * 255.0F / (float) fadeInTicks);
        }

        // Fade out
        if (titleTimer <= fadeOutTicks) {
            opacity = (int) (age * 255.0F / (float) fadeOutTicks);
        }

        opacity = Mth.clamp(opacity, 0, 255);
        return opacity;
    }

    private void renderTitle(GuiGraphics guiGraphics, Font fontRenderer, int alpha) {
        // Render title
        guiGraphics.pose().pushPose();
        float textSize = Config.CLIENT.structureTitleTextSize.get().floatValue();
        guiGraphics.pose().scale(textSize, textSize, textSize);
        int titleWidth = fontRenderer.width(displayedTitle);

        int xOffset = Config.CLIENT.structureTitleCenterText.get()
            ? Config.CLIENT.structureTitleXOffset.get() + (-titleWidth / 2)
            : Config.CLIENT.structureTitleXOffset.get();

        guiGraphics.drawString(fontRenderer, displayedTitle, xOffset, Config.CLIENT.structureTitleYOffset.get(), 
            getTitleTextColor() | alpha, Config.CLIENT.structureTitleRenderShadow.get());
        guiGraphics.pose().popPose();
    }

    private void renderSubtitle(GuiGraphics guiGraphics, Font fontRenderer, int alpha) {
        if (displayedSubTitle != null) {
            guiGraphics.pose().pushPose();

            float subTitleTextSize = Config.CLIENT.structureTitleTextSize.get().floatValue() * Config.CLIENT.structureSubtitleScale.get();
            guiGraphics.pose().scale(subTitleTextSize, subTitleTextSize, subTitleTextSize);

            float scale = Config.CLIENT.structureSubtitleScale.get();
            int subtitleWidth = (int) (fontRenderer.width(displayedSubTitle) * scale);
            int subXOffset = (int) ((Config.CLIENT.structureTitleCenterText.get()
                    ? Config.CLIENT.structureTitleXOffset.get() + ((float) -subtitleWidth / 2)
                    : Config.CLIENT.structureTitleXOffset.get())
                    / scale);
            int subYOffset = (int) ((Config.CLIENT.structureTitleYOffset.get() + Config.CLIENT.structureSubtitleSpacing.get()) / scale);

            guiGraphics.drawString(fontRenderer, displayedSubTitle, subXOffset, subYOffset, 
                getTitleTextColor() | alpha, Config.CLIENT.structureTitleRenderShadow.get());
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
        titleTimer = Config.CLIENT.structureTitleFadeInTime.get() + Config.CLIENT.structureTitleDisplayTime.get() + Config.CLIENT.structureTitleFadeOutTime.get();
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
}
