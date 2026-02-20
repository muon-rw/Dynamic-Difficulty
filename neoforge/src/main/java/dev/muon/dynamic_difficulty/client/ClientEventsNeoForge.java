package dev.muon.dynamic_difficulty.client;

import dev.muon.dynamic_difficulty.DynamicDifficulty;
import dev.muon.dynamic_difficulty.client.render.TitleRenderManager;
import dev.muon.dynamic_difficulty.config.Config;
import dev.muon.dynamic_difficulty.network.message.SyncDungeonDifficultyData;
import dev.muon.dynamic_difficulty.network.message.SyncLevelingData;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

@EventBusSubscriber(modid = DynamicDifficulty.MODID, value = Dist.CLIENT)
public class ClientEventsNeoForge {
    
    @SubscribeEvent
    public static void onRenderNameTag(RenderNameTagEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) {
            return;
        }
        
        if (LevelPlateHandler.shouldInjectLevel(entity)) {
            event.setContent(LevelPlateHandler.modifyNameTag(event.getContent(), entity));
        }
        if (LevelPlateHandler.shouldOverrideNameplateVisibility(entity)) {
            event.setCanRender(LevelPlateHandler.shouldShowName(entity) ? TriState.TRUE : TriState.FALSE);
        }
    }
    
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!Config.CLIENT_SPEC.isLoaded()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        
        // Title rendering tick
        TitleRenderManager.getInstance().clientTick();
        if (minecraft.player != null) {
            TitleRenderManager.getInstance().playerTick(minecraft.player);
        }
        
        // Apotheosis cache cleanup
        if (minecraft.level != null) {
            ApotheosisClientCache.onClientTick(minecraft.level.getGameTime());
        }
        
        // Process pending sync data (for entities that weren't loaded when packet arrived)
        SyncLevelingData.processPendingData();
        SyncDungeonDifficultyData.processPendingData();
    }
    
    @SubscribeEvent
    public static void onRenderGui(RenderGuiLayerEvent.Post event) {
        if (event.getName().equals(VanillaGuiLayers.BOSS_OVERLAY)) {
            if (Minecraft.getInstance().player != null) {
                TitleRenderManager.getInstance().renderTitles(
                    event.getGuiGraphics(), 
                    event.getPartialTick().getGameTimeDeltaPartialTick(false)
                );
            }
        }
    }
    
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        TitleRenderManager.getInstance().clearCache();
        ApotheosisClientCache.clearCache();
        SyncLevelingData.clearPendingData();
        SyncDungeonDifficultyData.clearPendingData();
    }
    
    @SubscribeEvent
    public static void onEntityUnload(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide() && event.getEntity() instanceof LivingEntity) {
            ApotheosisClientCache.onEntityRemoved(event.getEntity().getId());
            SyncLevelingData.removePendingData(event.getEntity().getId());
            SyncDungeonDifficultyData.removePendingData(event.getEntity().getId());
        }
    }
    
    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            ApotheosisClientCache.clearCache();
            SyncLevelingData.clearPendingData();
            SyncDungeonDifficultyData.clearPendingData();
        }
    }
}
