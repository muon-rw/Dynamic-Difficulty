package dev.muon.dynamic_difficulty.client;

import dev.muon.dynamic_difficulty.DynamicDifficulty;
import dev.muon.dynamic_difficulty.client.render.TitleRenderManager;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = DynamicDifficulty.MODID, value = Dist.CLIENT)
public class ClientEventHandler {
    
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        TitleRenderManager.getInstance().clientTick();
        if (Minecraft.getInstance().player != null) {
            TitleRenderManager.getInstance().playerTick(Minecraft.getInstance().player);
        }
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
    }
}
