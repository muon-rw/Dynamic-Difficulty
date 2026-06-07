package dev.muon.dynamic_difficulty;

import com.mojang.serialization.Codec;
import dev.muon.dynamic_difficulty.DynamicDifficulty;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public class EntityLevelAttachmentNeoForge {
    public static final DeferredRegister<AttachmentType<?>> REGISTRY =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, DynamicDifficulty.MODID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> LEVEL = REGISTRY.register(
            "level",
            () -> AttachmentType.builder(() -> 1)
                    .serialize(Codec.INT.fieldOf("level"))
                    .sync(ByteBufCodecs.VAR_INT.cast())
                    .build()
    );
}
