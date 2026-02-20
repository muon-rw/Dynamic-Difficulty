package dev.muon.dynamic_difficulty.client;

import dev.muon.dynamic_difficulty.DynamicDifficulty;
import dev.muon.dynamic_difficulty.api.LevelingAPI;
import dev.muon.dynamic_difficulty.config.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
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
public class LevelPlateHandler {

    /**
     * Whether level info should be injected into this entity's nameplate.
     * Controlled by injectLevelIntoMobs and injectLevelIntoPlayers config options.
     */
    public static boolean shouldInjectLevel(LivingEntity entity) {
        return entity instanceof Player
                ? Config.CLIENT.injectLevelIntoPlayers.get()
                : Config.CLIENT.injectLevelIntoMobs.get();
    }

    /**
     * Whether this mod should override the default nameplate visibility for this entity.
     * When false, vanilla decides when the nameplate is shown (sneaking, spectator, etc.).
     * Controlled by overrideMobNameplateVisibility and overridePlayerNameplateVisibility config options.
     */
    public static boolean shouldOverrideNameplateVisibility(LivingEntity entity) {
        return entity instanceof Player
                ? Config.CLIENT.overridePlayerNameplateVisibility.get()
                : Config.CLIENT.overrideMobNameplateVisibility.get();
    }

    /**
     * Modifies the name tag component to include level information.
     * Called from platform-specific event handlers/mixins.
     * Visibility is controlled by the caller; this only handles content injection.
     *
     * @param originalName The original name component
     * @param entity The entity being rendered
     * @return The modified name component with level info appended, or original if injection is disabled
     */
    public static Component modifyNameTag(Component originalName, LivingEntity entity) {
        if (!shouldInjectLevel(entity)) {
            return originalName;
        }
        
        int entityLevel = LevelingAPI.getLevel(entity);

        MutableComponent fullDisplayName = originalName.copy();

        // Build level component
        MutableComponent levelComponent = Component.literal(" ")
                .append(Component.translatable("dynamic_difficulty.level", entityLevel))
                .withStyle(style -> style.withColor(getLevelColor(Minecraft.getInstance().player, entity)));

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

        String entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        if (Config.CLIENT.hiddenLevelEntities.get().contains(entityId)) return false;

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
