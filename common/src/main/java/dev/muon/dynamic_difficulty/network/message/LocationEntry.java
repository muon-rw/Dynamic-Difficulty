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
 */
public class LocationEntry implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<LocationEntry> TYPE =
            new CustomPacketPayload.Type<>(DynamicDifficulty.id("location_entry"));

    public static final StreamCodec<FriendlyByteBuf, LocationEntry> CODEC = CustomPacketPayload.codec(
        LocationEntry::write,
        LocationEntry::new
    );

    public enum EntryType {
        STRUCTURE,
        BIOME,
        DIMENSION
    }

    private final EntryType entryType;
    @Nullable
    private final Identifier locationId;
    /** Bonus contribution that respects the max-level cap. */
    private final int nonBypassingBonus;
    /** Bonus contribution that bypasses the max-level cap. */
    private final int bypassingBonus;
    private final int baseLevel;
    private final int playerBonus;
    /** Final calculated level without player bonus (accounts for max level cap and bypassing bonuses). */
    private final int displayedLevel;
    /** Effective max-level cap at this position (after biome/structure overrides). 0 = unlimited. */
    private final int maxLevel;

    public LocationEntry(EntryType entryType, @Nullable Identifier locationId,
                         int nonBypassingBonus, int bypassingBonus,
                         int baseLevel, int playerBonus, int displayedLevel, int maxLevel) {
        this.entryType = entryType;
        this.locationId = locationId;
        this.nonBypassingBonus = nonBypassingBonus;
        this.bypassingBonus = bypassingBonus;
        this.baseLevel = baseLevel;
        this.playerBonus = playerBonus;
        this.displayedLevel = displayedLevel;
        this.maxLevel = maxLevel;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeEnum(entryType);
        buf.writeBoolean(locationId != null);
        if (locationId != null) {
            buf.writeIdentifier(locationId);
        }
        buf.writeInt(nonBypassingBonus);
        buf.writeInt(bypassingBonus);
        buf.writeInt(baseLevel);
        buf.writeInt(playerBonus);
        buf.writeInt(displayedLevel);
        buf.writeInt(maxLevel);
    }

    public LocationEntry(FriendlyByteBuf buf) {
        this.entryType = buf.readEnum(EntryType.class);
        if (buf.readBoolean()) {
            this.locationId = buf.readIdentifier();
        } else {
            this.locationId = null;
        }
        this.nonBypassingBonus = buf.readInt();
        this.bypassingBonus = buf.readInt();
        this.baseLevel = buf.readInt();
        this.playerBonus = buf.readInt();
        this.displayedLevel = buf.readInt();
        this.maxLevel = buf.readInt();
    }

    public int totalBonus() {
        return nonBypassingBonus + bypassingBonus;
    }

    @Override
    public CustomPacketPayload.Type<LocationEntry> type() {
        return TYPE;
    }

    /** Must only be called on the client side. */
    public static void handleOnClient(LocationEntry msg) {
        // Process even when locationBonus is 0; dimensions affect base level, not bonuses.
        if (msg.locationId == null) {
            return;
        }

        TitleRenderManager manager = TitleRenderManager.getInstance();
        int totalBonus = msg.totalBonus();

        switch (msg.entryType) {
            case STRUCTURE:
                manager.displayStructureTitle(msg.locationId, totalBonus, msg.baseLevel, msg.playerBonus, msg.displayedLevel);
                break;
            case BIOME:
                manager.displayBiomeTitle(msg.locationId, totalBonus, msg.baseLevel, msg.playerBonus, msg.displayedLevel);
                break;
            case DIMENSION:
                manager.displayDimensionTitle(msg.locationId, msg.baseLevel, msg.playerBonus, msg.displayedLevel);
                break;
        }
    }
}
