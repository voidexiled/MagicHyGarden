# Farm Server Systems

This document specifies the server-side farm systems currently wired in MagicHyGarden.

Related references:
- `docs/FARM_CONFIG_REFERENCE.md` (field-by-field JSON config reference)
- `docs/COMMAND_REFERENCE.md` (all `/farm` and `/crop` commands with examples)
- `docs/MUTATION_RULES.md` (full mutation rule schema and advanced filters)

## Scope
- `Survival` worlds keep vanilla behavior.
- `Farm` worlds are detected by `FarmWorldNamePrefix` (default: `MGHG_Farm_`).
- `Lobby` and `Survival` teleport targets can be configured with `LobbyWorldName` and `SurvivalWorldName`.
- MGHG crop systems (rehydrate, inspect HUD, harvest mutation loop) run only in farm worlds.
- If `LobbyWorldName` / `SurvivalWorldName` are missing or not found, commands fallback to first matching non-farm world (`lobby`/`survival` name hint, then first non-farm world).

## Global Farm Events
- Config file: `Server/Farming/Events/Mghg_Events.json`.
- Scheduler class: `MghgFarmEventScheduler`.
- Two groups:
  - `Regular`: default every `20-30 min`, duration `5 min`.
  - `Lunar`: default every `4 h`, duration `10 min`, priority over regular.
  - Both groups support `OccurrenceChance` (`0-1` or `0-100`) so a cycle can skip starting an event.
- During an active event, forced weather is applied to all farm worlds.
- Weather forcing path:
  - `WeatherResource.setForcedWeather(...)` is the runtime source of truth.
  - `WorldConfig.ForcedWeather` is explicitly kept `null` for farm worlds to avoid invalid persisted ids after restart.
- Weather application is tracked per world, so new farm instances created mid-event receive the active weather on the next scheduler tick.
- Between events, `ClearWeatherId` is forced (if configured).
- When an event ends, overdue schedules are recalculated from "now" before the next start check.
  This prevents chaining a new event in the exact same tick and gives the weather system time to transition naturally.
- Backward compatibility aliases:
  - Scheduler first tries the exact configured weather id (preferred for `Mghg_*` custom weathers).
  - Legacy ids can be remapped both ways (`Zone1_Rain` <-> `Mghg_Zone1_Rain`, `Zone3_Snow` <-> `Mghg_Zone3_Snow`) if needed.
  - If a configured weather id is missing, scheduler falls back to `ClearWeatherId` (or clears forced weather).
- Offline mutation policy (configurable in `Mghg_Events.json`):
  - `AllowMutationsWhenOwnerOffline` controls whether mutation rolls execute when owner is offline.
  - `OfflineMutationChanceMultiplier` scales mutation chance while owner is offline.
- Offline growth policy (configurable in `Mghg_Events.json`):
  - `AllowGrowthWhenOwnerOffline` controls farm world ticking when owner is offline.
  - `AllowGrowthWhenServerEmpty` controls farm world ticking when no players are online.
  - Scheduler applies both `world.setTicking(...)` and `WorldConfig.setBlockTicking(...)`.
  - Runtime debug overrides are available via `/farm event growth ...` (without editing JSON).

## Parcels and Permissions
- Default parcel size: `1000x256x1000`.
- Farm spawn origin guard (config in `Server/Farming/Farms/Mghg_FarmConfig.json`):
  - `FarmSpawnOriginGuardRadius`: if resolved spawn is too close to origin (`abs(x/z) <= radius`), fallback is applied.
  - `FarmSpawnX`, `FarmSpawnY`, `FarmSpawnZ`: fallback spawn coordinates used to avoid particle desync hotspots near `0,0,0`.
- Data persisted per owner in `run/mghg/parcels/<owner-uuid>.parcel.json` (legacy migration fallback: `run/mghg/parcels.json`).
- Full-instance persistence (primary path):
  - Farm world folders are snapshotted into `run/mghg/world_backups/<farm-world-name>/`.
  - Snapshot tick runs periodically and covers both loaded and unloaded farm worlds found on disk.
  - Shutdown performs a final flush.
  - If `/world prune --confirm` removes a farm world folder, `/farm home` restores it from snapshot before loading.
- Parcel file persistence (metadata/roles):
  - Parcel role/bounds metadata and sparse delta entries are still persisted in parcel JSON.
  - Parcel custom spawn (`SpawnX/SpawnY/SpawnZ`) is persisted per owner and used by `/farm home` and `/farm visit`.
  - Full world snapshots are the source of truth for block-state continuity.
