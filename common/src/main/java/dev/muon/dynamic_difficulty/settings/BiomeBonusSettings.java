package dev.muon.dynamic_difficulty.settings;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record BiomeBonusSettings(
    int levelBonus,
    boolean bypassesCap
) {
    public static final Codec<BiomeBonusSettings> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.INT.fieldOf("level_bonus").forGetter(BiomeBonusSettings::levelBonus),
            Codec.BOOL.optionalFieldOf("bypasses_cap", false).forGetter(BiomeBonusSettings::bypassesCap)
        ).apply(instance, BiomeBonusSettings::new)
    );
}

