package dev.muon.dynamic_difficulty.platform;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Platform-agnostic interface for network operations.
 * Implemented by platform-specific code.
 */
public interface NetworkHelper {
    
    /**
     * Send a packet to a specific player.
     */
    void sendToPlayer(ServerPlayer player, CustomPacketPayload payload);
    
    /**
     * Send a packet to all players tracking an entity.
     */
    void sendToPlayersTrackingEntity(Entity entity, CustomPacketPayload payload);
    
    /**
     * Send a packet to all players on the server.
     */
    void sendToAllPlayers(MinecraftServer server, CustomPacketPayload payload);
}
