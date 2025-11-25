package dev.muon.dynamic_difficulty.network.message;

import dev.muon.dynamic_difficulty.DynamicDifficulty;
import dev.muon.dynamic_difficulty.client.render.TitleRenderManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

public class LocationEntryPacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<LocationEntryPacket> TYPE =
            new CustomPacketPayload.Type<>(DynamicDifficulty.loc("location_entry"));
    
    public static final StreamCodec<FriendlyByteBuf, LocationEntryPacket> CODEC = CustomPacketPayload.codec(
        LocationEntryPacket::write,
        LocationEntryPacket::new
    );
    
    public enum EntryType {
        STRUCTURE,
        BIOME,
        DIMENSION
    }
    
    private final EntryType entryType;
    @Nullable
    private final ResourceLocation locationId;
    private final int locationBonus;
    private final int baseLevel;
    private final int playerBonus;
    
    public LocationEntryPacket(EntryType entryType, @Nullable ResourceLocation locationId, int locationBonus, int baseLevel, int playerBonus) {
        this.entryType = entryType;
        this.locationId = locationId;
        this.locationBonus = locationBonus;
        this.baseLevel = baseLevel;
        this.playerBonus = playerBonus;
    }
    
    public void write(FriendlyByteBuf buf) {
        buf.writeEnum(entryType);
        buf.writeBoolean(locationId != null);
        if (locationId != null) {
            buf.writeResourceLocation(locationId);
        }
        buf.writeInt(locationBonus);
        buf.writeInt(baseLevel);
        buf.writeInt(playerBonus);
    }
    
    public LocationEntryPacket(FriendlyByteBuf buf) {
        this.entryType = buf.readEnum(EntryType.class);
        if (buf.readBoolean()) {
            this.locationId = buf.readResourceLocation();
        } else {
            this.locationId = null;
        }
        this.locationBonus = buf.readInt();
        this.baseLevel = buf.readInt();
        this.playerBonus = buf.readInt();
    }
    
    @Override
    public CustomPacketPayload.Type<LocationEntryPacket> type() {
        return TYPE;
    }
    
    public static void handle(final LocationEntryPacket msg, final IPayloadContext context) {
        context.enqueueWork(() -> {
            handleOnClient(msg);
        });
    }
    
    @OnlyIn(Dist.CLIENT)
    private static void handleOnClient(LocationEntryPacket msg) {
        // Always process packets - level info should update even if locationBonus is 0
        // (dimensions affect base level, not bonuses)
        if (msg.locationId == null) {
            return;
        }
        
        TitleRenderManager manager = TitleRenderManager.getInstance();
        
        switch (msg.entryType) {
            case STRUCTURE:
                // Always update level info, but only show title if bonus > 0
                manager.displayStructureTitle(msg.locationId, msg.locationBonus, msg.baseLevel, msg.playerBonus);
                break;
            case BIOME:
                // Always update level info, but only show title if bonus > 0
                manager.displayBiomeTitle(msg.locationId, msg.locationBonus, msg.baseLevel, msg.playerBonus);
                break;
            case DIMENSION:
                // Dimensions affect base level through settings, not bonuses
                manager.displayDimensionTitle(msg.locationId, msg.baseLevel, msg.playerBonus);
                break;
        }
    }
}

