package dev.muon.dynamic_difficulty.settings;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.jetbrains.annotations.Nullable;

/** The biome or structure tier of the settings chain. */
public record LocationLevelingSettings(
        int startingLevel,
        int maxLevel,
        float levelsPerDistance,
        float levelsPerDepth,
        float levelsPerHeight,
        float levelsPerDay,
        float levelsPerLocalDifficulty,
        int randomLevelBonus,
        @Nullable Map<Attribute, AttributeModifier> attributeModifiers,
        @Nullable Double playerLevelMultiplier,
        @Nullable DimensionLevelingSettings.ApplyLevelBonuses applyLevelBonuses)
        implements LevelingSettings {

    /**
     * Empty fields inherit from the prior tier. {@code levelBonus} and {@code bypassesCap} are the
     * additive bonus, which stays separate from the settings chain.
     */
    public record RawSettings(
            Optional<Integer> startingLevel,
            Optional<Integer> maxLevel,
            Optional<Float> levelsPerDistance,
            Optional<Float> levelsPerDeepness,
            Optional<Float> levelsPerHeight,
            Optional<Float> levelsPerDay,
            Optional<Float> levelsPerLocalDifficulty,
            Optional<Integer> randomLevelBonus,
            Optional<Map<Attribute, AttributeModifier>> attributeModifiers,
            Optional<Double> playerLevelMultiplier,
            int levelBonus,
            boolean bypassesCap
    ) {
        /** {@code applyLevelBonuses} always comes from {@code prior}; biome and structure files cannot set it. */
        public LocationLevelingSettings resolve(LevelingSettings prior) {
            return new LocationLevelingSettings(
                    startingLevel.orElse(prior.startingLevel()),
                    maxLevel.orElse(prior.maxLevel()),
                    levelsPerDistance.orElse(prior.levelsPerDistance()),
                    levelsPerDeepness.orElse(prior.levelsPerDepth()),
                    levelsPerHeight.orElse(prior.levelsPerHeight()),
                    levelsPerDay.orElse(prior.levelsPerDay()),
                    levelsPerLocalDifficulty.orElse(prior.levelsPerLocalDifficulty()),
                    randomLevelBonus.orElse(prior.randomLevelBonus()),
                    attributeModifiers.orElse(prior.attributeModifiers()),
                    playerLevelMultiplier.orElse(prior.playerLevelMultiplier()),
                    prior.applyLevelBonuses()
            );
        }

        /** Each field keeps the higher value. The merged bonus pair is zeroed, since bonuses never merge. */
        public RawSettings merge(RawSettings other) {
            return new RawSettings(
                    maxOptional(startingLevel, other.startingLevel),
                    maxOptional(maxLevel, other.maxLevel),
                    maxOptional(levelsPerDistance, other.levelsPerDistance),
                    maxOptional(levelsPerDeepness, other.levelsPerDeepness),
                    maxOptional(levelsPerHeight, other.levelsPerHeight),
                    maxOptional(levelsPerDay, other.levelsPerDay),
                    maxOptional(levelsPerLocalDifficulty, other.levelsPerLocalDifficulty),
                    maxOptional(randomLevelBonus, other.randomLevelBonus),
                    mergeAttributeModifiers(attributeModifiers, other.attributeModifiers),
                    maxOptional(playerLevelMultiplier, other.playerLevelMultiplier),
                    0,
                    false
            );
        }
    }

    private static <N extends Comparable<N>> Optional<N> maxOptional(Optional<N> a, Optional<N> b) {
        if (a.isEmpty()) return b;
        if (b.isEmpty()) return a;
        return a.get().compareTo(b.get()) >= 0 ? a : b;
    }

    private static Optional<Map<Attribute, AttributeModifier>> mergeAttributeModifiers(
            Optional<Map<Attribute, AttributeModifier>> a,
            Optional<Map<Attribute, AttributeModifier>> b) {
        if (a.isEmpty()) return b;
        if (b.isEmpty()) return a;
        Map<Attribute, AttributeModifier> merged = new HashMap<>(a.get());
        b.get().forEach((attr, mod) -> merged.merge(attr, mod,
                (existing, incoming) -> existing.amount() >= incoming.amount() ? existing : incoming));
        return Optional.of(merged);
    }

    private static final Codec<Map<Attribute, AttributeModifier>> ATTRIBUTE_MODIFIERS_CODEC =
            AttributeModifierCodecs.mapCodec("location_leveling_bonus_");

    public static final Codec<RawSettings> STRUCTURE_RAW_CODEC = makeRawCodec(true);

    public static final Codec<RawSettings> BIOME_RAW_CODEC = makeRawCodec(false);

    private static Codec<RawSettings> makeRawCodec(boolean bypassesCapDefault) {
        return RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.optionalFieldOf("starting_level").forGetter(RawSettings::startingLevel),
                Codec.INT.optionalFieldOf("max_level").forGetter(RawSettings::maxLevel),
                Codec.FLOAT.optionalFieldOf("levels_per_distance").forGetter(RawSettings::levelsPerDistance),
                Codec.FLOAT.optionalFieldOf("levels_per_deepness").forGetter(RawSettings::levelsPerDeepness),
                Codec.FLOAT.optionalFieldOf("levels_per_height").forGetter(RawSettings::levelsPerHeight),
                Codec.FLOAT.optionalFieldOf("levels_per_day").forGetter(RawSettings::levelsPerDay),
                Codec.FLOAT.optionalFieldOf("levels_per_local_difficulty").forGetter(RawSettings::levelsPerLocalDifficulty),
                Codec.INT.optionalFieldOf("random_level_bonus").forGetter(RawSettings::randomLevelBonus),
                ATTRIBUTE_MODIFIERS_CODEC.optionalFieldOf("attribute_modifiers").forGetter(RawSettings::attributeModifiers),
                Codec.DOUBLE.optionalFieldOf("player_level_multiplier").forGetter(RawSettings::playerLevelMultiplier),
                Codec.INT.optionalFieldOf("level_bonus", 0).forGetter(RawSettings::levelBonus),
                Codec.BOOL.optionalFieldOf("bypasses_cap", bypassesCapDefault).forGetter(RawSettings::bypassesCap)
        ).apply(instance, RawSettings::new));
    }
}
