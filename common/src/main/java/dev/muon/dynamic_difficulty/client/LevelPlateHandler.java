package dev.muon.dynamic_difficulty.client;

import dev.muon.dynamic_difficulty.DynamicDifficulty;
import dev.muon.dynamic_difficulty.api.LevelingAPI;
import dev.muon.dynamic_difficulty.config.ConfigClient;
import dev.muon.dynamic_difficulty.config.Configs;
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
     * Cached at class init: presence of Chronicles: Leveling. When loaded, it
     * owns the player nameplate end-to-end (its own renderer reads the same
     * level via our PlayerLevelProvider), so we suppress player injection on
     * this side regardless of the user's {@code injectLevelIntoPlayers}
     * setting. Mob injection is unaffected.
     *
     * <p>Cached because {@link #shouldInjectLevel(LivingEntity)} is on the
     * per-frame nameplate path and the answer doesn't change post-launch.
     */
    private static final boolean CHRONICLES_LEVELING_LOADED =
            DynamicDifficulty.isModLoaded("chronicles_leveling");

    /** Controlled by injectLevelIntoMobs and injectLevelIntoPlayers config options. */
    public static boolean shouldInjectLevel(LivingEntity entity) {
        if (entity instanceof Player) {
            if (CHRONICLES_LEVELING_LOADED) return false;
            return Configs.CLIENT.injectLevelIntoPlayers.get();
        }
        return Configs.CLIENT.injectLevelIntoMobs.get();
    }

    /** When false, vanilla decides when the nameplate is shown (sneaking, spectator, etc.). */
    public static boolean shouldOverrideNameplateVisibility(LivingEntity entity) {
        return entity instanceof Player
                ? Configs.CLIENT.overridePlayerNameplateVisibility.get()
                : Configs.CLIENT.overrideMobNameplateVisibility.get();
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

        MutableComponent levelComponent = Component.literal(" ")
                .append(Component.translatable("dynamic_difficulty.level", entityLevel))
                .withStyle(style -> style.withColor(getLevelColor(Minecraft.getInstance().player, entity)));

        fullDisplayName.append(levelComponent);
        return fullDisplayName;
    }

    /**
     * Returns ARGB format (0xAARRGGBB) for name tag rendering.
     * Public so other display systems (like Jade) can use the same color logic.
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

        if (!passesCheapGuards(entity, clientPlayer, minecraft)) return false;
        if (!isWithinRenderDistance(entity, clientPlayer)) return false;

        ConfigClient.RenderBehavior behavior = Configs.CLIENT.renderBehavior.get();
        if (behavior == ConfigClient.RenderBehavior.NEVER) {
            return false;
        }

        if (!passesLevelAndHiddenFilters(entity)) return false;
        if (!hasLineOfSight(clientPlayer, entity)) return false;

        return matchesRenderBehavior(behavior, minecraft, entity);
    }

    private static boolean passesCheapGuards(LivingEntity entity, LocalPlayer clientPlayer, Minecraft minecraft) {
        if (clientPlayer == null) return false;
        if (!Minecraft.renderNames()) return false;
        if (entity.isVehicle()) return false;
        if (entity == minecraft.getCameraEntity()) return false;
        if (entity.isInvisibleTo(clientPlayer)) return false;
        return true;
    }

    private static boolean isWithinRenderDistance(LivingEntity entity, LocalPlayer clientPlayer) {
        double maxDistSq = Configs.CLIENT.renderDistance.get() * Configs.CLIENT.renderDistance.get();
        return entity.distanceToSqr(clientPlayer) <= maxDistSq;
    }

    private static boolean passesLevelAndHiddenFilters(LivingEntity entity) {
        if (!LevelingAPI.shouldShowLevel(entity)) return false;

        String entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        if (Configs.CLIENT.hiddenLevelEntities.get().contains(entityId)) return false;
        return true;
    }

    private static boolean hasLineOfSight(LocalPlayer clientPlayer, LivingEntity entity) {
        if (Configs.CLIENT.enableLineOfSightCheck.get()) {
            return clientPlayer.hasLineOfSight(entity);
        }
        return true;
    }

    private static boolean matchesRenderBehavior(ConfigClient.RenderBehavior behavior, Minecraft minecraft, LivingEntity entity) {
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
