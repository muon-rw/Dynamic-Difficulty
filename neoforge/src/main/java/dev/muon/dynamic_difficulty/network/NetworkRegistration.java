package dev.muon.dynamic_difficulty.network;

import dev.muon.dynamic_difficulty.DynamicDifficulty;
import dev.muon.dynamic_difficulty.network.message.LocationEntryPacket;
import dev.muon.dynamic_difficulty.network.message.SyncLevelingData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * NeoForge packet registration.
 */
@EventBusSubscriber(modid = DynamicDifficulty.MODID)
public class NetworkRegistration {

    @SubscribeEvent
    public static void registerPackets(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(DynamicDifficulty.MODID)
                .versioned("1");

        registrar.playToClient(
                SyncLevelingData.TYPE,
                SyncLevelingData.CODEC,
                NetworkRegistration::handleSyncLevelingData
        );
        
        registrar.playToClient(
                LocationEntryPacket.TYPE,
                LocationEntryPacket.CODEC,
                NetworkRegistration::handleLocationEntry
        );
    }
    
    private static void handleSyncLevelingData(final SyncLevelingData msg, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.flow().isClientbound()) {
                SyncLevelingData.handleOnClient(msg);
            }
        });
    }
    
    private static void handleLocationEntry(final LocationEntryPacket msg, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.flow().isClientbound()) {
                LocationEntryPacket.handleOnClient(msg);
            }
        });
    }
}
