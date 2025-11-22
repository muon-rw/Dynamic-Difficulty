package dev.muon.dynamic_difficulty.settings;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record StructureBonusSettings(int levelBonus, boolean bypassesCap) {
  public static final Codec<StructureBonusSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
      Codec.INT.fieldOf("level_bonus").forGetter(StructureBonusSettings::levelBonus),
      Codec.BOOL.optionalFieldOf("bypasses_cap", true).forGetter(StructureBonusSettings::bypassesCap)
  ).apply(instance, StructureBonusSettings::new));
}

