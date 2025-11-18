package dev.muon.dynamic_difficulty.network.message;

import dev.muon.dynamic_difficulty.DynamicDifficulty;
import dev.muon.dynamic_difficulty.client.render.StructureTitleRenderManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import org.jetbrains.annotations.Nullable;

public class StructureEntryPacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<StructureEntryPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(DynamicDifficulty.MODID, "structure_entry"));
    
    public static final StreamCodec<FriendlyByteBuf, StructureEntryPacket> CODEC = CustomPacketPayload.codec(
        StructureEntryPacket::write,
        StructureEntryPacket::new
    );

    @Nullable
    private final ResourceLocation structureId;
    private final int structureBonus;
    private final int baseLevel;
    private final int playerBonus;

    public StructureEntryPacket(@Nullable ResourceLocation structureId, int structureBonus, int baseLevel, int playerBonus) {
        this.structureId = structureId;
        this.structureBonus = structureBonus;
        this.baseLevel = baseLevel;
        this.playerBonus = playerBonus;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(structureId != null);
        if (structureId != null) {
            buf.writeResourceLocation(structureId);
        }
        buf.writeInt(structureBonus);
        buf.writeInt(baseLevel);
        buf.writeInt(playerBonus);
    }

    public StructureEntryPacket(FriendlyByteBuf buf) {
        if (buf.readBoolean()) {
            this.structureId = buf.readResourceLocation();
        } else {
            this.structureId = null;
        }
        this.structureBonus = buf.readInt();
        this.baseLevel = buf.readInt();
        this.playerBonus = buf.readInt();
    }

    @Override
    public CustomPacketPayload.Type<StructureEntryPacket> type() {
        return TYPE;
    }

    public static void handle(final StructureEntryPacket msg, ClientPlayNetworking.Context context) {
        // Handle on network thread - Fabric's client networking handles thread safety
        handleOnClient(msg);
    }

    @Environment(EnvType.CLIENT)
    private static void handleOnClient(StructureEntryPacket msg) {
        if (msg.structureId != null && msg.structureBonus > 0) {
            StructureTitleRenderManager.getInstance().displayStructureTitle(
                    msg.structureId, 
                    msg.structureBonus, 
                    msg.baseLevel,
                    msg.playerBonus
            );
        }
    }
}
