package dev.muon.dynamic_difficulty.client;

import dev.muon.dynamic_difficulty.DynamicDifficulty;
import dev.muon.dynamic_difficulty.client.render.TitleRenderManager;
import dev.muon.dynamic_difficulty.network.NetworkRegistration;
import dev.muon.dynamic_difficulty.network.message.SyncLevelingData;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
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

            // Process pending sync data (for entities that weren't loaded when packet arrived)
            SyncLevelingData.processPendingData();
        });
        
        // HUD rendering for titles. 26.1 uses HudElementRegistry + HudElement
        HudElement titleHud = (guiGraphics, deltaTracker) -> {
            if (Minecraft.getInstance().player != null) {
                TitleRenderManager.getInstance().renderTitles(
                    guiGraphics,
                    deltaTracker.getGameTimeDeltaPartialTick(false)
                );
            }
        };
        HudElementRegistry.addLast(DynamicDifficulty.id("titles"), titleHud);
        
        // Entity unload - clean up caches
        ClientEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            if (entity instanceof LivingEntity) {
                SyncLevelingData.removePendingData(entity.getId());
            }
        });
        
        // Disconnect - clear all caches
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            TitleRenderManager.getInstance().clearCache();
            SyncLevelingData.clearPendingData();
        });
    }
} 