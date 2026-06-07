package dev.muon.dynamic_difficulty.network;

import dev.muon.dynamic_difficulty.network.message.LocationEntry;
import dev.muon.dynamic_difficulty.network.message.SyncLevelingData;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public class NetworkRegistration {

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(SyncLevelingData.TYPE, SyncLevelingData.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(LocationEntry.TYPE, LocationEntry.CODEC);
    }

    @Environment(EnvType.CLIENT)
    public static void registerClient() {
        ClientPlayNetworking.registerGlobalReceiver(
                SyncLevelingData.TYPE,
                (payload, context) -> SyncLevelingData.handleOnClient(payload)
        );

        ClientPlayNetworking.registerGlobalReceiver(
                LocationEntry.TYPE,
                (payload, context) -> LocationEntry.handleOnClient(payload)
        );
    }
}