- Roles:
  - `OWNER`, `MANAGER`, `MEMBER`, `VISITOR`.
- Invite flow:
  - `/farm invite <player>` creates a pending invite (does not auto-add membership).
  - Target player can inspect pending invites with `/farm invites`.
  - Target player accepts with `/farm accept` or rejects with `/farm deny`.
  - Invites expire automatically (15 minutes).
  - Invites are persisted in `run/mghg/parcels/invites.json` and reloaded on server start.
- Visit policy:
  - `/farm visit <player>` requires you to be at least `MEMBER` in target parcel.
  - Unknown/uninitialized target parcel is denied (no implicit bootstrap on foreign visit).
- Build/break protection systems:
  - `MghgParcelPlaceGuardSystem`
  - `MghgParcelBreakGuardSystem`
  - `MghgParcelUseGuardSystem` (blocks interaction outside parcel or without build role)
- Build permission is role-based for the full farm world (`OWNER/MANAGER/MEMBER`), with owner-world override.

## Economy and Shop
- Economy persisted in `run/mghg/economy.json`.
- Player name cache persisted in `run/mghg/player_names.json`.
- Economy config in `Server/Farming/Economy/Mghg_Economy.json`.
  - `AutoCreateAccountOnFirstAccess`: auto-creates player account on first economy query.
  - `StartingBalance`: initial money for first-time players.
- Shop stock persisted in `run/mghg/shop_stock.json`.
- Shop activity logs persisted in `run/mghg/shop_ui_logs.json` (per player, command-accessible).
- Shop config: `Server/Farming/Shop/Mghg_Shop.json`.
- Per-item fields:
  - `Id` (shopId lógico para comandos)
  - `BuyItemId` (item entregado al comprar; fallback: `Id`)
  - `SellItemIds` (items aceptados al vender; fallback: `BuyItemId/Id`)
  - `MinStock`, `MaxStock`
  - `RestockChance` (`0-1` or `0-100`)
  - `BuyPrice`, `SellPrice`
  - `EnableMetaSellPricing`
  - `SellSizeMultiplierMinSize`, `SellSizeMultiplierMaxSize`
  - `SellSizeMultiplierAtMin`, `SellSizeMultiplierAtMax`
  - `ClimateMultiplier*`, `LunarMultiplier*`, `RarityMultiplier*`
- Sell pricing model:
  - `Unit = BaseSell x SizeMultiplier(size) x ClimateMultiplier x LunarMultiplier x RarityMultiplier`
  - `SizeMultiplier(size)` interpolates linearly between:
    - `SellSizeMultiplierMinSize` -> `SellSizeMultiplierAtMin`
    - `SellSizeMultiplierMaxSize` -> `SellSizeMultiplierAtMax`
  - `Weight` remains informational only; it no longer adds independent value to sell price.
- Stock semantics:
  - Restock se calcula una sola vez por ciclo (mismos números para todos).
  - Cada jugador tiene consumo propio por ciclo (`stock personal`), no se agota globalmente.
  - El estado de consumo por jugador se reinicia en cada restock.
- Transaction access fields:
  - `RequireFarmWorldForTransactions`
  - `RequireParcelAccessForTransactions`
  - `RequireBenchProximityForTransactions`
  - `BenchSearchRadius`
  - `BenchBlockIds`
- Restock:
  - Interval from `RestockIntervalMinSeconds/MaxSeconds`.
  - Each item is rolled independently using `RestockChance`.
  - If roll fails, that item is absent in that restock cycle.

## Commands
- `/farm home`
- `/farm farms`
- `/farm lobby`
- `/farm survival`
- `/farm visit <ownerUuid|ownerName>`
- `/farm spawn [status|set|reset]`
  - Shortcut: `/farm setspawn`
