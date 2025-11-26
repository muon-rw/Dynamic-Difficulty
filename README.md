# Dynamic Difficulty

Highly configurable and compatible mob leveling system

___
# Mod Developers: 
- **No Maven yet! For now, use Cursemaven!**
---

# Datapack Guide
### NOTE: This guide is for 1.21.1 Neoforge!
*1.21.1 Fabric does not yet have all of these features, though they will be ported in the next few days!*
*For information on 1.21.10, make sure to select the 1.21.10 branch!*

### How are levels calculated?

1. **Base Level**
    - The config file `dynamic_difficulty-common.toml` defines default base levels, based on:
        - `starting_level` - Base level for all entities (default: 1)
        - `levels_per_distance` - Bonus per block from the world's spawn point (default: 0.01)
        - `levels_per_deepness` - Bonus per block below sea level (Y=64) (default: 0.0)
        - `random_level_bonus` - Random bonus levels (0 to this value) (default: 0)
    - Dimensions can override these defaults with a datapack (see [Dimensions](#dimensions))
    - Entity-specific settings provide final authority over base level (see [Entities](#entities))

2. **Non-Bypassing Bonuses** - Respect the `max_level` from Step 1
    - Bonuses with `bypasses_cap: false` (default for all biome bonuses, see [Biomes](#biome-leveling-settings))
    - If `max_level > 0`, the level is capped at this value
    - Only applies to base level + non-bypassing bonuses

3. **Bypassing Bonuses** - Applied in addition to `max_level`
    - Bonuses with `bypasses_cap: true` (default for all structure bonuses, see [Structure Bonuses](#structure-bonuses))
    - Player-based bonuses (scaled by nearby player levels, see [Player-based Scaling](#player-based-scaling))

**Example:**
- Entity with `max_level: 20`
- Base calculation:
    - `starting_level`: 1
    - Distance from spawn: 1600 blocks × `levels_per_distance` (0.01) = +16
    - Total base: **17**
- Biome bonus (`bypasses_cap: false`): +5 → Total: 22 → **Capped to 20**
- Structure bonus (`bypasses_cap: true`): +10 → **Final: 30**

### Lookup Priority

**Base settings** are resolved in this order (first match wins):
1. **Entity Types** (e.g., `entities/zombie.json`)
2. **Entity Type Tags** (e.g., `entity_tags/monsters.json`)
3. **Fallback to dimension defaults** (`dimensions/overworld.json`)
4. **Fallback to config defaults** (`config/dynamic_difficulty-common.toml`)

---

## Dimensions

**Individual Dimensions:**
```
data/<namespace>/leveling_settings/dimensions/<dimension_id>.json
```

**Dimension Tags:** (does anyone use these?)
```
data/<namespace>/leveling_settings/dimension_tags/<tag_id>.json
```

**Examples:**
- `data/minecraft/leveling_settings/dimensions/overworld.json`
- `data/minecraft/leveling_settings/dimensions/the_nether.json`
- `data/minecraft/leveling_settings/dimensions/the_end.json`

### JSON Format

**Example 1: Override attribute modifiers**
```json
{
  "starting_level": 1,
  "max_level": 0,
  "levels_per_distance": 0.005,
  "levels_per_deepness": 0.05,
  "random_level_bonus": 0,
  "attribute_modifiers": [
    {
      "attribute": "minecraft:generic.attack_damage",
      "amount": 0.3,
      "operation": "add_value"
    }
  ]
}
```

**Example 2: Fall back to config defaults**
```json
{
  "starting_level": 1,
  "max_level": 0,
  "levels_per_distance": 0.005,
  "levels_per_deepness": 0.05,
  "random_level_bonus": 0
}
```

**Note:** To fall back to config defaults, simply omit the `attribute_modifiers` field entirely (or use an empty array `[]` - both work the same way).

### Fields

| Field                 | Type | Default | Description                                                      |
|-----------------------|------|---------|------------------------------------------------------------------|
| `starting_level`      | Integer | 1 | Base level for entities in this dimension                        |
| `max_level`           | Integer | 0 | Maximum level cap (0 = unlimited)                                |
| `levels_per_distance` | Float | 0.01 | Levels added per block from spawn                                |
| `levels_per_deepness` | Float | 0.0 | Levels added per block below sea level                           |
| `random_level_bonus`  | Integer | 0 | Random bonus levels (0 to this value)                            |
| `spawn_pos_override`  | Object | `null` | **OPTIONAL** - Override spawn position for distance calculations |
| `attribute_modifiers` | Array | `[]` | **OPTIONAL** - Custom attribute bonuses per level    |

**Note:** Dimension settings are used as fallback when no entity-specific settings exist. Dimensions can also override attribute modifiers, using the same format as entity settings (see [Attribute Modifiers](#attribute-modifiers) below).

---

## Entities

**Individual Entities:**
```
data/<namespace>/leveling_settings/entities/<entity_id>.json
```

**Entity Tags:**
```
data/<namespace>/leveling_settings/entity_tags/<tag_id>.json
```

**Examples:**
- `data/minecraft/leveling_settings/entities/zombie.json`
- `data/minecraft/leveling_settings/entity_tags/monsters.json`
- `data/cataclysm/leveling_settings/entities/the_harbinger.json`

### JSON Format

**Example 1: Override attributes per level from the `dynamic_difficulty-common.toml` config**
```json
{
  "starting_level": 1,
  "max_level": 0,
  "levels_per_distance": 0.01,
  "levels_per_deepness": 0.0,
  "random_level_bonus": 0,
  "attribute_modifiers": [
    {
      "attribute": "minecraft:generic.attack_damage",
      "amount": 0.2,
      "operation": "add_value"
    },
    {
      "attribute": "minecraft:generic.max_health",
      "amount": 0.05,
      "operation": "add_multiplied_base"
    }
  ]
}
```

**Example 2: Fall back to config defaults for attributes per level**
```json
{
  "starting_level": 1,
  "max_level": 0,
  "levels_per_distance": 0.01,
  "levels_per_deepness": 0.0,
  "random_level_bonus": 0
}
```

**Note:** To fall back to config defaults, simply omit the `attribute_modifiers` field entirely (or use an empty array `[]` - both work the same way).

### Fields

| Field | Type | Default | Description                                    |
|-------|------|---------|------------------------------------------------|
| `starting_level` | Integer | 1 | Base level for this entity type                |
| `max_level` | Integer | 0 | Maximum level cap (0 = unlimited)              |
| `levels_per_distance` | Float | 0.01 | Levels added per block from world spawn        |
| `levels_per_deepness` | Float | 0.0 | Levels added per block below sea level         |
| `random_level_bonus` | Integer | 0 | Random bonus levels (0 to this value)          |
| `attribute_modifiers` | Array | `[]` | OPTIONAL -  Custom attribute bonuses per level |

### Attribute Modifiers

**Advanced:** Entities (and dimensions) can override the config's default attribute modifiers by specifying the `attribute_modifiers` array. This allows fine-grained control over how each entity type scales with level.

Optional array of attribute bonuses applied per entity level:

```json
"attribute_modifiers": [
  {
    "attribute": "minecraft:generic.attack_damage",
    "amount": 0.2,
    "operation": "add_value"
  },
  {
    "attribute": "minecraft:generic.max_health",
    "amount": 0.05,
    "operation": "add_multiplied_base"
  },
  {
    "attribute": "dynamic_difficulty:projectile_damage_bonus",
    "amount": 0.2,
    "operation": "add_value"
  }
]
```

**Operation Types:**
- `"add_value"` - Flat addition (e.g., +0.2 damage per level)
- `"add_multiplied_base"` - Add percentage of entity's base value (`0.05`: +5% health per level)
- `"add_multiplied_total"` - Same as `add_multiplied_base`, but multiply the "total" including all other modifiers

**Note:** Legacy numeric operation IDs (`0`, `1`, `2`) are deprecated but still functional for backwards compatibility. They will log a deprecation warning and may be removed in a future version. Please use the enum serialized names shown above.

**Common Attributes:**
- `minecraft:generic.attack_damage` - Attack damage
- `minecraft:generic.max_health` - Maximum health
- `minecraft:generic.armor` - Armor points
- `minecraft:generic.armor_toughness` - Armor toughness
- `minecraft:generic.knockback_resistance` - Knockback resistance
- `minecraft:generic.movement_speed` - Movement speed

**Built-in Mod Attributes:**
- `dynamic_difficulty:projectile_damage_bonus` - Bonus projectile damage
- `dynamic_difficulty:projectile_damage_multiplier` - Projectile damage multiplier
- `dynamic_difficulty:explosion_damage_bonus` - Bonus explosion damage
- `dynamic_difficulty:explosion_damage_multiplier` - Explosion damage multiplier
- `dynamic_difficulty:damage_bonus` - General damage bonus
- `dynamic_difficulty:damage_multiplier` - General damage multiplier
- `dynamic_difficulty:magic_damage_bonus` - Magic damage bonus
- `dynamic_difficulty:magic_damage_multiplier` - Magic damage multiplier

---

## Structure Bonuses

Configure level bonuses for structures or structure tags. Structure bonuses **bypass the max level cap by default**.

### File Locations

**Individual Structures:**
```
data/<namespace>/leveling_settings/structures/<structure_id>.json
```

**Structure Tags:**
```
data/<namespace>/leveling_settings/structure_tags/<tag_id>.json
```

**Examples:**
- `data/minecraft/leveling_settings/structures/trial_dungeon.json`
- `data/minecraft/leveling_settings/structure_tags/minecraft:village.json`
- `data/dynamic_difficulty/leveling_settings/structure_tags/level_1.json`

### JSON Format

```json
{
  "level_bonus": 10,
  "bypasses_cap": true
}
```

### Fields

| Field | Type | Default | Description                                            |
|-------|------|---------|--------------------------------------------------------|
| `level_bonus` | Integer | Required | Levels added to entities spawning in this structure    |
| `bypasses_cap` | Boolean | `true` | Whether this bonus bypasses the entity's max level cap |

### Built-in Structure Tags

The mod includes built-in structure tags in the `dynamic_difficulty` namespace:

- `dynamic_difficulty:level_1` - +5 levels (`bypasses_cap: true`)
- `dynamic_difficulty:level_2` - +10 levels (`bypasses_cap: true`)
- `dynamic_difficulty:level_3` - +15 levels (`bypasses_cap: true`)
- `dynamic_difficulty:level_4` - +20 levels (`bypasses_cap: true`)
- `dynamic_difficulty:level_5` - +25 levels (`bypasses_cap: true`)
- `dynamic_difficulty:level_6` - +30 levels (`bypasses_cap: true`)

These tags are defined in `data/dynamic_difficulty/tags/worldgen/structure/` and can be used to categorize structures by difficulty. You can add your own structures to these tags or create custom structure tags.

**Example:** To add a structure to the `level_3` tag, create:
```
data/<namespace>/tags/worldgen/structure/level_3.json
```

```json
{
  "replace": false,
  "values": [
    "minecraft:stronghold",
    {"id": "yourmod:custom_dungeon", "required": false}
  ]
}
```

**Note:** For modded structures, it's recommended to use the optional object format `{"id": "namespace:path", "required": false}` instead of plain strings. This prevents errors if the mod isn't installed.

---

## Biome Leveling Settings

Configure level bonuses for biomes or biome tags. Biome bonuses **do NOT bypass the max level cap by default**.

### File Locations

**Individual Biomes:**
```
data/<namespace>/leveling_settings/biomes/<biome_id>.json
```

**Biome Tags:**
```
data/<namespace>/leveling_settings/biome_tags/<tag_id>.json
```

**Examples:**
- `data/minecraft/leveling_settings/biomes/plains.json`
- `data/minecraft/leveling_settings/biome_tags/is_ocean.json`
- `data/minecraft/leveling_settings/biome_tags/is_overworld.json`

### JSON Format

```json
{
  "level_bonus": 5,
  "bypasses_cap": false
}
```

### Fields

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `level_bonus` | Integer | Required | Levels added to entities spawning in this biome |
| `bypasses_cap` | Boolean | `false` | Whether this bonus bypasses the max level cap |

**Note:** Biome bonuses with `bypasses_cap: false` are applied before the max level cap, while those with `bypasses_cap: true` are applied after (like structure bonuses).

---

## Complete Example

Here's a complete example datapack structure showing both custom mod and Minecraft namespaces:

```
datapacks/my_custom_leveling/
├── pack.mcmeta
└── data/
    ├── mymod/
    │   └── leveling_settings/
    │       ├── entities/
    │       │   └── custom_mob.json
    │       ├── entity_tags/
    │       │   └── bosses.json
    │       ├── dimensions/
    │       │   └── custom_dimension.json
    │       ├── structures/
    │       │   └── dungeon.json
    │       ├── structure_tags/
    │       │   └── difficult_structures.json
    │       ├── biomes/
    │       │   └── hostile_biome.json
    │       └── biome_tags/
    │           └── dangerous_biomes.json
    └── minecraft/
        └── leveling_settings/
            ├── entities/
            │   └── zombie.json
            └── dimensions/
                └── overworld.json
```

**Note:** The namespace is defined by the parent directory (`mymod/` or `minecraft/`), not in the filename. So `mymod/leveling_settings/biome_tags/dangerous_biomes.json` refers to the tag `mymod:dangerous_biomes`.

---

## Player-based Scaling

Dynamic Difficulty supports player-based level scaling, where nearby players contribute bonus levels to mobs based on their own levels.

### How It Works

- When enabled, the mod searches for players within a configured radius around each mob
- Each player's level is calculated from registered `PlayerLevelProvider` implementations
- The average (or configured aggregation method) of nearby player levels is used as a bonus
- This bonus is scaled by the `player_level_multiplier` config value
- Player-based bonuses **always bypass the max level cap**

### Configuration

Configure player-based scaling in `dynamic_difficulty-common.toml`:

- `enable_player_based_leveling` - Enable/disable player-based bonuses (default: `true`)
- `player_level_radius` - Search radius for nearby players (default: `128.0`)
- `player_level_multiplier` - Multiplier for player level bonuses (default: `1.0`)
- `player_level_display_strategy` - How to aggregate multiple player levels (default: `HIGHEST_PRIORITY`)

### Player Level Providers

Some player level provider support is built-in. Additional providers can be added via the API
Suggestions for new built-in providers are also welcome - Submit a GitHub issue! 

**Current Providers:**
- Pufferfish's Skills

---

## Tips:

1. Use the `/dynamic_difficulty dumpStructures` command to for a list of registered structures and their configured bonuses.
2. Use `/dynamic_difficulty debug level` to see a full breakdown for the zone you're standing in
3. You can check if your datapack is loaded in game by using `/datapack list`