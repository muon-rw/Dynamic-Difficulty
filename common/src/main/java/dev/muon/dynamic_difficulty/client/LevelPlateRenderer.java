package dev.muon.dynamic_difficulty.client;

import dev.muon.dynamic_difficulty.DynamicDifficulty;
import dev.muon.dynamic_difficulty.api.LevelingAPI;
import dev.muon.dynamic_difficulty.compat.dungeon_difficulty.DungeonDifficultyData;
import dev.muon.dynamic_difficulty.config.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Common level plate rendering logic.
 * Platform-specific code (NeoForge event / Fabric mixin) should call these methods.
 */
public class LevelPlateRenderer {

    /**
     * Modifies the name tag component to include level information.
     * Called from platform-specific event handlers/mixins.
     *
     * @param originalName The original name component
     * @param entity The entity being rendered
     * @return The modified name component with level info appended
     */
    public static Component modifyNameTag(Component originalName, LivingEntity entity) {
        if (!shouldShowName(entity)) {
            return originalName;
        }
        
        int entityLevel = LevelingAPI.getLevel(entity);

        MutableComponent fullDisplayName = originalName.copy();

        // Build level component
        MutableComponent levelComponent = Component.literal(" ")
                .append(Component.translatable("dynamic_difficulty.level", entityLevel))
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

        // Add Dungeon Difficulty info if available and enabled
        if (Config.CLIENT.showDungeonDifficultyInfo.get()) {
            DungeonDifficultyData ddData = DynamicDifficulty.getHelper().getDungeonDifficultyAttachmentHelper().getData(entity);
            if (ddData != null && !ddData.isEmpty()) {
                // Use Dungeon Difficulty's translation keys to get proper icons/formatting
                MutableComponent ddComponent = Component.literal(" [")
                        .append(Component.translatable(ddData.getTranslationKey()))
                        .append(Component.literal(" " + ddData.level() + "]"))
                        .withStyle(style -> style.withColor(getDungeonDifficultyColor(ddData)));
                levelComponent.append(ddComponent);
            }
        }

        fullDisplayName.append(levelComponent);
        return fullDisplayName;
    }

    /**
     * Get the color for level display based on player's level relative to the entity.
     * Uses ARGB format (0xAARRGGBB) for name tag rendering.
     * Public so other display systems (like Jade) can use the same color logic.
     *
     * @param player The player viewing the entity
     * @param entity The entity being viewed
     * @return ARGB color value
     */
    public static int getLevelColor(Player player, LivingEntity entity) {
        int playerLevel = player != null ? LevelingAPI.getLevel(player) : 0;
        int entityLevel = LevelingAPI.getLevel(entity);
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

    /**
     * Get the RGB color (without alpha) for level display.
     * Useful for systems that don't use ARGB format.
     *
     * @param player The player viewing the entity
     * @param entity The entity being viewed
     * @return RGB color value
     */
    public static int getLevelColorRGB(Player player, LivingEntity entity) {
        return getLevelColor(player, entity) & 0x00FFFFFF; // Strip alpha channel
    }

    public static int getTierColor(String tier) {
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

    /**
     * Get the color for Dungeon Difficulty display based on the difficulty level.
     * Higher levels get more intense colors.
     *
     * @param data The Dungeon Difficulty data
     * @return ARGB color value
     */
    public static int getDungeonDifficultyColor(DungeonDifficultyData data) {
        int level = data.level();
        // Color scale based on level (ARGB format)
        if (level <= 2) return 0xFF90EE90;      // Light green - easy
        if (level <= 4) return 0xFF3CB371;      // Medium sea green
        if (level <= 6) return 0xFFFFD700;      // Gold - medium
        if (level <= 8) return 0xFFFF8C00;      // Dark orange
        return 0xFFDC143C;                      // Crimson - hard
    }

    public static boolean shouldShowName(LivingEntity entity) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer clientPlayer = minecraft.player;

        // Early exit checks (cheap operations first)
        if (clientPlayer == null) return false;
        if (!Minecraft.renderNames()) return false;
        if (entity.isVehicle()) return false;
        if (entity == minecraft.getCameraEntity()) return false;
        if (entity.isInvisibleTo(clientPlayer)) return false;

        // Check distance before expensive line of sight check
        double maxDistSq = Config.CLIENT.renderDistance.get() * Config.CLIENT.renderDistance.get();
        if (entity.distanceToSqr(clientPlayer) > maxDistSq) {
            return false;
        }

        Config.RenderBehavior behavior = Config.CLIENT.renderBehavior.get();
        if (behavior == Config.RenderBehavior.NEVER) {
            return false;
        }

        // Check if level should be shown before expensive operations
        if (!LevelingAPI.shouldShowLevel(entity)) return false;

        // Line of sight check - expensive raycast, but only done after all cheap checks pass
        // Can be disabled via config for better performance
        if (Config.CLIENT.enableLineOfSightCheck.get()) {
            if (!clientPlayer.hasLineOfSight(entity)) {
                return false;
            }
        }

        // Final behavior checks
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
