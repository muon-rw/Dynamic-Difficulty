package dev.muon.dynamic_difficulty;

import dev.muon.dynamic_difficulty.command.ModCommands;
import dev.muon.dynamic_difficulty.data.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * NeoForge-specific event handlers that delegate to common LevelingEvents.
 */
@EventBusSubscriber(modid = DynamicDifficulty.MODID)
public class LevelingEventsNeoForge {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        ModCommands.register(event.getDispatcher());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void applyLevelBonuses(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof LivingEntity living) {
            LevelingEvents.onEntityJoinLevel(living);
        }
    }

    @SubscribeEvent
    public static void adjustExperienceDrop(LivingExperienceDropEvent event) {
        int adjusted = LevelingEvents.adjustExperienceDrop(event.getEntity(), event.getDroppedExperience());
        event.setDroppedExperience(adjusted);
    }

    @SubscribeEvent
    public static void dropAdditionalLoot(LivingDropsEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel serverLevel)) {
            return;
        }
        LevelingEvents.dropAdditionalLoot(event.getEntity(), itemStack -> event.getEntity().spawnAtLocation(serverLevel, itemStack), event.getSource());
    }

    @SubscribeEvent
    public static void reloadSettings(AddServerReloadListenersEvent event) {
        event.addListener(DynamicDifficulty.loc("dimension_leveling_settings"), new DimensionsLevelingSettingsReloaderNeoForge());
        event.addListener(DynamicDifficulty.loc("dimension_tag_leveling_settings"), new DimensionTagLevelingSettingsReloaderNeoForge());
        event.addListener(DynamicDifficulty.loc("entity_leveling_settings"), new EntityLevelingSettingsReloaderNeoForge());
        event.addListener(DynamicDifficulty.loc("entity_tag_leveling_settings"), new EntityTagLevelingSettingsReloaderNeoForge());
        event.addListener(DynamicDifficulty.loc("structure_leveling_settings"), new StructureLevelingSettingsReloaderNeoForge());
        event.addListener(DynamicDifficulty.loc("structure_tag_leveling_settings"), new StructureTagLevelingSettingsReloaderNeoForge());
        event.addListener(DynamicDifficulty.loc("biome_leveling_settings"), new BiomeLevelingSettingsReloaderNeoForge());
        event.addListener(DynamicDifficulty.loc("biome_tag_leveling_settings"), new BiomeTagLevelingSettingsReloaderNeoForge());
    }

    @SubscribeEvent
    public static void onConfigReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getType() == net.neoforged.fml.config.ModConfig.Type.COMMON
                && event.getConfig().getModId().equals(DynamicDifficulty.MODID)) {
            LevelingEvents.onConfigReload();
        }
    }

    @SubscribeEvent
    public static void syncEntityLevel(PlayerEvent.StartTracking event) {
        if (!(event.getTarget() instanceof LivingEntity trackedEntity)) return;
        if (!(event.getEntity() instanceof ServerPlayer trackingPlayer)) return;
        LevelingEvents.onStartTracking(trackedEntity, trackingPlayer);
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!event.getEntity().level().isClientSide() && event.getEntity() instanceof ServerPlayer player) {
            LevelingEvents.onPlayerLoggedIn(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!event.getEntity().level().isClientSide() && event.getEntity() instanceof ServerPlayer player) {
            LevelingEvents.onPlayerLoggedOut(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!event.getEntity().level().isClientSide() && event.getEntity() instanceof ServerPlayer player) {
            LevelingEvents.onPlayerRespawn(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!event.getEntity().level().isClientSide() && event.getEntity() instanceof ServerPlayer player) {
            LevelingEvents.onPlayerChangeDimension(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (!event.getEntity().level().isClientSide() && event.getEntity() instanceof ServerPlayer player) {
            LevelingEvents.onPlayerClone(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            LevelingEvents.onPlayerDeath(player);
        }
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof LivingEntity living) {
            LevelingEvents.syncEntityOnJoin(living);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            LevelingEvents.onPlayerTick(player);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        LevelingEvents.onServerTick(event.getServer());
    }
}
