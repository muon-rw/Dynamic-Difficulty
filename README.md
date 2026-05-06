# Dynamic Difficulty

Highly configurable and compatible mob leveling system

___

<details>
<summary><h1>Mod Developers (Click to Expand)</h1></summary>

### Maven Repository

Add the following mavens to your `repositories` in your `build.gradle` (assuming Gradle Groovy)
```groovy
repositories {
    // ... other repositories
    // Dynamic Difficulty
    maven { url = "https://maven.muon.rip/releases/" }
    // FzzyConfig
    maven { url = "https://maven.fzzyhmstrs.me/" }
    // Mixin Squared 
    maven { url = "https://maven.bawnorton.com/releases" }
}
```

Set a mod version in `gradle.properties`

```properties
dynamic_difficulty_version=1.3.0
minecraft_version=26.1.2
fzzy_config=0.7.6+26.1
```
Find all available mod versions at: [maven.muon.rip/#/releases/](https://maven.muon.rip/#/releases/)

Then add the appropriate artifact in `dependencies` in your `build.gradle`. Note that on 26.1.x Fabric Loom
no longer remaps, so dependencies use plain `implementation`/`runtimeOnly` (no `mod`-prefix).

#### NeoForge (assuming ModDevGradle)

```groovy
implementation("dev.muon.dynamic_difficulty:dynamic_difficulty-neoforge-${minecraft_version}:${dynamic_difficulty_version}")
implementation("me.fzzyhmstrs:fzzy_config:${fzzy_config}+neoforge")
```

#### Fabric (assuming Fabric Loom)

```groovy
implementation("dev.muon.dynamic_difficulty:dynamic_difficulty-fabric-${minecraft_version}:${dynamic_difficulty_version}")
implementation("me.fzzyhmstrs:fzzy_config:${fzzy_config}")
```

#### Common (assuming ModDevGradle)

```groovy
compileOnly("dev.muon.dynamic_difficulty:dynamic_difficulty-common-${minecraft_version}:${dynamic_difficulty_version}")
compileOnly("me.fzzyhmstrs:fzzy_config:${fzzy_config}")
```

</details>

---

# Datapack Guide
> [!CAUTION]
> ### This guide is for 26.1.2 Only!
> For older Minecraft versions, select the matching branch (e.g. `1.21.1-multiloader`, `1.21.11-multiloader`). Config file format and some config keys differ there.*

## Config Files

Dynamic Difficulty uses [FzzyConfig](https://modrinth.com/mod/fzzy-config). Two TOML files under `config/dynamic_difficulty/`:

| File | Purpose | Synced |
|---|---|---|
| `dynamic_difficulty-sync.toml` | Gameplay values (leveling formulas, caps, mod integration, loot toggles, attribute bonuses). | **Yes**. Server is authoritative; client values are overwritten on join. |
| `dynamic_difficulty-client.toml` | Rendering/HUD preferences (level plates, title overlays, colors, anchors, Jade toggle). | No |

FzzyConfig also ships an in-game editor, which you can open from the Mods screen or via `/fzzy_config`.

### Built-in Default Settings

The mod ships with a built-in datapack at (at `resourcepacks/default/data/` within the jar) containing dimension/entity/biome/structure presets for vanilla Minecraft and many popular mods (Cataclysm, Twilight Forest, Ice and Fire, etc.).

To start clean without these settings, set `useDefaultLevelingSettings = false` in `dynamic_difficulty-sync.toml` (requires a restart).

### How levels are calculated

For each mob spawn:

1. **Base level** is computed from:

    ```
    starting_level
      + distance        × levels_per_distance
      + depth           × levels_per_deepness         (if Y < sea_level)
      + height          × levels_per_height           (if Y > sea_level)
      + days            × levels_per_day
      + localDifficulty × levels_per_local_difficulty
      + random(0 .. random_level_bonus)
    ```

2. **+ non-bypassing bonuses** (`bypasses_cap: false`), then **capped at `max_level`** if `max_level > 0`
3. **+ bypassing bonuses** (`bypasses_cap: true`) and player-based bonuses on top of the cap (player bypass behavior is configurable via `playerLevelBypassesCap`)

All scaling factors come from the **resolution chain**:

```
config defaults → dimension → biome → structure → entity
```

Each tier can override any standard scaling field (not just the additive `level_bonus`). A structure can set `levels_per_distance: 0` for a flat-leveled dungeon; a biome can supply its own `attribute_modifiers`; an entity can pin its `max_level`. Each tier overrides any field set by the prior tier; tiers that don't set a field inherit. `spawn_pos_override` / `sea_level` are dimension-only; `apply_level_bonuses` is dimension/entity-only.

When multiple biome or structure entries match (individual + tags, or overlapping structures), override fields merge via **per-field max**: the largest value of each field wins independently. The `level_bonus`/`bypasses_cap` pair instead contributes via a bypass-cap bucket model (max bonus per bucket).

**Example**, entity with `max_level: 20`:
- Base: `1 + 1600×0.01 + 20×0.05 + 10×0.5 + 2.5×1.0` = **25**
- Biome bonus (+5, respects cap): `25+5=30` → **capped to 20**
- Structure bonus (+10, bypasses cap): **final 30**

> **Note on keys:** Datapack JSONs use snake_case (`starting_level`, `max_level`); the TOML config uses camelCase (`startingLevel`, `maxLevel`). Mixing them silently falls back to defaults.

---

## Dimensions

```
data/<namespace>/leveling_settings/dimensions/<dimension_id>.json
data/<namespace>/leveling_settings/dimension_tags/<tag_id>.json
```

All fields are optional; omitted fields fall back to the config defaults. Use `attribute_modifiers: []` to explicitly disable modifiers for the dimension.

**Minimal:**
```json
{ "max_level": 50, "levels_per_day": 0.5 }
```

**With overrides, attribute modifiers, and bonus gating:**
```json
{
  "starting_level": 1,
  "levels_per_distance": 0.005,
  "levels_per_height": 0.02,
  "spawn_pos_override": { "x": 0, "z": 0 },
  "sea_level": 64,
  "attribute_modifiers": [
    { "attribute": "minecraft:attack_damage", "amount": 0.3, "operation": "add_value" }
  ],
  "player_level_multiplier": 0.5,
  "apply_level_bonuses": { "biome": true, "structure": false, "player": false }
}
```

### Fields

| Field | Type | Default | Description |
|---|---|---|---|
| `starting_level` | Integer | config | Base level |
| `max_level` | Integer | config | Cap (`0` = unlimited) |
| `levels_per_distance` | Float | config | Per block from spawn |
| `levels_per_deepness` | Float | config | Per block below `sea_level` |
| `levels_per_height` | Float | config | Per block above `sea_level` |
| `levels_per_day` | Float | config | Per in-game day |
| `levels_per_local_difficulty` | Float | config | Per point of local difficulty |
| `random_level_bonus` | Integer | config | Max random bonus per entity |
| `spawn_pos_override` | Object | `null` | `{x, z}` for distance origin (dimension-only) |
| `sea_level` | Integer | 64 | Y reference for depth/height (dimension-only) |
| `attribute_modifiers` | Array | config | Per-level attribute bonuses |
| `player_level_multiplier` | Double | config | Player-bonus multiplier override |
| `apply_level_bonuses` | Object | `null` | Bonus-source gate (see below) |

### `apply_level_bonuses`

Disables specific bonus sources at this tier. When present, **all three fields are required**.

| Field | Description |
|---|---|
| `biome` | If `false`, biome `level_bonus` is skipped |
| `structure` | If `false`, structure `level_bonus` is skipped |
| `player` | If `false`, player-based bonuses are skipped |

The same shape applies at entity scope; entity overrides dimension. Biomes and structures cannot set this field.

---

## Entities

```
data/<namespace>/leveling_settings/entities/<entity_id>.json
data/<namespace>/leveling_settings/entity_tags/<tag_id>.json
```

Same field set as [Dimensions](#dimensions), minus `spawn_pos_override` and `sea_level` (dimension-only). All fields optional; omitted fields inherit through the resolution chain.

```json
{ "max_level": 100 }
```

```json
{
  "levels_per_distance": 0.0,
  "player_level_multiplier": 2.0,
  "attribute_modifiers": [
    { "attribute": "minecraft:attack_damage", "amount": 0.5, "operation": "add_value" }
  ],
  "apply_level_bonuses": { "biome": true, "structure": false, "player": true }
}
```

### Attribute Modifiers

Per-level attribute scaling. Each entry is `{attribute, amount, operation}`:

```json
"attribute_modifiers": [
  { "attribute": "minecraft:attack_damage",                  "amount": 0.2,  "operation": "add_value" },
  { "attribute": "minecraft:max_health",                     "amount": 0.05, "operation": "add_multiplied_base" },
  { "attribute": "dynamic_difficulty:projectile_damage_bonus", "amount": 0.2, "operation": "add_value" }
]
```

**Operations** (string enum names; legacy numeric ids `0`/`1`/`2` are deprecated):
- `add_value`: flat addition (`+0.2` per level)
- `add_multiplied_base`: % of entity's base value (`0.05` = +5% per level)
- `add_multiplied_total`: % of base plus all other modifiers

`attribute_modifiers: []` explicitly disables scaling at this tier.

**Vanilla attributes** worth knowing: `minecraft:attack_damage`, `minecraft:max_health`, `minecraft:armor`, `minecraft:armor_toughness`, `minecraft:knockback_resistance`, `minecraft:movement_speed`.

**Mod-added damage attributes:** `dynamic_difficulty:{projectile,explosion,damage,magic_damage}_{bonus,multiplier}`.

---

## Structure Settings

```
data/<namespace>/leveling_settings/structures/<structure_id>.json
data/<namespace>/leveling_settings/structure_tags/<tag_id>.json
```

Structures sit between biomes and entities in the resolution chain. A structure entry can:
- Contribute an additive `level_bonus` (default `bypasses_cap: true`)
- **Override** any standard scaling field (`starting_level`, `levels_per_*`, `attribute_modifiers`, `player_level_multiplier`, …)

**Pure bonus:**
```json
{ "level_bonus": 10, "bypasses_cap": true }
```

**Flat-leveled dungeon (override scaling, no bonus):**
```json
{
  "starting_level": 30,
  "max_level": 35,
  "levels_per_distance": 0.0,
  "levels_per_deepness": 0.0
}
```

**Bonus + per-structure attribute modifiers:**
```json
{
  "level_bonus": 10,
  "attribute_modifiers": [
    { "attribute": "minecraft:armor", "amount": 0.5, "operation": "add_value" }
  ]
}
```

### Fields

All fields optional; omitted fields inherit through the chain. Field set is the same as [Dimensions](#dimensions) **except** `apply_level_bonuses`, `spawn_pos_override`, and `sea_level` cannot be set on structures. The bonus pair is structure-specific:

| Field | Type | Default | Description |
|---|---|---|---|
| `level_bonus` | Integer | `0` | Additive bonus inside this structure |
| `bypasses_cap` | Boolean | `true` | Whether `level_bonus` bypasses `max_level` |

**Merging:** when a structure matches an individual entry plus tags, or when multiple structures overlap one position, override fields merge via **per-field max**. The `level_bonus`/`bypasses_cap` pair contributes via the bypass-cap bucket model: max bonus per bucket (bypassing vs non-bypassing).

### Built-in Structure Tags

| Tag | Bonus | Cap |
|---|---|---|
| `dynamic_difficulty:level_1` | +5 | bypasses |
| `dynamic_difficulty:level_2` | +10 | bypasses |
| `dynamic_difficulty:level_3` | +15 | bypasses |
| `dynamic_difficulty:level_4` | +20 | bypasses |
| `dynamic_difficulty:level_5` | +25 | bypasses |
| `dynamic_difficulty:level_6` | +30 | bypasses |

Add structures to a tier by creating `data/<namespace>/tags/worldgen/structure/level_N.json`:

```json
{
  "replace": false,
  "values": [
    "minecraft:stronghold",
    {"id": "yourmod:custom_dungeon", "required": false}
  ]
}
```

Use the `{"id": ..., "required": false}` form for modded structures so the tag still loads if the mod is missing.

---

## Biome Settings

```
data/<namespace>/leveling_settings/biomes/<biome_id>.json
data/<namespace>/leveling_settings/biome_tags/<tag_id>.json
```

Same field set and merging rules as [Structure Settings](#structure-settings). The only difference: `bypasses_cap` defaults to `false` for biomes.

```json
{ "level_bonus": 5, "bypasses_cap": false }
```

Biomes can also override scaling fields. For example, a hostile biome that bumps distance scaling and applies its own attribute modifiers:

```json
{
  "levels_per_distance": 0.02,
  "level_bonus": 5,
  "attribute_modifiers": [
    { "attribute": "minecraft:attack_damage", "amount": 0.4, "operation": "add_value" }
  ]
}
```

---

## Datapack Layout

```
datapacks/my_pack/
├── pack.mcmeta
└── data/<namespace>/leveling_settings/
    ├── dimensions/<id>.json     dimension_tags/<tag>.json
    ├── biomes/<id>.json         biome_tags/<tag>.json
    ├── structures/<id>.json     structure_tags/<tag>.json
    └── entities/<id>.json       entity_tags/<tag>.json
```

The namespace comes from the directory under `data/`, not the filename. So `data/mymod/leveling_settings/biome_tags/foo.json` defines settings for the tag `mymod:foo`.

---

## Player-based Scaling

When enabled, mobs gain bonus levels based on registered `PlayerLevelProvider` levels of nearby players (within a configurable radius, aggregated per a configurable strategy).

The contribution is scaled by `playerLevelMultiplier`, overridable per dimension/biome/structure/entity via `player_level_multiplier` in the resolution chain. Bypass behavior follows the `playerLevelBypassesCap` config (default: `true`, the bonus is added on top of `max_level`).

Config keys (`dynamic_difficulty-sync.toml`):

| Key | Default | Description |
|---|---|---|
| `applyPlayerBasedLeveling` | `true` | Master toggle |
| `playerLevelRadius` | `128.0` | Search radius (blocks) |
| `playerLevelMultiplier` | `1.0` | Default scaling multiplier |
| `playerLevelBypassesCap` | `true` | If `false`, the bonus respects `max_level` |
| `playerLevelDisplayStrategy` | `HIGHEST_PRIORITY` | How to aggregate multiple players |

**Built-in providers:** Pufferfish's Skills. Additional providers can be added via the API; suggestions for new built-ins are welcome. Open a GitHub issue.

---

## Loot Settings

Level-gated drops are injected into entity loot via Global Loot Modifiers (NeoForge) or a mixin (Fabric). Toggle the entire system with `enableLevelBasedDrops` in `dynamic_difficulty-sync.toml`. To customize drops without disabling injection, override the loot table (see below).

### Loot Condition: `dynamic_difficulty:entity_level`

Gate any loot pool by mob level. All fields optional (use `min`/`max` for ranges, `exact` for a single level; `exact` overrides `min`/`max`).

```json
{ "condition": "dynamic_difficulty:entity_level", "min": 20, "max": 50 }
```

### Built-in Loot Table

```
data/dynamic_difficulty/loot_table/inject/level_based_drops.json
```

Tiered level-up item drops:

| Level Range | Drops |
|---|---|
| 2–19 | Potion of Growth, Elixir of Nurturing, Draught of Ascension |
| 20–39 | + Essence of Vitality |
| 40–59 | + Crystal of Awakening |
| 60–79 | Higher rarity weights |
| 80+ | Highest rarity weights |

Each level-up item has a configurable cap (`{itemName}MaxLevel` in sync TOML). Defaults: Potion of Growth 20, Elixir 40, Draught 60, Essence 80, Crystal 100. The drop ranges are tuned to match these caps; adjust both together if you change them.

### Customizing

- **Replace drops (both loaders):** put your own table at `data/dynamic_difficulty/loot_table/inject/level_based_drops.json`.
- **Replace the GLM entirely (NeoForge):** override `data/dynamic_difficulty/loot_modifiers/inject_level_drops.json`.
- **Add GLMs alongside (NeoForge):** add an entry in `data/neoforge/loot_modifiers/global_loot_modifiers.json` with `"replace": false` so you stack with the built-in.

**Custom-table example** (1% diamond drop from level-50+ mobs):

```json
{
  "type": "minecraft:entity",
  "pools": [
    {
      "rolls": 1,
      "entries": [
        { "type": "minecraft:item", "name": "minecraft:diamond", "weight": 1 },
        { "type": "minecraft:empty", "weight": 99 }
      ],
      "conditions": [
        { "condition": "dynamic_difficulty:entity_level", "min": 50 }
      ]
    }
  ]
}
```

---

## Tips

- `/dynamic_difficulty debug location`: full breakdown of the level calculation at your position (base, overrides, bonuses, cap, final). Indispensable when something looks off.
- `/dynamic_difficulty dumpStructures`: lists registered structures and their configured bonuses.
- `/datapack list`: confirm your datapack is loaded.
- Edit datapacks with [VSCode](https://code.visualstudio.com/) by opening the *entire datapack folder*. Much easier than poking files through Explorer/Finder.
