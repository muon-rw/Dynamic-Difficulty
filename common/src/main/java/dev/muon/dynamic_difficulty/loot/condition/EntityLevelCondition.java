package dev.muon.dynamic_difficulty.loot.condition;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.muon.dynamic_difficulty.api.LevelingAPI;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;

import java.util.Optional;

/**
 * Loot condition that checks if an entity's level is within a specified range.
 * This allows datapack creators and modpack makers to create level-gated loot drops.
 * 
 * Example JSON:
 * {
 *   "condition": "dynamic_difficulty:entity_level",
 *   "min": 20,
 *   "max": 50
 * }
 */
public record EntityLevelCondition(Optional<Integer> min, Optional<Integer> max, Optional<Integer> exact) implements LootItemCondition {
    
    public static final MapCodec<EntityLevelCondition> CODEC = RecordCodecBuilder.mapCodec(instance ->
        instance.group(
            Codec.INT.optionalFieldOf("min").forGetter(EntityLevelCondition::min),
            Codec.INT.optionalFieldOf("max").forGetter(EntityLevelCondition::max),
            Codec.INT.optionalFieldOf("exact").forGetter(EntityLevelCondition::exact)
        ).apply(instance, EntityLevelCondition::new)
    );

    @Override
    public LootItemConditionType getType() {
        return ModLootConditions.ENTITY_LEVEL.value();
    }

    @Override
    public boolean test(LootContext context) {
        Entity entity = context.getOptionalParameter(LootContextParams.THIS_ENTITY);
        
        if (!(entity instanceof LivingEntity living)) {
            return false;
        }

        if (!LevelingAPI.hasLevel(living)) {
            return false;
        }

        int entityLevel = LevelingAPI.getLevel(living);

        // Check exact level if specified
        if (exact.isPresent()) {
            return entityLevel == exact.get();
        }

        // Check min/max range
        if (min.isPresent() && entityLevel < min.get()) {
            return false;
        }

        if (max.isPresent() && entityLevel > max.get()) {
            return false;
        }

        return true;
    }

    /**
     * Builder for creating EntityLevelCondition instances programmatically
     */
    public static class Builder implements LootItemCondition.Builder {
        private Optional<Integer> min = Optional.empty();
        private Optional<Integer> max = Optional.empty();
        private Optional<Integer> exact = Optional.empty();

        public Builder min(int min) {
            this.min = Optional.of(min);
            return this;
        }

        public Builder max(int max) {
            this.max = Optional.of(max);
            return this;
        }

        public Builder exact(int exact) {
            this.exact = Optional.of(exact);
            return this;
        }

        public Builder range(int min, int max) {
            this.min = Optional.of(min);
            this.max = Optional.of(max);
            return this;
        }

        @Override
        public LootItemCondition build() {
            return new EntityLevelCondition(min, max, exact);
        }
    }

    /**
     * Creates a new builder for this condition
     */
    public static Builder builder() {
        return new Builder();
    }
}

