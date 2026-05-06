package dev.muon.dynamic_difficulty.settings;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.jetbrains.annotations.Nullable;

/**
 * Resolved structure or biome leveling settings.
 *
 * <p>Combines two payloads:
 * <ul>
 *   <li>The standard {@link LevelingSettings} fields, which participate in the resolution chain
 *       {@code dimension → biome → structure → entity} as overrides.</li>
 *   <li>An additive {@link #levelBonus()}/{@link #bypassesCap()} pair preserved from the legacy
 *       structure/biome bonus model. Applied on top of the resolved chain via the cap-bypass split
 *       in {@code LevelingSystem.createLevelForEntity()}.</li>
 * </ul>
 *
 * <p>{@link #applyLevelBonuses()} is exposed for {@link LevelingSettings} compatibility but is a
 * passthrough from the prior tier; biome and structure JSON cannot set it.
 */
public record LocationLevelingSettings(
        int startingLevel,
        int maxLevel,
        float levelsPerDistance,
        float levelsPerDeepness,
        float levelsPerHeight,
        float levelsPerDay,
        float levelsPerLocalDifficulty,
        int randomLevelBonus,
        @Nullable Map<Attribute, AttributeModifier> attributeModifiers,
        @Nullable Double playerLevelMultiplier,
        @Nullable DimensionLevelingSettings.ApplyLevelBonuses applyLevelBonuses,
        int levelBonus,
        boolean bypassesCap)
        implements LevelingSettings {

    /**
     * Raw settings as parsed from JSON. All override fields are Optional ("omit = inherit prior tier").
     * The {@code levelBonus}/{@code bypassesCap} pair carries the additive bonus payload;
     * {@code levelBonus} of 0 means "no bonus from this entry".
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
        /**
         * Resolve into a {@link LocationLevelingSettings} by inheriting any unset field from
         * {@code prior}. {@code applyLevelBonuses} always passes through from {@code prior};
         * biome/structure cannot override it.
         */
        public LocationLevelingSettings resolve(LevelingSettings prior) {
            return new LocationLevelingSettings(
                    startingLevel.orElse(prior.startingLevel()),
                    maxLevel.orElse(prior.maxLevel()),
                    levelsPerDistance.orElse(prior.levelsPerDistance()),
                    levelsPerDeepness.orElse(prior.levelsPerDeepness()),
                    levelsPerHeight.orElse(prior.levelsPerHeight()),
                    levelsPerDay.orElse(prior.levelsPerDay()),
                    levelsPerLocalDifficulty.orElse(prior.levelsPerLocalDifficulty()),
                    randomLevelBonus.orElse(prior.randomLevelBonus()),
                    attributeModifiers.orElse(prior.attributeModifiers()),
                    playerLevelMultiplier.orElse(prior.playerLevelMultiplier()),
                    prior.applyLevelBonuses(),
                    levelBonus,
                    bypassesCap
            );
        }

        /**
         * Merge two raw settings via per-field max for the override fields. Empty values yield to
         * present values; both present takes the higher number.
         *
         * <p>The merged result's bonus pair ({@code levelBonus}, {@code bypassesCap}) is
         * deliberately zeroed; bonus math uses the bucket-max model over individual entries via
         * {@code LocationBonusUtils}, not the merged result. Reading {@code levelBonus()} on a
         * merged settings always returns {@code 0} so callers can't be misled.
         */
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

    /**
     * Per-attribute, modifier with the higher {@code amount} wins. Operation/id of the winning
     * modifier are kept verbatim.
     */
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

    // === Codecs ===

    private static final Codec<Map<Attribute, AttributeModifier>> ATTRIBUTE_MODIFIERS_CODEC =
            AttributeModifierCodecs.mapCodec("location_leveling_bonus_");

    /**
     * Codec for structure leveling settings. {@code bypasses_cap} defaults to {@code true} when omitted.
     */
    public static final Codec<RawSettings> STRUCTURE_RAW_CODEC = makeRawCodec(true);

    /**
     * Codec for biome leveling settings. {@code bypasses_cap} defaults to {@code false} when omitted.
     */
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
