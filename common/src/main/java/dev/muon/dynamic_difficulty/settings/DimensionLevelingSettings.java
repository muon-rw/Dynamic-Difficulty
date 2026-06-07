package dev.muon.dynamic_difficulty.settings;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.muon.dynamic_difficulty.config.Configs;

import java.util.Map;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.jetbrains.annotations.Nullable;

public record DimensionLevelingSettings(
        int startingLevel,
        int maxLevel,
        float levelsPerDistance,
        float levelsPerDepth,
        float levelsPerHeight,
        float levelsPerDay,
        float levelsPerLocalDifficulty,
        int randomLevelBonus,
        @Nullable BlockPos spawnPosOverride,
        int seaLevel,
        @Nullable Map<Attribute, AttributeModifier> attributeModifiers,
        @Nullable Double playerLevelMultiplier,
        @Nullable ApplyLevelBonuses applyLevelBonuses)
        implements LevelingSettings {

    public record RawSettings(
            Optional<Integer> startingLevel,
            Optional<Integer> maxLevel,
            Optional<Float> levelsPerDistance,
            Optional<Float> levelsPerDeepness,
            Optional<Float> levelsPerHeight,
            Optional<Float> levelsPerDay,
            Optional<Float> levelsPerLocalDifficulty,
            Optional<Integer> randomLevelBonus,
            Optional<BlockPos> spawnPosOverride,
            Optional<Integer> seaLevel,
            Optional<Map<Attribute, AttributeModifier>> attributeModifiers,
            Optional<Double> playerLevelMultiplier,
            Optional<ApplyLevelBonuses> applyLevelBonuses
    ) {
        public DimensionLevelingSettings resolve() {
            return new DimensionLevelingSettings(
                    startingLevel.orElseGet(() -> Configs.SYNC.startingLevel.get()),
                    maxLevel.orElseGet(() -> Configs.SYNC.maxLevel.get()),
                    levelsPerDistance.orElseGet(() -> Configs.SYNC.levelsPerDistance.get().floatValue()),
                    levelsPerDeepness.orElseGet(() -> Configs.SYNC.levelsPerDeepness.get().floatValue()),
                    levelsPerHeight.orElseGet(() -> Configs.SYNC.levelsPerHeight.get().floatValue()),
                    levelsPerDay.orElseGet(() -> Configs.SYNC.levelsPerDay.get().floatValue()),
                    levelsPerLocalDifficulty.orElseGet(() -> Configs.SYNC.levelsPerLocalDifficulty.get().floatValue()),
                    randomLevelBonus.orElseGet(() -> Configs.SYNC.randomLevelBonus.get()),
                    spawnPosOverride.orElse(null),
                    seaLevel.orElse(64),
                    attributeModifiers.orElse(null),
                    playerLevelMultiplier.orElse(null),
                    applyLevelBonuses.orElse(null)
            );
        }
    }

    public record ApplyLevelBonuses(
            boolean biome,
            boolean structure,
            boolean player
    ) {
        public static final Codec<ApplyLevelBonuses> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        Codec.BOOL.fieldOf("biome").forGetter(ApplyLevelBonuses::biome),
                        Codec.BOOL.fieldOf("structure").forGetter(ApplyLevelBonuses::structure),
                        Codec.BOOL.fieldOf("player").forGetter(ApplyLevelBonuses::player)
                ).apply(instance, ApplyLevelBonuses::new)
        );
    }

    private static final Codec<BlockPos> SPAWN_POS_OVERRIDE_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("x").forGetter(BlockPos::getX),
            Codec.INT.fieldOf("z").forGetter(BlockPos::getZ)
    ).apply(instance, (x, z) -> new BlockPos(x, 0, z)));

    private static final Codec<Map<Attribute, AttributeModifier>> ATTRIBUTE_MODIFIERS_CODEC =
            AttributeModifierCodecs.mapCodec("dimension_leveling_bonus_");

    public static final Codec<RawSettings> RAW_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("starting_level").forGetter(RawSettings::startingLevel),
            Codec.INT.optionalFieldOf("max_level").forGetter(RawSettings::maxLevel),
            Codec.FLOAT.optionalFieldOf("levels_per_distance").forGetter(RawSettings::levelsPerDistance),
            Codec.FLOAT.optionalFieldOf("levels_per_deepness").forGetter(RawSettings::levelsPerDeepness),
            Codec.FLOAT.optionalFieldOf("levels_per_height").forGetter(RawSettings::levelsPerHeight),
            Codec.FLOAT.optionalFieldOf("levels_per_day").forGetter(RawSettings::levelsPerDay),
            Codec.FLOAT.optionalFieldOf("levels_per_local_difficulty").forGetter(RawSettings::levelsPerLocalDifficulty),
            Codec.INT.optionalFieldOf("random_level_bonus").forGetter(RawSettings::randomLevelBonus),
            SPAWN_POS_OVERRIDE_CODEC.optionalFieldOf("spawn_pos_override").forGetter(RawSettings::spawnPosOverride),
            Codec.INT.optionalFieldOf("sea_level").forGetter(RawSettings::seaLevel),
            ATTRIBUTE_MODIFIERS_CODEC.optionalFieldOf("attribute_modifiers").forGetter(RawSettings::attributeModifiers),
            Codec.DOUBLE.optionalFieldOf("player_level_multiplier").forGetter(RawSettings::playerLevelMultiplier),
            ApplyLevelBonuses.CODEC.optionalFieldOf("apply_level_bonuses").forGetter(RawSettings::applyLevelBonuses)
    ).apply(instance, RawSettings::new));

    /**
     * Legacy codec that outputs resolved settings directly.
     * For backwards compatibility - parses raw then resolves.
     */
    public static final Codec<DimensionLevelingSettings> CODEC = RAW_CODEC.xmap(
            RawSettings::resolve,
            // Encoding: convert back to raw (all fields present)
            settings -> new RawSettings(
                    Optional.of(settings.startingLevel()),
                    Optional.of(settings.maxLevel()),
                    Optional.of(settings.levelsPerDistance()),
                    Optional.of(settings.levelsPerDepth()),
                    Optional.of(settings.levelsPerHeight()),
                    Optional.of(settings.levelsPerDay()),
                    Optional.of(settings.levelsPerLocalDifficulty()),
                    Optional.of(settings.randomLevelBonus()),
                    Optional.ofNullable(settings.spawnPosOverride()),
                    Optional.of(settings.seaLevel()),
                    Optional.ofNullable(settings.attributeModifiers()),
                    Optional.ofNullable(settings.playerLevelMultiplier()),
                    Optional.ofNullable(settings.applyLevelBonuses())
            )
    );

    public static DimensionLevelingSettings createDefault() {
        return new DimensionLevelingSettings(
                Configs.SYNC.startingLevel.get(),
                Configs.SYNC.maxLevel.get(),
                Configs.SYNC.levelsPerDistance.get().floatValue(),
                Configs.SYNC.levelsPerDeepness.get().floatValue(),
                Configs.SYNC.levelsPerHeight.get().floatValue(),
                Configs.SYNC.levelsPerDay.get().floatValue(),
                Configs.SYNC.levelsPerLocalDifficulty.get().floatValue(),
                Configs.SYNC.randomLevelBonus.get(),
                null,
                64,
                null,
                null,
                null
        );
    }
}
