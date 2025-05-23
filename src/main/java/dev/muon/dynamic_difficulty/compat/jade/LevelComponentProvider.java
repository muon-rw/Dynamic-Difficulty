package dev.muon.dynamic_difficulty.compat.jade;

import dev.muon.dynamic_difficulty.DynamicDifficulty;
import dev.muon.dynamic_difficulty.MobsLevelingEvents;
import dev.muon.dynamic_difficulty.api.LevelingAPI;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public enum LevelComponentProvider implements IEntityComponentProvider {
  INSTANCE;

  private static final ResourceLocation ID = new ResourceLocation(DynamicDifficulty.MODID, "level");

  @Override
  public ResourceLocation getUid() {
    return ID;
  }

  @Override
  public void appendTooltip(
      ITooltip tooltip, EntityAccessor entityAccessor, IPluginConfig pluginConfig) {
    Entity entity = entityAccessor.getEntity();
    boolean showLevel =
        LevelingAPI.hasLevel(entity) && LevelingAPI.shouldShowLevel(entity);
    if (!showLevel) return;
    int level = LevelingAPI.getLevel((LivingEntity) entity) + 1;
    tooltip.add(Component.translatable("jade.dynamic_difficulty.tooltip", level));
  }
}
