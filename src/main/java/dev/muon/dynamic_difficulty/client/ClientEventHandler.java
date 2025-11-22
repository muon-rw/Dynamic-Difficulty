package dev.muon.dynamic_difficulty.client;

import dev.muon.dynamic_difficulty.client.render.TitleRenderManager;
import dev.muon.dynamic_difficulty.config.Config;
import dev.muon.dynamic_difficulty.network.NetworkDispatcher;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;

@Environment(EnvType.CLIENT)
public class ClientEventHandler implements ClientModInitializer {
    
    @Override
    public void onInitializeClient() {
        // Register client config
        Config.registerClient();
        
        // Register networking handlers
        NetworkDispatcher.registerClient();
        
        // Register client-side event handlers
        ApotheosisClientCache.register();
        
        // Register entity unload handler for ClientLevelCache
        ClientEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            if (entity instanceof LivingEntity) {
                ClientLevelCache.removeEntity(entity.getId());
            }
        });
        
        // Clear all caches on disconnect to prevent memory leaks and stale data
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClientLevelCache.clearCache();
            ApotheosisClientCache.clearCache();
            TitleRenderManager.getInstance().clearCache();
        });
        
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            TitleRenderManager.getInstance().clientTick();
            if (client.player != null) {
                TitleRenderManager.getInstance().playerTick(client.player);
            }
        });
        
        HudRenderCallback.EVENT.register((guiGraphics, deltaTracker) -> {
            if (Minecraft.getInstance().player != null) {
                TitleRenderManager.getInstance().renderTitles(guiGraphics, deltaTracker.getGameTimeDeltaTicks());
            }
        });
    }
}
