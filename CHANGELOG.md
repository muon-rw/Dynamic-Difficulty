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