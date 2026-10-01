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
            Optional<ApplyLevelBonuses> applyLevelBonuses,
            int priority
    ) {
        public DimensionLevelingSettings resolve(DimensionLevelingSettings prior) {
            return new DimensionLevelingSettings(
                    startingLevel.orElse(prior.startingLevel()),
                    maxLevel.orElse(prior.maxLevel()),
                    levelsPerDistance.orElse(prior.levelsPerDistance()),
                    levelsPerDeepness.orElse(prior.levelsPerDepth()),
                    levelsPerHeight.orElse(prior.levelsPerHeight()),
                    levelsPerDay.orElse(prior.levelsPerDay()),
                    levelsPerLocalDifficulty.orElse(prior.levelsPerLocalDifficulty()),
                    randomLevelBonus.orElse(prior.randomLevelBonus()),
                    spawnPosOverride.orElse(prior.spawnPosOverride()),
                    seaLevel.orElse(prior.seaLevel()),
                    attributeModifiers.orElse(prior.attributeModifiers()),
                    playerLevelMultiplier.orElse(prior.playerLevelMultiplier()),
                    applyLevelBonuses.orElse(prior.applyLevelBonuses())
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
            ApplyLevelBonuses.CODEC.optionalFieldOf("apply_level_bonuses").forGetter(RawSettings::applyLevelBonuses),
            Codec.INT.optionalFieldOf("priority", 0).forGetter(RawSettings::priority)
    ).apply(instance, RawSettings::new));

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
