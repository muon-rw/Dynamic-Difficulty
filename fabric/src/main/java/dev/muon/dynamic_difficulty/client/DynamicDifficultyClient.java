package dev.muon.dynamic_difficulty.client;

import dev.muon.dynamic_difficulty.client.render.TitleRenderManager;
import dev.muon.dynamic_difficulty.network.NetworkRegistration;
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
public class DynamicDifficultyClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // Register client-side packet handlers
        NetworkRegistration.registerClient();
        
        registerEventCallbacks();
    }

    private void registerEventCallbacks() {
        // Client tick - title rendering and cache cleanup
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            TitleRenderManager.getInstance().clientTick();
            if (client.player != null) {
                TitleRenderManager.getInstance().playerTick(client.player);
            }
            
            // Apotheosis cache cleanup
            if (client.level != null) {
                ApotheosisClientCache.onClientTick(client.level.getGameTime());
            }
        });
        
        // HUD rendering for titles
        HudRenderCallback.EVENT.register((guiGraphics, tickCounter) -> {
            if (Minecraft.getInstance().player != null) {
                TitleRenderManager.getInstance().renderTitles(
                    guiGraphics,
                    tickCounter.getGameTimeDeltaPartialTick(false)
                );
            }
        });
        
        // Entity unload - clean up Apotheosis cache
        ClientEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            if (entity instanceof LivingEntity) {
                ApotheosisClientCache.onEntityRemoved(entity.getId());
            }
        });
        
        // Disconnect - clear all caches
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            TitleRenderManager.getInstance().clearCache();
            ApotheosisClientCache.clearCache();
        });
    }
} 