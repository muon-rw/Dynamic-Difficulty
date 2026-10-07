## 1.3.5
- Performance: LevelPlaterHandler now only runs its invisibility and raycast checks for mobs eligible for nameplate rendering at all

## 1.3.4
- Added new optional `priority` field to entity and dimension tag settings for more granularity with overlapping tags (previously, one tag was selected arbitrarily)
- Fix structure lookups loading or generating chunks on the server thread, which could hang the server (#62)
- Mobs keep their share of health when a level changes their max health, instead of healing to full; fixes Apothic Spawners' starting health being overridden (#58)
- Unleveled entities (blacklisted, passive, armor stands) no longer show "Lv. 1" on nameplates or Health Bars, and hidden-level entities no longer get their nameplate forced hidden (#39)
- The built-in overworld settings no longer override `startingLevel` and `levelsPerDeepness` from the config (#60)
- Per-mob and per-sync debug logging is now off by default; enable it with `debugLogging` in `sync.toml`

## 1.3.3
Brings 1.21.1 up to date with the 26.1.2 releases (1.2.0 through 1.3.3)
- Migrate to FzzyConfig, now required instead of Forge Config API Port
- Config files are now `config/dynamic_difficulty/sync.toml` and `client.toml`; gameplay settings are synced from the server
- *Configs* can NOT be reused from old versions, you'll need to reapply changes
- Existing *datapacks* should still be compatible, except for the renamed structure tags below
- Biome/structure datapacks can now override any base setting (`starting_level`, `levels_per_*`, `attribute_modifiers`, `player_level_multiplier`, …), not just `level_bonus`/`bypasses_cap`
- Resolution chain is now dimension -> biome -> structure -> entity
- Multi-tag and overlapping-structure matches merge per highest of each field
- Negative biome/structure `level_bonus` values are now ignored; override `starting_level` or `max_level` instead
- Built-in structure tags renamed from `level_1`..`level_6` to `level_5`..`level_30` (named after their bonus), and now belong to the built-in datapack
- Add Health Bars (Fuzs) compat
- Better compat for Visual Traveler's Titles
- Change default component from "Level n" to "Lv. n"
- Don't inject nameplate onto players regardless of config if Chronicles: Leveling is loaded
- Default config: player level multiplier 1.0 -> 0.3, no mob armor per level, depth scaling 0.05 per block
- Disable all horizontal-distance-based-scaling by default
- Add a few default biome-tag based bonuses
- Fix damage bonuses being applied twice on Neoforge
- Fix playtime scaling and player level updates never registering on Fabric
- Fix `dimension_tags` settings only affecting the debug display, not spawned mobs
- Fix the level HUD and `LevelingAPI.getLevelAt` ignoring the max level, `playerLevelBypassesCap` and `apply_level_bonuses`
- Leveling attribute bonuses that are no longer configured are removed from existing mobs when they next load
- Fix mobs healing to full whenever their chunk reloads
- Invalid leveling settings files are now logged instead of silently skipped
- Biome and structure lookups now run once per mob spawn

## 1.1.1
- Update Reskillable Reimagined compat to 2.3.3
- Fix levelup items not being usable on Tamed Wolves (or other mobs that override right click behavior)
- Added config values for nameplate handling
- Fixed a rare case of "Cannot get config value before config is loaded" with certain mods installed
- Some internal javadoc cleanup

## 1.1.0
- Allow Entities or Dimensions to set a player scaling multiplier, or disable biome/structure/player bonuses entirely. See README for usage

## 1.0.9
- Fix "Trying to access Unbound Value" on Neoforge 

## 1.0.8
- Whether player level bonuses bypass the max level cap is now configurable
- Entity blacklist/whitelists now support modid wildcards (`cataclysm:*`) (This was already supported, but undocumented)

## 1.0.7
- Change default `#c:is_cold/overworld` biome level bonus to `#c:is_icy`

## 1.0.6
- Properly mark "Forge Config API Port" as required on Fabric
- Add config option to disable built-in leveling settings ("Built-In Datapack" section), so it's easier for pack devs to start their own from scratch
- Fix some small issues with the `/dynamic_difficulty debug location` command related to depth

## 1.0.5
- Add Dungeon Difficulty nameplate compat (can be toggled off in config)
- Add some better edge-case handling for Fabric-side dedicated server attachment syncing

## 1.0.4
- Fix a crash on Fabric when connecting to dedicated servers

## 1.0.3
- Add API helper methods `getLevelAt` for finding the base entity level at a given location
- Add `@since` and `@Nullable`/`@NotNull` annotations to API, improved some `@return` descriptions

## 1.0.2
- More fixes for gradle project resolution

## 1.0.1
- Make gradle resolution easier, updated dev readme

## 1.0.0
- Multiloader Port - Might be some small things missed, please report any bugs you find! 
- Increase config default bonus for attack/projectile/magic damage per level 0.2 -> 0.25
- Add preset levels for Mine Cells, RPG MiniBosses, The Bumblezone, Marium's Soulslike Weaponry, Eden Ring, Paradise Lost, T.O Magic n' Extras
- Updated preset levels for Aquamirae, Ice And Fire, Iron's Spellbooks, Cataclysm, Friends and Foes, Minecraft
- Fix "Only show structure title if modified" config option not working
- Player bonuses are now merged into the "Lv. xx" subtitle, unless advanced tooltips is enabled
- Use platform built-in syncing for level attachment
- Implemented the `set`/`add`/`get` level commands

## 0.9.1
- Clean up datapack settings fallback chain: Entity -> Dimension -> Config (all fields now optional)
- `levels_per_day` and `levels_per_local_difficulty` are now overridable per dimension/entity
- `levels_per_height` is now overridable per entity
- Specifying empty attribute modifiers `"attribute_modifiers": []` now explicitly disables scaling
- Omitting `attribute_modifiers` falls back to dimension -> config defaults
- Debug Location command now factors random bonus in Final Level

## 0.9.0
- Sync reskillable points automatically on allocation
- Fix dayBonus not applying (Thanks jeffjks for bug testing!)
- Add optional individual playtime-based scaling
- Add optional local difficulty-based scaling
- More internal reorganization (Almost stable, really!)
- Various improvements to the debug location command

## 0.8.0
- Add support for Reskillable Reimagined for player-based mob scaling
- Fix depth-based scaling
- Add height-based scaling
- Sea level is now configurable per dimension in datapacks
- `spawn_pos_override` now only takes an `x` and `z`
- Add config option to disable restriction for only showing structure titles if they have modified levels
- Updated some dimension default settings
- Some more internal reorganization

## 0.7.3
- Removed structure/biome cache from 0.7.2
- Now only check bonuses for known nearby structures using StructureManager#startsForStructure and LocationPredicate, making caching unnecessary

## 0.7.2
- Fix potential performance issues caused by scanning for Apotheosis modifiers
- Add option to disable line of sight checks for performance
- Optimize location lookups
- More internal reorganization

## 0.7.1
- Fix Puffish Skills point allocations not updating instantly
- Rename methods related to level tag -> level attachment
- Improve doc accuracy throughout codebase

## 0.7.0
- Created a datapack guide, see the README on github!
- Build in many preset leveling settings
- Puffish Skills player-based scaling now has a tree blacklist, default `puffish_skills:mining`
- Add biome-based scaling (none included by default)
- Add entity tag, biome tag, dimension tag scaling (none included by default)
- Move structure/structure-tag scaling from config to datapacks (to match entities/biomes/dimensions)
- Switch modifier operations to use enum (`"add_multiplied_base"`, `"add_value"`, `"add_multiplied_total"`), instead of int (`0`, `1`, `2`)
- Removed unimplemented "level_power_per" settings
- Add built-in Traveler's Titles-like dimension/biome announcement (disabled automatically if Traveler's Titles mod is loaded)
- Always announce structures even if they don't have any bonuses
- Disable Structure announcement by default if Structure Credits mod is loaded
- Redo client config title offsets
- Various internal cleanup
- Fix attribute bonuses per level not refreshing on config change
- Removed NBT and manual client cache, replaced with Neoforge Attachments

## 0.6.1
- Fix level-up items dropping from non-entities

## 0.6.0
- Added 5 new items, used to level up tamed pets (or any mob)
- Greatly improve player-based scaling framework
- Add player scaling/level tracking for Puffish Skills
- Make level display translatable
- Structure subtitle level bonuses now also display any player level bonus also applied to mobs
- Added several config values
- Added Jade compat
- Add vanilla structures to the default structure level bonus tags
- Added API methods for live-updating mob levels
- Add loot table and loot condition for modifying leveled mob loot
- At some point here I added more damage attributes. Maybe that was in 0.5.2. i'm losing my mind

## 0.5.2
- Fix structure subtitle not moving properly with title
- Add client config values for structure subtitle scaling, subtitle y offset

## 0.5.1
- Re-add translation keys for damage bonus attributes

## 0.5.0
- Add structure levels, structure tags, structure title renderer
- Mob levels now start at 1 instead of 0
- Re-add projectile_damage_bonus
- Improve damage modification method

## 0.4.1
- Fix incorrect multiplier logic causing low player damage

## 0.4.0
**BREAKING CHANGE: REQUIRES CONFIG RESET!**
- Renamed Projectile/Explosion damage attributes (You'll see some Unknown Attribute log messages, these will only happen once per entity and can be ignored)
- Changed projectile/explosion damage attributes to be multipliers
- Fix Ranged/Explosion damage bonuses not scaling properly with level
- Fix damage being modified even if it should have been blocked

## 0.3.1
- Fix incorrect id's for projectile/explosion damage bonus

## 0.3.0
- Add Apotheosis World Tier support to rendering
- Fix damage modifiers
- Change damage-based modifiers to be addition-based (using new config structure - regenerate your common config!)
- Various cleanup

## 0.2.0
- Fix attribute modifiers not applying correctly

## 0.1.0
- Initial Rewrite