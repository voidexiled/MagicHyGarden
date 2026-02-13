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
  - Save your current position as parcel spawn (must be inside your own farm).
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
- `/farm shop [open|close|text|hud]`
- `/farm buy <shopId> [qty]`
- `/farm buymax <shopId>`
- `/farm sell <shopId> [qty]`
- `/farm sellall [shopId|all]`
- `/farm log [show|clear] [limit]`

Example:
- `/farm shop open`
- `/farm buy lettuce 10`
- `/farm sell lettuce 1`
- `/farm sellall all`
- `/farm log show 50`

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
- `/farm reload [all|events|worlds|parcels|invites|economy|shop|names]`

## Farm admin commands (`/farm admin`)
- `/farm admin status`
- `/farm admin paths`
- `/farm admin parcel <self|player|uuid|list|save|reload>`
- `/farm admin world <status|list|backup|backup-all|restore|ensure> [self|player|uuid]`
- `/farm admin stock <status|restock|set> [shopId] [qty]`
- `/farm admin economy <self|player|uuid> [status|set|add|sub] [amount]`
- `/farm admin sim <create|list|tp> [name] [count]`

Examples:
- `/farm admin world backup self`
- `/farm admin world restore 0eea1f01-3c65-4e08-925e-1656546864fb`
- `/farm admin stock set lettuce 50`
- `/farm admin economy self add 500`
- `/farm admin sim create qa 5`
- `/farm admin sim tp sim_qa_01`

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
