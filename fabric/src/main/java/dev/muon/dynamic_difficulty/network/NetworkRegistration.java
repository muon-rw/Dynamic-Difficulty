package dev.muon.dynamic_difficulty.network;

import dev.muon.dynamic_difficulty.network.message.LocationEntryPacket;
import dev.muon.dynamic_difficulty.network.message.SyncLevelingData;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/**
 * Fabric packet registration.
 */
public class NetworkRegistration {

    /**
     * Register payload types (server-side). Call from mod initializer.
     */
    public static void register() {
        PayloadTypeRegistry.playS2C().register(SyncLevelingData.TYPE, SyncLevelingData.CODEC);
        PayloadTypeRegistry.playS2C().register(LocationEntryPacket.TYPE, LocationEntryPacket.CODEC);
    }

    /**
     * Register client-side handlers. Call from client mod initializer.
     */
    @Environment(EnvType.CLIENT)
    public static void registerClient() {
        ClientPlayNetworking.registerGlobalReceiver(
                SyncLevelingData.TYPE,
                (payload, context) -> SyncLevelingData.handleOnClient(payload)
        );

        ClientPlayNetworking.registerGlobalReceiver(
                LocationEntryPacket.TYPE,
                (payload, context) -> LocationEntryPacket.handleOnClient(payload)
        );
    }
}
