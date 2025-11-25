package dev.muon.dynamic_difficulty.compat;

import dev.muon.dynamic_difficulty.DynamicDifficulty;
import dev.muon.dynamic_difficulty.api.LevelingAPI;
import dev.muon.dynamic_difficulty.client.LevelPlateRenderer;
import dev.muon.dynamic_difficulty.config.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

@WailaPlugin
public enum LevelComponentProvider implements IEntityComponentProvider {
    INSTANCE;

    @Override
    public ResourceLocation getUid() {
        return DynamicDifficulty.loc("level");
    }

    @Override
    public void appendTooltip(
            ITooltip tooltip, EntityAccessor entityAccessor, IPluginConfig pluginConfig) {
        if (!Config.CLIENT.enableJadeIntegration.get()) {
            return;
        }
        
        Entity entity = entityAccessor.getEntity();
        if (entity instanceof LivingEntity living) {
            boolean showLevel =
                    LevelingAPI.hasLevel(living) && LevelingAPI.shouldShowLevel(living);
            if (!showLevel) return;
            
            int entityLevel = LevelingAPI.getLevel(living);
            int color = LevelPlateRenderer.getLevelColorRGB(Minecraft.getInstance().player, living);
            
            tooltip.add(Component.translatable("jade.dynamic_difficulty.tooltip", entityLevel)
                    .withStyle(style -> style.withColor(color)));
        }
    }
}