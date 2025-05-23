package dev.muon.dynamic_difficulty.mixin;

import javax.annotation.Nullable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
  @Accessor
  @Nullable
  Player getLastHurtByPlayer();

  @Accessor
  int getLastHurtByPlayerTime();
}