- `/farm invite <player>`
- `/farm invites`
- `/farm accept`
- `/farm deny`
- `/farm members`
- `/farm leave [ownerUuid|ownerNameOnline]`
- `/farm kick <player>`
- `/farm role <player> <role>`
- `/farm balance`
- `/farm leaderboard [limit=10]`
- `/farm stock`
- `/farm shop [open|close|text|hud]`
- `/farm buy <shopId> [qty=1]`
- `/farm buymax <shopId>`
- `/farm sell <shopId> [qty=1]`
- `/farm sellall [shopId|all]`
- `/farm log [show|clear] [limit=30]`
- Right-click on any configured bench block (`BenchBlockIds`) in farm worlds opens the interactive shop page (`Pages/Mghg_FarmShopPage.ui`).
- `/farm shop open` opens the interactive shop page; `/farm shop hud` opens the legacy HUD.
- `/farm shop close` closes both page and HUD if either is active.
- While the shop UI is open, it refreshes every second (balance, restock timer, stock grids, potential value, pricing details).
- Successful `/farm buy`, `/farm buymax`, `/farm sell`, and `/farm sellall` operations trigger immediate UI refresh.
- Interactive page controls:
  - Left panel: buy seeds grid with per-cycle personal stock.
  - Right panel: sellable inventory grid for selected seed, multi-select enabled.
  - Actions: `Buy 1`, `Buy 10`, `Buy Max`, `Select all`, `Unselect all`, `Sell 1`, `Sell selected`, `Sell all`.
  - Footer details show selected entry pricing model.
  - Hover tooltip on sell slots shows per-item valuation (unit/stack + multipliers + formula).
  - Logs are queried via `/farm log` instead of in-page activity panel.
- `/farm event [status|reload|worlds|growth|weather|list|start|stop]`
  - Help: `/farm event help`
- `/farm event worlds` (lists farm worlds, owner online, players, ticking, block ticking, forced weather, parcel state)
- `/farm event growth <true|false|reset> [true|false]` (runtime override for owner-offline/server-empty growth gates)
- `/farm event weather <weatherId|clear> [force]` (force weather across farm worlds for debugging; `force` clears scheduler cache and reapplies)
- `/farm event list [weather|lunar|all]` (lists configured event ids/weights/weather ids)
- `/farm event start <weather|lunar> <eventId|random> [durationSec]` (force-start configured event)
- `/farm event stop` (force-stop active event and apply clear weather)
- `/farm reload [all|events|worlds|parcels|invites|economy|names|shop]`
- `/farm admin [status|paths|parcel|world|stock|economy|sim] [value1] [value2] [value3]`
  - Help: `/farm admin help`
  - `status`: global snapshot (worlds/parcels/event/shop timers)
  - `paths`: prints active runtime data paths
  - `parcel <self|player|uuid|list|save|reload>`:
    - target owner metadata (bounds, spawn, entries, file path)
    - list all parcels (truncated) for auditing
    - force save/reload of parcel store
  - `world <status|list|backup|backup-all|restore|ensure> [self|player|uuid]`:
    - inspect loaded farm worlds and snapshot availability
    - force world snapshot for one owner or all owners
    - force restore from snapshot (for prune/recovery tests)
    - ensure world exists/loads for target owner
  - `stock status|restock|set <shopId> <qty>`:
    - status prints per-item global stock, prices, restock chance
    - restock forces immediate cycle
    - set writes a direct stock override
  - `economy <self|player|uuid> [status|set|add|sub] [amount]`: balance debug/control
  - `sim <create|list|tp> ...`:
    - `create <name> [count]`: creates deterministic simulated owners (`sim_*`) and ensures their farm worlds/parcels
    - creator gets `MANAGER` role in those parcels for immediate build/debug access
    - `list`: prints simulated parcel owners/worlds/spawns
    - `tp <name|uuid>`: teleports admin to simulated farm spawn (and grants manager if needed)

## Buy/Sell Safety
- Buy flow is rollback-safe:
  1) Validate balance, stock, inventory capacity.
  2) Withdraw money.
  3) Consume player stock for current cycle.
  4) Add item to inventory.
  5) If step 4 fails, refund money and restore stock.
- BuyMax flow:
  1) Resolve `shopId`, stock personal, and max affordable quantity.
  2) Resolve max inventory-fit quantity with binary search over `canAddItemStack`.
  3) Execute the same rollback-safe buy transaction with resolved max quantity.
- Sell flow:
  1) Resolve `shopId` and accepted `SellItemIds`.
  2) Scan inventory slots and collect sell selections.
  3) Compute per-stack unit price (base + metadata modifiers).
  4) Remove selected stacks.
  5) Deposit economy gain.
  6) If removal fails mid-flow, restore removed stacks (rollback).
- SellAll flow:
  1) Resolve target `shopId` or all sellable shop items.
  2) Remove all matching stacks from inventory with metadata-aware pricing.
  3) Deposit aggregated gain once.
  4) If any removal fails mid-flow, restore all removed stacks (rollback).
- Buy/Sell access policy:
  - Can be restricted to farm worlds only.
  - Can require parcel build access and player position inside parcel bounds.
  - Can optionally require bench proximity by scanning nearby blocks for ids in `BenchBlockIds`.
