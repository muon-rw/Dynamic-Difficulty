package dev.muon.dynamic_difficulty.network.message;

import dev.muon.dynamic_difficulty.DynamicDifficulty;
import dev.muon.dynamic_difficulty.client.render.StructureTitleRenderManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import javax.annotation.Nullable;

public class StructureEntryPacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<StructureEntryPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(DynamicDifficulty.MODID, "structure_entry"));

    @Nullable
    private final ResourceLocation structureId;
    private final int levelBonus;
    private final int baseLevel;

    public StructureEntryPacket(@Nullable ResourceLocation structureId, int levelBonus, int baseLevel) {
        this.structureId = structureId;
        this.levelBonus = levelBonus;
        this.baseLevel = baseLevel;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(structureId != null);
        if (structureId != null) {
            buf.writeResourceLocation(structureId);
        }
        buf.writeInt(levelBonus);
        buf.writeInt(baseLevel);
    }

    public StructureEntryPacket(FriendlyByteBuf buf) {
        if (buf.readBoolean()) {
            this.structureId = buf.readResourceLocation();
        } else {
            this.structureId = null;
        }
        this.levelBonus = buf.readInt();
        this.baseLevel = buf.readInt();
    }

    @Override
    public CustomPacketPayload.Type<StructureEntryPacket> type() {
        return TYPE;
    }

    public static void handle(final StructureEntryPacket msg, final IPayloadContext context) {
        context.enqueueWork(() -> {
            handleOnClient(msg);
        });
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleOnClient(StructureEntryPacket msg) {
        if (msg.structureId != null && msg.levelBonus > 0) {
            StructureTitleRenderManager.getInstance().displayStructureTitle(
                    msg.structureId, 
                    msg.levelBonus, 
                    msg.baseLevel
            );
        }
    }
}
