## 0.6.0
- Greatly improve player-based scaling framework
- Add player scaling/level tracking for Puffish Skills
- Make level display translatable
- Structure titles now also display the player level bonus applied to mobs
- Added several config values
- Added Jade compat
- Add vanilla structures to the default structure level bonus tags

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