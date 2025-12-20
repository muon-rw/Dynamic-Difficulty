package dev.muon.dynamic_difficulty.client;

import dev.muon.dynamic_difficulty.DynamicDifficulty;
import dev.muon.dynamic_difficulty.api.LevelingAPI;
import dev.muon.dynamic_difficulty.client.render.TitleRenderManager;
import dev.muon.dynamic_difficulty.config.Config;
import dev.muon.dynamic_difficulty.network.message.SyncLevelingData;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.TriState;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

@EventBusSubscriber(modid = DynamicDifficulty.MODID, value = Dist.CLIENT)
public class ClientEventsNeoForge {

    // Disabled
     /**
     * RenderNameTagEvent.CanRender is broken in NeoForge - the event is created but not posted
     * @see dev.muon.dynamic_difficulty.mixin.client.EntityRendererMixin
     * */
    /*
    @SubscribeEvent
    public static void onRenderNameTag(RenderNameTagEvent.CanRender event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) {
            return;
        }
        
        // Early exit if entity shouldn't show level (matches Fabric mixin logic)
        if (!LevelingAPI.shouldShowLevel(entity)) {
            return;
        }
        
        // Check hiddenLevelEntities config (matches Fabric mixin logic)
        String entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        if (Config.CLIENT.hiddenLevelEntities.get().contains(entityId)) {
            event.setCanRender(TriState.FALSE);
            return;
        }
        
        // Use LevelPlateHandler.shouldShowName to determine visibility
        // This includes all the config checks (distance, behavior, line of sight, etc.)
        if (LevelPlateHandler.shouldShowName(entity)) {
            // Show name tag with level info
            event.setContent(LevelPlateHandler.modifyNameTag(event.getContent(), entity));
            event.setCanRender(TriState.TRUE);
        } else {
            // Hide name tag when shouldShowName returns false
            // This prevents vanilla name tags from showing when our config says not to
            event.setCanRender(TriState.FALSE);
        }
    }
    */
    
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        
        // Title rendering tick
        TitleRenderManager.getInstance().clientTick();
        if (minecraft.player != null) {
            TitleRenderManager.getInstance().playerTick(minecraft.player);
        }
        
        // Process pending sync data (for entities that weren't loaded when packet arrived)
        SyncLevelingData.processPendingData();
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
        SyncLevelingData.clearPendingData();
    }
    
    @SubscribeEvent
    public static void onEntityUnload(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide() && event.getEntity() instanceof LivingEntity) {
            SyncLevelingData.removePendingData(event.getEntity().getId());
        }
    }
    
    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            SyncLevelingData.clearPendingData();
        }
    }
}
