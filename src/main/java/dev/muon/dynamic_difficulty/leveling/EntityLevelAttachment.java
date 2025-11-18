package dev.muon.dynamic_difficulty.leveling;

import com.mojang.serialization.Codec;
import dev.muon.dynamic_difficulty.DynamicDifficulty;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.ResourceLocation;

public class EntityLevelAttachment {
    public static final AttachmentType<Integer> LEVEL = AttachmentRegistry.createPersistent(
            ResourceLocation.fromNamespaceAndPath(DynamicDifficulty.MODID, "level"),
            Codec.INT
    );
}

