package dev.muon.dynamic_difficulty.mixin;

import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ChunkMap.class)
public interface ChunkMapInvoker {
    @Invoker("getVisibleChunkIfPresent")
    @Nullable
    ChunkHolder dynamic_difficulty$getVisibleChunkIfPresent(long chunkPos);
}
