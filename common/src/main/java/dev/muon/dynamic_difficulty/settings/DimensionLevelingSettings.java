package dev.muon.dynamic_difficulty.settings;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.muon.dynamic_difficulty.DynamicDifficulty;
import dev.muon.dynamic_difficulty.config.Config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.jetbrains.annotations.Nullable;

/**
 * Resolved dimension leveling settings. All fields have values (no nulls for primitive-like fields).
 * Created by resolving RawDimensionSettings with config defaults.
 */
public record DimensionLevelingSettings(
        int startingLevel,
        int maxLevel,
        float levelsPerDistance,
        float levelsPerDeepness,
        float levelsPerHeight,
        float levelsPerDay,
        float levelsPerLocalDifficulty,
        int randomLevelBonus,
        @Nullable BlockPos spawnPosOverride,
        int seaLevel,
        @Nullable Map<Attribute, AttributeModifier> attributeModifiers)
        implements LevelingSettings {

    /**
     * Raw settings as parsed from JSON. All fields are Optional to support "omit = use config default".
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
            Optional<BlockPos> spawnPosOverride,
            Optional<Integer> seaLevel,
            Optional<Map<Attribute, AttributeModifier>> attributeModifiers
    ) {
        /**
         * Resolve raw settings into final settings, using config defaults for any omitted fields.
         */
        public DimensionLevelingSettings resolve() {
            return new DimensionLevelingSettings(
                    startingLevel.orElseGet(() -> Config.COMMON.startingLevel.get()),
                    maxLevel.orElseGet(() -> Config.COMMON.maxLevel.get()),
                    levelsPerDistance.orElseGet(() -> Config.COMMON.levelsPerDistance.get().floatValue()),
                    levelsPerDeepness.orElseGet(() -> Config.COMMON.levelsPerDeepness.get().floatValue()),
                    levelsPerHeight.orElseGet(() -> Config.COMMON.levelsPerHeight.get().floatValue()),
                    levelsPerDay.orElseGet(() -> Config.COMMON.levelsPerDay.get().floatValue()),
                    levelsPerLocalDifficulty.orElseGet(() -> Config.COMMON.levelsPerLocalDifficulty.get().floatValue()),
                    randomLevelBonus.orElseGet(() -> Config.COMMON.randomLevelBonus.get()),
                    spawnPosOverride.orElse(null),
                    seaLevel.orElse(64),
                    attributeModifiers.orElse(null)
            );
        }
    }

    // === Codecs ===

    private record AttributeModifierEntry(ResourceLocation attribute, double amount, String operation) {
    }

    private static AttributeModifier.Operation parseOperation(String operationStr) {
        for (AttributeModifier.Operation op : AttributeModifier.Operation.values()) {
            if (op.getSerializedName().equals(operationStr)) {
                return op;
            }
        }
        DynamicDifficulty.LOGGER.warn("Invalid operation '{}'. Defaulting to add_value.", operationStr);
        return AttributeModifier.Operation.ADD_VALUE;
    }

    // Codec that accepts both string (enum name) and int (legacy) for backwards compatibility
    private static final Codec<String> OPERATION_CODEC = Codec.either(Codec.STRING, Codec.INT)
            .xmap(
                    either -> either.map(
                            str -> str,
                            num -> {
                                DynamicDifficulty.LOGGER.warn("Numeric operation ID {} is deprecated. Use enum names instead.", num);
                                return AttributeModifier.Operation.BY_ID.apply(num).getSerializedName();
                            }
                    ),
                    Either::left
            );

    private static final Codec<AttributeModifierEntry> ATTRIBUTE_MODIFIER_ENTRY_CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    ResourceLocation.CODEC.fieldOf("attribute").forGetter(AttributeModifierEntry::attribute),
                    Codec.DOUBLE.fieldOf("amount").forGetter(AttributeModifierEntry::amount),
                    OPERATION_CODEC.fieldOf("operation").forGetter(AttributeModifierEntry::operation)
            ).apply(instance, AttributeModifierEntry::new));

    private static final Codec<BlockPos> SPAWN_POS_OVERRIDE_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("x").forGetter(BlockPos::getX),
            Codec.INT.fieldOf("z").forGetter(BlockPos::getZ)
    ).apply(instance, (x, z) -> new BlockPos(x, 0, z)));

    private static final Codec<Map<Attribute, AttributeModifier>> ATTRIBUTE_MODIFIERS_CODEC =
            Codec.list(ATTRIBUTE_MODIFIER_ENTRY_CODEC)
                    .xmap(
                            list -> {
                                Map<Attribute, AttributeModifier> map = new HashMap<>();
                                for (AttributeModifierEntry entry : list) {
                                    Attribute attribute = BuiltInRegistries.ATTRIBUTE.get(entry.attribute());
                                    if (attribute != null) {
                                        AttributeModifier.Operation operation = parseOperation(entry.operation());
                                        ResourceLocation modifierId = DynamicDifficulty.loc(
                                                "dimension_leveling_bonus_" + entry.attribute().getPath().replace("/", "_"));
                                        map.put(attribute, new AttributeModifier(modifierId, entry.amount(), operation));
                                    }
                                }
                                return map;
                            },
                            map -> {
                                List<AttributeModifierEntry> list = new ArrayList<>();
                                map.forEach((attr, modifier) -> {
                                    ResourceLocation attrId = BuiltInRegistries.ATTRIBUTE.getKey(attr);
                                    list.add(new AttributeModifierEntry(attrId, modifier.amount(), modifier.operation().getSerializedName()));
                                });
                                return list;
                            }
                    );

    /**
     * Codec for parsing raw settings from JSON. ALL fields are optional.
     */
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
            ATTRIBUTE_MODIFIERS_CODEC.optionalFieldOf("attribute_modifiers").forGetter(RawSettings::attributeModifiers)
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
                    Optional.of(settings.levelsPerDeepness()),
                    Optional.of(settings.levelsPerHeight()),
                    Optional.of(settings.levelsPerDay()),
                    Optional.of(settings.levelsPerLocalDifficulty()),
                    Optional.of(settings.randomLevelBonus()),
                    Optional.ofNullable(settings.spawnPosOverride()),
                    Optional.of(settings.seaLevel()),
                    Optional.ofNullable(settings.attributeModifiers())
            )
    );

    /**
     * Creates default settings entirely from config values.
     */
    public static DimensionLevelingSettings createDefault() {
        return new DimensionLevelingSettings(
                Config.COMMON.startingLevel.get(),
                Config.COMMON.maxLevel.get(),
                Config.COMMON.levelsPerDistance.get().floatValue(),
                Config.COMMON.levelsPerDeepness.get().floatValue(),
                Config.COMMON.levelsPerHeight.get().floatValue(),
                Config.COMMON.levelsPerDay.get().floatValue(),
                Config.COMMON.levelsPerLocalDifficulty.get().floatValue(),
                Config.COMMON.randomLevelBonus.get(),
                null,
                64,
                null
        );
    }
}
