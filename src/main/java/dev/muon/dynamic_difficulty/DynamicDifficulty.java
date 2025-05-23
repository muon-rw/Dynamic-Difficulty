package dev.muon.dynamic_difficulty;

import com.mojang.logging.LogUtils;
import dev.muon.dynamic_difficulty.config.Config;
import dev.muon.dynamic_difficulty.attribute.ModAttributes;
import dev.muon.dynamic_difficulty.network.NetworkDispatcher;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.event.entity.EntityAttributeModificationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@EventBusSubscriber(bus = Bus.MOD)
@Mod(DynamicDifficulty.MODID)
public class DynamicDifficulty {
  public static final Logger LOGGER = LogUtils.getLogger();
  public static final String MODID = "dynamic_difficulty";

  public static ResourceLocation loc(String path) {
    return new ResourceLocation(DynamicDifficulty.MODID, path);
  }

  public DynamicDifficulty(FMLJavaModLoadingContext context) {
    IEventBus modEventBus = context.getModEventBus();
    ModAttributes.REGISTRY.register(modEventBus);
    Config.register(context);
    NetworkDispatcher.init();
  }

  @SubscribeEvent
  public static void attachMobAttributes(EntityAttributeModificationEvent event) {
    event
        .getTypes()
        .forEach(
            entityType -> {
              event.add(entityType, ModAttributes.PROJECTILE_DAMAGE_MULTIPLIER.get());
              event.add(entityType, ModAttributes.EXPLOSION_DAMAGE_MULTIPLIER.get());
            });
  }
}
