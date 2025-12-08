# Dynamic Difficulty

Highly configurable and compatible mob leveling system

___

<details>
<summary><h2>For Mod Developers</h2></summary>

### Maven Repository

Add the following to your `repositories` 
```groovy
repositories {
    maven {
        url = "https://maven.muon.rip/releases/"
    }
}
```

### Dependency Setup

Set a version in `gradle.properties` (assuming Gradle Groovy)

```properties
dynamic_difficulty_version=1.0.0+1.21.1
```

Then add the appropriate dependency for your loader:

#### NeoForge

```groovy
implementation("dev.muon.dynamic_difficulty:dynamic_difficulty-neoforge:${dynamic_difficulty_version}")
```

#### Fabric

```groovy
modImplementation("dev.muon.dynamic_difficulty:dynamic_difficulty-fabric:${dynamic_difficulty_version}")
```

#### Common

```groovy
implementation("dev.muon.dynamic_difficulty:dynamic_difficulty-common:${dynamic_difficulty_version}")
```


### Available Versions

Find available versions at: [maven.muon.rip/#/releases/](https://maven.muon.rip/#/releases/)

</details>

---

# Datapack Guide
### NOTE: This guide is for 1.21.1 Only!
*For information on 1.21.10, make sure to select the 1.21.10 branch!*

### How are levels calculated?

1. **Base Level**
    - The config file `dynamic_difficulty-common.toml` defines default base levels, based on:
        - `starting_level` - Base level for all entities (default: 1)
        - `levels_per_distance` - Bonus per block from the world's spawn point (default: 0.01)
        - `levels_per_deepness` - Bonus per block below sea level (default: 0.0)
        - `levels_per_height` - Bonus per block above sea level (default: 0.0)
        - `levels_per_day` - Bonus per in-game day passed (default: 0.0)
        - `levels_per_local_difficulty` - Bonus based on Minecraft's local difficulty (default: 0.0)
        - `random_level_bonus` - Random bonus levels (0 to this value) (default: 0)
    - **All fields are optional in datapacks** - omit a field to use config defaults
    - Dimensions can override any of these defaults with a datapack (see [Dimensions](#dimensions))
    - Entity-specific settings provide final authority over base level (see [Entities](#entities))
    - **Note:** Deepness/height scaling use sea level (Y=64) as the reference point. Dimensions can override this with the `sea_level` field.

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
    - Depth: 20 blocks below sea level × `levels_per_deepness` (0.05) = +1
    - Days: 10 days × `levels_per_day` (0.5) = +5
    - Local difficulty: 2.5 × `levels_per_local_difficulty` (1.0) = +2
    - Total base: **25**
- Biome bonus (`bypasses_cap: false`): +5 → Total: 30 → **Capped to 20**
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

**All fields are optional** - omit any field to use the config default.

**Example 1: Minimal - just override what you need**
```json
{
  "max_level": 50,
  "levels_per_day": 0.5
}
```

**Example 2: Override attribute modifiers**
```json
{
  "starting_level": 1,
  "levels_per_distance": 0.005,
  "levels_per_deepness": 0.05,
  "attribute_modifiers": [
    {
      "attribute": "minecraft:generic.attack_damage",
      "amount": 0.3,
      "operation": "add_value"
    }
  ]
}
```

**Example 3: Using spawn_pos_override and height/day scaling**
```json
{
  "levels_per_distance": 0.01,
  "levels_per_height": 0.02,
  "levels_per_day": 0.5,
  "levels_per_local_difficulty": 1.0,
  "spawn_pos_override": {
    "x": 0,
    "z": 0
  },
  "sea_level": 64
}
```

**Example 4: Disable day/local scaling for a dimension (e.g., The End)**
```json
{
  "levels_per_day": 0.0,
  "levels_per_local_difficulty": 0.0
}
```

### Fields

| Field                 | Type | Default | Description                                                      |
|-----------------------|------|---------|------------------------------------------------------------------|
| `starting_level`      | Integer | config | Base level for entities in this dimension                        |
| `max_level`           | Integer | config | Maximum level cap (0 = unlimited)                                |
| `levels_per_distance` | Float | config | Levels added per block from spawn                                |
| `levels_per_deepness` | Float | config | Levels added per block below sea level (only applies when Y < sea_level) |
| `levels_per_height`   | Float | config | Levels added per block above sea level (only applies when Y > sea_level) |
| `levels_per_day`      | Float | config | Levels added per in-game day passed                              |
| `levels_per_local_difficulty` | Float | config | Levels added per point of local difficulty                |
| `random_level_bonus`  | Integer | config | Random bonus levels (0 to this value)                            |
| `spawn_pos_override`  | Object | `null` | Override spawn position (x, z only) for horizontal distance calculations |
| `sea_level`           | Integer | 64 | Reference Y coordinate for depth/height calculations             |
| `attribute_modifiers` | Array | config | Custom attribute bonuses per level                               |

**Note:** All fields are optional. Omitting a field uses the value from `dynamic_difficulty-common.toml` config. For `attribute_modifiers`, use an empty array `[]` to explicitly disable modifiers for this dimension.

**Note:** Dimension settings are used as fallback when no entity-specific settings exist. Dimensions can also override attribute modifiers, using the same format as entity settings (see [Attribute Modifiers](#attribute-modifiers) below).

**Important:**
- Deepness-based scaling (`levels_per_deepness`) only applies when Y < `sea_level` (default: 64). It does not affect entities above sea level.
- Height-based scaling (`levels_per_height`) is optional and only applies when Y > `sea_level` and `levels_per_height` > 0.
- The `sea_level` can be overridden per dimension to match different world generation (e.g., set to 0 for dimensions without a traditional sea level).

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

**All fields are optional** - omit any field to inherit from dimension settings (which fall back to config).

**Example 1: Minimal - just set a max level for bosses**
```json
{
  "max_level": 100
}
```

**Example 2: Override attributes for a specific entity**
```json
{
  "max_level": 50,
  "attribute_modifiers": [
    {
      "attribute": "minecraft:generic.attack_damage",
      "amount": 0.5,
      "operation": "add_value"
    }
  ]
}
```

**Example 3: Disable distance scaling for a stationary enemy**
```json
{
  "levels_per_distance": 0.0,
  "levels_per_day": 0.0
}
```

### Fields

| Field | Type | Default | Description                                    |
|-------|------|---------|------------------------------------------------|
| `starting_level` | Integer | dimension | Base level for this entity type                |
| `max_level` | Integer | dimension | Maximum level cap (0 = unlimited)              |
| `levels_per_distance` | Float | dimension | Levels added per block from world spawn        |
| `levels_per_deepness` | Float | dimension | Levels added per block below sea level         |
| `levels_per_height` | Float | dimension | Levels added per block above sea level         |
| `levels_per_day` | Float | dimension | Levels added per in-game day passed            |
| `levels_per_local_difficulty` | Float | dimension | Levels added per point of local difficulty     |
| `random_level_bonus` | Integer | dimension | Random bonus levels (0 to this value)          |
| `attribute_modifiers` | Array | dimension | Custom attribute bonuses per level             |

**Note:** All fields are optional. Omitting a field uses the value from the dimension settings, which in turn falls back to config. For `attribute_modifiers`, use an empty array `[]` to explicitly disable modifiers for this entity.

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
- `data/minecraft/leveling_settings/structure_tags/village.json`
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

Some player level provider support is built-in.
- Additional providers can be added via the API
- Suggestions for new built-in providers are also welcome - Submit a GitHub issue!

**Current Providers:**
- Pufferfish's Skills

---

## Loot Settings

Dynamic Difficulty provides a flexible system for level-based loot drops.

**Platform Implementation:**
- **NeoForge:** Uses Global Loot Modifiers (GLMs) for loot injection
- **Fabric:** Uses a mixin to inject the loot table directly

### Configuration

Level-based drops can be enabled/disabled in `dynamic_difficulty-common.toml`:

```toml
[level_based_drops]
# Whether mobs should drop level-up items based on their level
enable_level_based_drops = true
```

**Note:** This config option toggles the entire built-in loot injection system. When disabled, no loot injection occurs regardless of what's in the loot table. If you want to keep loot injection active but customize the drops, override the loot table instead (see [Overriding the Built-in Loot](#overriding-the-built-in-loot)).

### Built-in Loot Condition

The mod provides a custom loot condition for level-gated drops:

**Condition Type:** `dynamic_difficulty:entity_level`

| Field | Type | Description |
|-------|------|-------------|
| `min` | Integer | **OPTIONAL** - Minimum entity level (inclusive) |
| `max` | Integer | **OPTIONAL** - Maximum entity level (inclusive) |
| `exact` | Integer | **OPTIONAL** - Match exactly this level (overrides min/max) |

**Examples:**

```json
{
  "condition": "dynamic_difficulty:entity_level",
  "min": 20,
  "max": 50
}
```

```json
{
  "condition": "dynamic_difficulty:entity_level",
  "min": 10
}
```

```json
{
  "condition": "dynamic_difficulty:entity_level",
  "exact": 100
}
```

### Built-in Global Loot Modifier (NeoForge Only)

On NeoForge, the mod includes a Global Loot Modifier (GLM) that injects a custom loot table into all entity drops.

**File:** `data/dynamic_difficulty/loot_modifiers/inject_level_drops.json`

```json
{
  "type": "dynamic_difficulty:inject_loot_table",
  "conditions": [],
  "loot_table": "dynamic_difficulty:inject/level_based_drops"
}
```

**GLM Type:** `dynamic_difficulty:inject_loot_table`

| Field | Type | Description |
|-------|------|-------------|
| `conditions` | Array | Standard NeoForge loot conditions |
| `loot_table` | ResourceLocation | The loot table to inject into entity drops |

**Note:** On Fabric, the loot table injection is handled via mixin instead. The same loot table (`dynamic_difficulty:inject/level_based_drops`) is used on both platforms.

### Built-in Loot Table

The default level-based drops are defined in:

```
data/dynamic_difficulty/loot_table/inject/level_based_drops.json
```

This table uses multiple pools with `dynamic_difficulty:entity_level` conditions to provide tiered drops:

| Level Range | Available Drops |
|-------------|-----------------|
| 2-19 | Potion of Growth, Elixir of Nurturing, Draught of Ascension |
| 20-39 | + Essence of Vitality |
| 40-59 | + Crystal of Awakening |
| 60-79 | Higher rarity weights |
| 80+ | Highest rarity weights |

### Level-Up Item Caps

The built-in level-up items have configurable maximum level caps in `dynamic_difficulty-common.toml`:

| Item | Default Max Level | Config Key |
|------|-------------------|------------|
| Potion of Growth | 20 | `potion_of_growth_max_level` |
| Elixir of Nurturing | 40 | `elixir_of_nurturing_max_level` |
| Draught of Ascension | 60 | `draught_of_ascension_max_level` |
| Essence of Vitality | 80 | `essence_of_vitality_max_level` |
| Crystal of Awakening | 100 | `crystal_of_awakening_max_level` |

**Note:** The drop level ranges in the built-in loot table are designed to match these caps. For example, entities level 2-19 can drop Potion of Growth (which levels mobs up to 20), while entities level 20+ start dropping Elixir of Nurturing (which can raise mobs to 40), and so on. This creates a natural progression where defeating higher-level mobs yields items capable of creating even stronger mobs.

If you modify these caps in the config, consider also updating the loot table ranges to match.

### Overriding the Built-in Loot

To customize or replace the built-in loot behavior, you have several options:

**Option 1: Replace the loot table (easiest, works on both platforms)**

Create your own loot table at `data/dynamic_difficulty/loot_table/inject/level_based_drops.json` in your datapack. Both NeoForge and Fabric will automatically use your table instead.

**Option 2: Replace the GLM entirely (NeoForge only)**

Override the built-in GLM by creating your own file at:
```
data/dynamic_difficulty/loot_modifiers/inject_level_drops.json
```

This completely replaces the built-in GLM with your own configuration.

**Option 3: Add additional GLMs (NeoForge only)**

The `global_loot_modifiers.json` works like a tag — using `"replace": false` merges your entries with existing ones. You only need to include your own GLM:

```
data/neoforge/loot_modifiers/global_loot_modifiers.json
```

```json
{
  "replace": false,
  "entries": [
    "yourmod:additional_level_drops"
  ]
}
```

This adds your GLM alongside the built-in one without affecting other mods.

### Example: Custom Level-Based Loot Table

```json
{
  "type": "minecraft:entity",
  "pools": [
    {
      "rolls": 1,
      "entries": [
        {
          "type": "minecraft:item",
          "name": "minecraft:diamond",
          "weight": 1
        },
        {
          "type": "minecraft:empty",
          "weight": 99
        }
      ],
      "conditions": [
        {
          "condition": "dynamic_difficulty:entity_level",
          "min": 50
        }
      ]
    }
  ]
}
```

This example gives a 1% chance to drop a diamond from entities level 50 or higher.

---

## Tips:

1. Use the `/dynamic_difficulty dumpStructures` command for a list of registered structures and their configured bonuses.
2. Use `/dynamic_difficulty debug location` to see a full breakdown of level calculation for where you're standing
3. You can check if your datapack is loaded in game by using `/datapack list`
4. Datapacks are best edited with [VSCode](https://code.visualstudio.com/)
5. Open your *entire datapack folder* in VScode to manage all files at once - Infinitely easier to organize lots of files and folders, rather than relying on Explorer/Finder! 
