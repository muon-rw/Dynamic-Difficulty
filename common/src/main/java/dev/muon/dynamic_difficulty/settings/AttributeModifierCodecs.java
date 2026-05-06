package dev.muon.dynamic_difficulty.settings;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.muon.dynamic_difficulty.DynamicDifficulty;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared codec helpers for {@code attribute_modifiers} JSON arrays used by all leveling settings
 * tiers (dimension, entity, location). Each tier produces modifiers with a tier-specific id prefix
 * so they don't collide when stacked on the same attribute instance.
 */
final class AttributeModifierCodecs {
  private AttributeModifierCodecs() {}

  private record Entry(Identifier attribute, double amount, String operation) {}

  private static AttributeModifier.Operation parseOperation(String operationStr) {
    for (AttributeModifier.Operation op : AttributeModifier.Operation.values()) {
      if (op.getSerializedName().equals(operationStr)) {
        return op;
      }
    }
    DynamicDifficulty.LOGGER.warn("Invalid operation '{}'. Defaulting to add_value.", operationStr);
    return AttributeModifier.Operation.ADD_VALUE;
  }

  // Accept both string (enum name) and int (legacy) for backwards compatibility.
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

  private static final Codec<Entry> ENTRY_CODEC =
      RecordCodecBuilder.create(instance -> instance.group(
          Identifier.CODEC.fieldOf("attribute").forGetter(Entry::attribute),
          Codec.DOUBLE.fieldOf("amount").forGetter(Entry::amount),
          OPERATION_CODEC.fieldOf("operation").forGetter(Entry::operation)
      ).apply(instance, Entry::new));

  /**
   * Returns a codec for {@code Map<Attribute, AttributeModifier>}. Each generated modifier id is
   * {@code dynamic_difficulty:<idPrefix><attribute_path>}, so different tiers produce distinct ids
   * for the same attribute (preventing one tier from removing another's modifier).
   */
  static Codec<Map<Attribute, AttributeModifier>> mapCodec(String idPrefix) {
    return Codec.list(ENTRY_CODEC).xmap(
        list -> {
          Map<Attribute, AttributeModifier> map = new HashMap<>();
          for (Entry entry : list) {
            Attribute attribute = BuiltInRegistries.ATTRIBUTE.getValue(entry.attribute());
            if (attribute != null) {
              AttributeModifier.Operation operation = parseOperation(entry.operation());
              Identifier modifierId = DynamicDifficulty.id(idPrefix + entry.attribute().getPath().replace("/", "_"));
              map.put(attribute, new AttributeModifier(modifierId, entry.amount(), operation));
            }
          }
          return map;
        },
        map -> {
          List<Entry> list = new ArrayList<>();
          map.forEach((attr, modifier) -> {
            Identifier attrId = BuiltInRegistries.ATTRIBUTE.getKey(attr);
            list.add(new Entry(attrId, modifier.amount(), modifier.operation().getSerializedName()));
          });
          return list;
        }
    );
  }
}
