package dev.muon.dynamic_difficulty.compat.jade;

import net.minecraft.world.entity.LivingEntity;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

@WailaPlugin
public class DynamicDifficultyComponentProvider implements IWailaPlugin {
  @Override
  public void registerClient(IWailaClientRegistration registration) {
    registration.registerEntityComponent(LevelComponentProvider.INSTANCE, LivingEntity.class);
  }
}
