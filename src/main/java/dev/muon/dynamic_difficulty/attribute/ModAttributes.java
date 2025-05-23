package dev.muon.dynamic_difficulty.attribute;

import dev.muon.dynamic_difficulty.DynamicDifficulty;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModAttributes {
  public static final DeferredRegister<Attribute> REGISTRY =
      DeferredRegister.create(ForgeRegistries.ATTRIBUTES, DynamicDifficulty.MODID);

  public static final RegistryObject<Attribute> PROJECTILE_DAMAGE_MULTIPLIER =
      rangedAttribute("monster", "projectile_damage_bonus", 1, 1, 1000);
  public static final RegistryObject<Attribute> EXPLOSION_DAMAGE_MULTIPLIER =
      rangedAttribute("monster", "explosion_damage_bonus", 1, 1, 1000);

  private static RegistryObject<Attribute> rangedAttribute(
      String category, String name, double defaultValue, double minValue, double maxValue) {
    return REGISTRY.register(
        category + "." + name,
        () ->
            new RangedAttribute(category + "." + name, defaultValue, minValue, maxValue)
                .setSyncable(true));
  }
}
