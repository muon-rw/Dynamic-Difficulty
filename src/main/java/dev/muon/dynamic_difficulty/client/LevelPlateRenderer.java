package dev.muon.dynamic_difficulty.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.muon.dynamic_difficulty.DynamicDifficulty;
import dev.muon.dynamic_difficulty.api.LevelingAPI;
import dev.muon.dynamic_difficulty.config.Config;
import javax.annotation.Nonnull;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Matrix4f;
import dev.muon.dynamic_difficulty.MobsLevelingEvents;

@EventBusSubscriber(modid = DynamicDifficulty.MODID, value = Dist.CLIENT)
public class LevelPlateRenderer {

  private static final float TEXT_SCALE = -0.025f; //todo:config



  @SubscribeEvent
  public static void renderEntityLevel(RenderNameTagEvent event) {
    if (!(event.getEntity() instanceof LivingEntity entity)) {
      return;
    }

    if (!MobsLevelingEvents.shouldShowName(entity)) {
      return;
    }

    PoseStack poseStack = event.getPoseStack();
    poseStack.pushPose();
    poseStack.translate(0d, entity.getBbHeight() + 0.5f, 0d);
    poseStack.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
    float textScale = Config.CLIENT.textScale.get();
    poseStack.scale(-textScale, -textScale, textScale);

    int entityLevel = ClientLevelCache.getLevel(entity);
    renderNameAndLevel(event, entity, entityLevel);

    event.getPoseStack().popPose();
    event.setResult(RenderNameTagEvent.Result.DENY);
  }

  private static void renderNameAndLevel(RenderNameTagEvent event, LivingEntity entity, int entityLevel) {
    Matrix4f pose = event.getPoseStack().last().pose();
    Font font = Minecraft.getInstance().font;
    MultiBufferSource buffer = event.getMultiBufferSource();
    Font.DisplayMode displayMode = getDisplayMode(entity);

    String nameFromEvent = event.getContent().getString();
    String levelText = " Level " + (entityLevel + 1);

    int nameWidth = font.width(nameFromEvent);
    int fullWidth = font.width(nameFromEvent + levelText);

    // Calculate text rendering positions (top-left for text)
    float textRenderX = -fullWidth / 2.0f + Config.CLIENT.levelTextShiftX.get();
    float textRenderY = Config.CLIENT.levelTextShiftY.get();

    // --- 1. Draw Background Quad ---
    float padding = 1.0f;
    float bgX1 = textRenderX - padding;
    float bgY1 = textRenderY - padding;
    float bgX2 = textRenderX + fullWidth + padding;
    float bgY2 = textRenderY + font.lineHeight + padding;

    float mcOptionsBackgroundOpacity = Minecraft.getInstance().options.getBackgroundOpacity(0.25F);
    int bgAlpha = (int)(mcOptionsBackgroundOpacity * 255.0F);

    RenderSystem.enableBlend();
    RenderSystem.defaultBlendFunc(); // Uses GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA
    RenderSystem.setShader(GameRenderer::getPositionColorShader);

    Tesselator tesselator = Tesselator.getInstance();
    BufferBuilder bufferBuilder = tesselator.getBuilder();

    bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
    bufferBuilder.vertex(pose, bgX1, bgY1, 0.0F).color(0, 0, 0, bgAlpha).endVertex();
    bufferBuilder.vertex(pose, bgX1, bgY2, 0.0F).color(0, 0, 0, bgAlpha).endVertex();
    bufferBuilder.vertex(pose, bgX2, bgY2, 0.0F).color(0, 0, 0, bgAlpha).endVertex();
    bufferBuilder.vertex(pose, bgX2, bgY1, 0.0F).color(0, 0, 0, bgAlpha).endVertex();
    tesselator.end(); // Uploads and draws the quad

    RenderSystem.disableBlend(); // Reset blend state

    // --- 2. Render Text (Name and Level) ---
    Component nameComponent = Component.literal(nameFromEvent);
    Component levelComponent = Component.literal(levelText);
    int packedLight = event.getPackedLight();

    int nameDisplayColor = 0xFFFFFF; // White for name
    Player player = Minecraft.getInstance().player;
    int levelDisplayColor = getLevelColor(player, entity);

    // Render Name (two-pass for SEE_THROUGH)
    // Pass 1 (either SEE_THROUGH or NORMAL)
    font.drawInBatch(nameComponent, textRenderX, textRenderY, nameDisplayColor, false, pose, buffer, displayMode, 0, packedLight);
    // Pass 2 (only if SEE_THROUGH, then draw again with NORMAL for solidity)
    if (displayMode == Font.DisplayMode.SEE_THROUGH) {
        font.drawInBatch(nameComponent, textRenderX, textRenderY, nameDisplayColor, false, pose, buffer, Font.DisplayMode.NORMAL, 0, packedLight);
    }

    // Render Level (two-pass for SEE_THROUGH)
    float levelTextActualX = textRenderX + nameWidth;
    // Pass 1
    font.drawInBatch(levelComponent, levelTextActualX, textRenderY, levelDisplayColor, false, pose, buffer, displayMode, 0, packedLight);
    // Pass 2
    if (displayMode == Font.DisplayMode.SEE_THROUGH) {
        font.drawInBatch(levelComponent, levelTextActualX, textRenderY, levelDisplayColor, false, pose, buffer, Font.DisplayMode.NORMAL, 0, packedLight);
    }
  }

  private static int getLevelColor(Player player, LivingEntity entity) {
    int playerLevel = ClientLevelCache.getLevel(player);
    int entityLevel = ClientLevelCache.getLevel(entity);

    // --- // Comparative Level Coloring
    if (playerLevel > 0) {
      int levelDifference = entityLevel - playerLevel;
      if (levelDifference > 10) return 0xFF0000; // red
      if (levelDifference > -5) return 0xFFFF00; // yellow
      return 0x00FF00; // green
    } else {

      // --- // Absolute Level Coloring (Player does not have a level)
      if (entityLevel < 8) return 0x00FF00; // green
      if (entityLevel <= 19) return 0xFFFF00; // yellow
      return 0xFF0000; // red
    }
  }

  @Nonnull
  private static Font.DisplayMode getDisplayMode(LivingEntity entity) {
    return !entity.isDiscrete() ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL;
  }

}
