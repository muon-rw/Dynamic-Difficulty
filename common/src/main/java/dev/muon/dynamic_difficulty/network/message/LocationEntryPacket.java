package dev.muon.dynamic_difficulty.network.message;

import dev.muon.dynamic_difficulty.DynamicDifficulty;
import dev.muon.dynamic_difficulty.client.render.TitleRenderManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Packet to notify clients when entering a location (structure, biome, dimension).
 * Platform-specific code handles registration and the handle() method adapter.
 */
public class LocationEntryPacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<LocationEntryPacket> TYPE =
            new CustomPacketPayload.Type<>(DynamicDifficulty.id("location_entry"));
    
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
    private final Identifier locationId;
    private final int locationBonus;
    private final int baseLevel;
    private final int playerBonus;
    private final int displayedLevel; // Final calculated level without player bonus (accounts for max level cap and bypassing bonuses)
    
    public LocationEntryPacket(EntryType entryType, @Nullable Identifier locationId, int locationBonus, int baseLevel, int playerBonus, int displayedLevel) {
        this.entryType = entryType;
        this.locationId = locationId;
        this.locationBonus = locationBonus;
        this.baseLevel = baseLevel;
        this.playerBonus = playerBonus;
        this.displayedLevel = displayedLevel;
    }
    
    public void write(FriendlyByteBuf buf) {
        buf.writeEnum(entryType);
        buf.writeBoolean(locationId != null);
        if (locationId != null) {
            buf.writeIdentifier(locationId);
        }
        buf.writeInt(locationBonus);
        buf.writeInt(baseLevel);
        buf.writeInt(playerBonus);
        buf.writeInt(displayedLevel);
    }
    
    public LocationEntryPacket(FriendlyByteBuf buf) {
        this.entryType = buf.readEnum(EntryType.class);
        if (buf.readBoolean()) {
            this.locationId = buf.readIdentifier();
        } else {
            this.locationId = null;
        }
        this.locationBonus = buf.readInt();
        this.baseLevel = buf.readInt();
        this.playerBonus = buf.readInt();
        this.displayedLevel = buf.readInt();
    }

    @Override
    public CustomPacketPayload.Type<LocationEntryPacket> type() {
        return TYPE;
    }
    
    /**
     * Client-side handling logic. Called by platform-specific packet handlers.
     * Must only be called on the client side.
     */
    public static void handleOnClient(LocationEntryPacket msg) {
        // Always process packets - level info should update even if locationBonus is 0
        // (dimensions affect base level, not bonuses)
        if (msg.locationId == null) {
            return;
        }
        
        TitleRenderManager manager = TitleRenderManager.getInstance();
        
        switch (msg.entryType) {
            case STRUCTURE:
                // Always update level info, but only show title if bonus > 0
                manager.displayStructureTitle(msg.locationId, msg.locationBonus, msg.baseLevel, msg.playerBonus, msg.displayedLevel);
                break;
            case BIOME:
                // Always update level info, but only show title if bonus > 0
                manager.displayBiomeTitle(msg.locationId, msg.locationBonus, msg.baseLevel, msg.playerBonus, msg.displayedLevel);
                break;
            case DIMENSION:
                // Dimensions affect base level through settings, not bonuses
                manager.displayDimensionTitle(msg.locationId, msg.baseLevel, msg.playerBonus, msg.displayedLevel);
                break;
        }
    }
}

