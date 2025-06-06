package dev.muon.dynamic_difficulty.client;

import dev.muon.dynamic_difficulty.DynamicDifficulty;
import dev.muon.dynamic_difficulty.api.LevelingAPI;
import dev.muon.dynamic_difficulty.config.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.common.util.TriState;

@EventBusSubscriber(modid = DynamicDifficulty.MODID, value = Dist.CLIENT)
public class LevelPlateRenderer {

  @SubscribeEvent
  public static void renderEntityLevel(RenderNameTagEvent event) {
    if (!(event.getEntity() instanceof LivingEntity entity)) {
      return;
    }

    if (shouldShowName(entity)) {
      Component originalName = event.getContent();
      int entityLevel = ClientLevelCache.getLevel(entity);
      
      MutableComponent fullDisplayName = originalName.copy();
      
      // Build level component
      MutableComponent levelComponent = Component.literal(" Level " + entityLevel)
          .withStyle(style -> style.withColor(getLevelColor(Minecraft.getInstance().player, entity)));
      
      // Add Apotheosis world tier if available and enabled
      if (Config.CLIENT.showApotheosisWorldTier.get()) {
        String worldTier = ApotheosisClientCache.getWorldTier(entity);
        if (worldTier != null) {
          MutableComponent tierComponent = Component.literal(" [" + worldTier + "]")
              .withStyle(style -> style.withColor(getTierColor(worldTier)));
          levelComponent.append(tierComponent);
        }
      }
      
      fullDisplayName.append(levelComponent);

      event.setContent(fullDisplayName);
      event.setCanRender(TriState.TRUE);
    }
  }

  private static int getLevelColor(Player player, LivingEntity entity) {
    int playerLevel = ClientLevelCache.getLevel(player);
    int entityLevel = ClientLevelCache.getLevel(entity);
    if (playerLevel > 0) {
      int levelDifference = entityLevel - playerLevel;
      if (levelDifference > 10) return 0xFFFF0000; // red (ARGB)
      if (levelDifference > -5) return 0xFFFFFF00; // yellow (ARGB)
      return 0xFF00FF00; // green (ARGB)
    } else {
      if (entityLevel < 8) return 0xFF00FF00; // green (ARGB)
      if (entityLevel <= 19) return 0xFFFFFF00; // yellow (ARGB)
      return 0xFFFF0000; // red (ARGB)
    }
  }
  
  private static int getTierColor(String tier) {
    // Color coding for Apotheosis tiers (ARGB format)
    return switch (tier) {
      case "Haven" -> 0xFF90EE90;     // Light green
      case "Frontier" -> 0xFF3CB371;   // Medium sea green
      case "Ascent" -> 0xFFFFD700;     // Gold
      case "Summit" -> 0xFFFF8C00;     // Dark orange
      case "Pinnacle" -> 0xFFDC143C;   // Crimson
      default -> 0xFFFFFFFF;          // White
    };
  }

  public static boolean shouldShowName(LivingEntity entity) {
    Minecraft minecraft = Minecraft.getInstance();
    LocalPlayer clientPlayer = minecraft.player;

    if (clientPlayer == null) return false;
    if (!Minecraft.renderNames()) return false;
    if (entity.isVehicle()) return false;
    if (entity == minecraft.getCameraEntity()) return false;

    if (!clientPlayer.hasLineOfSight(entity) || entity.isInvisibleTo(clientPlayer)) return false;

    if (!LevelingAPI.shouldShowLevel(entity)) return false;

    double maxDistSq = Config.CLIENT.renderDistance.get() * Config.CLIENT.renderDistance.get();
    if (entity.distanceToSqr(clientPlayer) > maxDistSq) {
      return false;
    }

    Config.RenderBehavior behavior = Config.CLIENT.renderBehavior.get();
    switch (behavior) {
      case NEVER:
        return false;
      case ALWAYS:
        return true;
      case LOOKING_AT:
        HitResult hitResult = minecraft.hitResult;
        if (hitResult != null && hitResult.getType() == HitResult.Type.ENTITY) {
          EntityHitResult entityHitResult = (EntityHitResult) hitResult;
          return entityHitResult.getEntity() == entity;
        }
        return false;
      default:
        return false;
    }
  }
}
