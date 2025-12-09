package dev.muon.dynamic_difficulty.compat.dungeon_difficulty;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.Locale;

/**
 * Data record storing Dungeon Difficulty scaling information for an entity.
 * 
 * @param difficultyName The name of the difficulty type (e.g., "normal", "dungeon", "apocalypse")
 * @param level The difficulty level assigned to the entity
 */
public record DungeonDifficultyData(String difficultyName, int level) {
    
    public static final DungeonDifficultyData EMPTY = new DungeonDifficultyData("", 0);
    
    public static final Codec<DungeonDifficultyData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.STRING.fieldOf("difficulty_name").forGetter(DungeonDifficultyData::difficultyName),
                    Codec.INT.fieldOf("level").forGetter(DungeonDifficultyData::level)
            ).apply(instance, DungeonDifficultyData::new)
    );
    
    public static final StreamCodec<ByteBuf, DungeonDifficultyData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, DungeonDifficultyData::difficultyName,
            ByteBufCodecs.VAR_INT, DungeonDifficultyData::level,
            DungeonDifficultyData::new
    );
    
    public boolean isEmpty() {
        return difficultyName.isEmpty() || level <= 0;
    }
    
    /**
     * Gets the translation key for this difficulty type.
     * Uses Dungeon Difficulty's translation key format: "difficulty.type.<name>"
     * @return The translation key (e.g., "difficulty.type.normal", "difficulty.type.dungeon")
     */
    public String getTranslationKey() {
        return "difficulty.type." + difficultyName.toLowerCase(Locale.ROOT);
    }
}

