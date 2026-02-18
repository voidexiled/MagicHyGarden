# Command Reference

This is the live command reference for MagicHyGarden gameplay and operations.

## Farm commands (`/farm`)
- `/farm help`
  - Concise in-game help with examples.

### Navigation and membership
- `/farm home`
  - Create/load your farm world and teleport to your farm spawn.
- `/farm farms`
  - List your owned farm and memberships.
- `/farm visit <ownerUuid|ownerName>`
  - Visit another player's farm (requires membership/invite acceptance).
- `/farm lobby`
  - Teleport to configured lobby world.
- `/farm survival`
  - Teleport to configured survival world.

### Spawn control
- `/farm spawn`
- `/farm spawn status`
  - Show current parcel spawn.
- `/farm spawn set`
  - Save your current position as parcel spawn (must be inside your own farm world).
- `/farm spawn reset`
  - Remove custom spawn and fallback to parcel center/safe spawn.
- `/farm setspawn`
  - Shortcut for `/farm spawn set`.

### Invite and roles
- `/farm invite <player>`
- `/farm invites`
- `/farm accept`
- `/farm deny`
- `/farm members`
- `/farm leave [ownerUuid|ownerName]`
- `/farm kick <player>`
- `/farm role <player> <owner|manager|member|visitor>`

### Economy/shop (player)
- `/farm balance`
- `/farm leaderboard [limit]`
- `/farm stock`
- `/farm perks [open|close|status|upgrade] [perkId]`
- `/farm shop [open|openv2|v2|close|text|hud]`
- `/farm buy <shopId> [qty]`
- `/farm buymax <shopId>`
- `/farm sell <shopId> [qty]`
- `/farm sellall [shopId|all]`
- `/farm log [show|clear] [limit]`

Example:
- `/farm shop open`
- `/farm shop openv2`
- `/farm perks`
- `/farm perks close`
- `/farm perks upgrade fertile_soil`
- `/farm perks upgrade sell_multiplier`
- `/farm buy lettuce 10`
- `/farm sell lettuce 1`
- `/farm sellall all`
- `/farm log show 50`

Perk ids currently supported:
- `fertile_soil`
- `sell_multiplier`

### Events (player/admin debug)
- `/farm event status`
- `/farm event worlds`
- `/farm event reload`
- `/farm event growth <true|false|reset> [serverEmpty true|false]`
- `/farm event weather <weatherId|clear> [force]`
- `/farm event list [weather|lunar|all]`
- `/farm event start <weather|lunar> <eventId|random> [durationSec]`
- `/farm event stop`

### Runtime reload
- `/farm reload [all|events|worlds|parcels|invites|economy|perks|shop|names]`

## Farm admin commands (`/farm admin`)
- `/farm admin status`
- `/farm admin paths`
- `/farm admin parcel list`
- `/farm admin parcel save`
- `/farm admin parcel reload`
- `/farm admin parcel info [player]`
- `/farm admin world status`
- `/farm admin world backupall`
- `/farm admin world backup <target>`
- `/farm admin world restore <target>`
- `/farm admin world ensure <target>`
- `/farm admin stock status`
- `/farm admin stock restock`
- `/farm admin stock set <shopId> <qty>`
- `/farm admin economy set <target> <amount>`
- `/farm admin economy add <target> <amount>`
- `/farm admin economy subtract <target> <amount>`
- `/farm admin perks status [target]`
- `/farm admin perks setlevel <target> <level>`
- `/farm admin perks recalc [target]`

Examples:
- `/farm admin economy set self 2000`
- `/farm admin world backup Voidexiled`
- `/farm admin world restore 0eea1f01-3c65-4e08-925e-1656546864fb`
- `/farm admin stock set lettuce 50`
- `/farm admin economy add Voidexiled 500`
- `/farm admin economy subtract Voidexiled 250`
- `/farm admin perks status self`
- `/farm admin perks setlevel Voidexiled 3`
- `/farm admin perks recalc Voidexiled`

`target` accepts `self`, UUID, or cached/online player name.

## Crop commands (`/crop`)

### Reload
- `/crop reload [all|crops|mutations|growth]`

### Manual mutate (target block in crosshair)
- `/crop add mutation --name=<rain|snow|frozen|none>`
- `/crop add lunar --name=<dawnlit|dawnbound|amberlit|amberbound|none>`
- `/crop add rarity --name=<gold|rainbow|none>`

### Growth/size tools
- `/crop grow [percent]`
  - Force growth progress (0..100) on targeted MGHG crop.
- `/crop set size [value]`
  - Set MGHG `size` metadata on targeted crop.

### Debug
- `/crop debug target`
  - Prints block, farming, and MGHG data for targeted block.
- `/crop debug held`
  - Prints held item metadata (including `MghgCropMeta` when present).
- `/crop debug rules`
  - Prints loaded mutation rules summary.
- `/crop debug test`
  - Spawns debug item stack for quick metadata testing.

## Notes
- Use exact `shopId` values from `/farm stock` for buy/sell commands.
- Use `/farm reload ...` after config changes in `Server/Farming/*`.
- Use `/crop reload ...` after mutation/growth/crop registry edits.
