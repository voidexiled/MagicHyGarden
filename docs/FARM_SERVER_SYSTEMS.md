# Farm Server Systems

This document specifies the server-side farm systems currently wired in MagicHyGarden.

Related references:
- `docs/FARM_CONFIG_REFERENCE.md` (field-by-field JSON config reference)
- `docs/COMMAND_REFERENCE.md` (all `/farm` and `/crop` commands with examples)
- `docs/MUTATION_RULES.md` (full mutation rule schema and advanced filters)

## Scope
- `Survival` worlds keep vanilla behavior.
- `Farm` worlds are detected by `FarmWorldNamePrefix` (default: `MGHG_Farm_`).
- Farm world creation supports two modes (`Mghg_FarmConfig.json`):
  - `CreationMode=Generator`: standard `addWorld(gen, storage)` flow.
  - `CreationMode=TemplateCopy`: copies a predefined world folder before load; falls back to generator if copy/validation fails.
- `Lobby` and `Survival` teleport targets can be configured with `LobbyWorldName` and `SurvivalWorldName`.
- MGHG crop systems (rehydrate, inspect HUD, farm-info HUD, harvest mutation loop) run only in farm worlds.
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
  - Parcel JSON persists owner/role/spawn metadata. Legacy `Blocks` payload is kept only for backward compatibility.
  - Parcel JSON now also persists perk state (`Perks`) including fertile level and tracked fertile block keys.
  - On startup (when full-instance persistence is enabled), legacy `Blocks` payload is auto-purged once world/backup data exists.
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
- Fertile-soil perk guard systems:
  - `MghgHoeTillInteraction` is now the authoritative hoe-till path (`Hoe_Till` + `Mghg_Hoe_Till`), enforcing custom-hoe-only behavior in farm worlds and applying perk/cap tracking directly on conversion.
  - `MghgFertileSoilUsePreSystem` checks cap before hoe conversion, blocks non-configured hoes, and resolves target-offset interactions (tracks around the clicked block).
  - `MghgFertileSoilUsePostSystem` tracks successful hoe conversions with a near-target scan fallback.
  - `MghgFertileSoilPlaceGuardSystem` prevents bypass by manually placing fertile blocks over cap.
  - `MghgFertileSoilBreakSystem` frees tracked slots on fertile block break.
  - `MghgFertileSoilReconcileService` periodically removes stale tracked entries and enforces current cap.
- Seed placement restriction is currently asset-driven (`Seed_Condition` / crop support tags), not Java-guard-driven.
  - For strict custom-soil-only planting, use a custom seed condition matcher by explicit block id (example: `Mghg_Soil_Dirt_Tilled`).
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
  - If roll fails, that item keeps `stock=0` for that cycle.

## Commands
- `/farm home`
- `/farm help`
- `/farm farms`
- `/farm lobby`
- `/farm survival`
- `/farm visit <ownerUuid|ownerName>`
- `/farm spawn`
- `/farm spawn status`
- `/farm spawn set`
- `/farm spawn reset`
- `/farm setspawn` (shortcut for `spawn set`)
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
- `/farm perks [open|close|status|upgrade] [fertile_soil|sell_multiplier]`
- `/farm shop [open|close|text|hud]`
- `/farm buy <shopId> [qty=1]`
- `/farm buymax <shopId>`
- `/farm sell <shopId> [qty=1]`
- `/farm sellall [shopId|all]`
- `/farm log [show|clear] [limit=30]`
- `/farm perks` opens the interactive perks page (`Pages/Mghg_FarmPerksPage.ui`).
- `/farm perks close` closes the perks page if it is currently open.
- Right-click on any configured bench block (`BenchBlockIds`) in farm worlds opens the interactive shop page (`Pages/Mghg_FarmShopPage.ui`).
- `/farm shop open` opens the interactive shop page; `/farm shop hud` opens the legacy HUD.
- `/farm shop close` closes both page and HUD if either is active.
- While the shop UI is open, it refreshes every second (restock timer, stock grids, and potential value).
- Successful `/farm buy`, `/farm buymax`, `/farm sell`, and `/farm sellall` operations trigger immediate UI refresh.
- Farm worlds keep a persistent right-side farm info HUD (farm title, balance, fertile soil usage, sell multiplier, mutation multiplier placeholder, members).
- Interactive page controls:
  - Buy tab: rootling-style seed cards (left click = buy 1, right/double click = buy max).
  - Sell tab: inventory-like grid with multi-select (`Select all`, `Clear`, `Sell selected`).
  - Potential value uses all stacks when no selection exists, and selected stacks when selection is active.
  - Logs are queried via `/farm log` instead of in-page activity panel.
- Item tooltip model:
  - Shop V2 no longer injects custom tooltip text in the UI widgets.
  - Tooltip details come from item-level metadata, so inventory and shop slot hovers stay consistent.
  - If `DynamicTooltipsLib` is installed, crop/seed pricing lines are appended dynamically using MGHG metadata + shop config.
- `/farm event [status|reload|worlds|growth|weather|list|start|stop]`
- `/farm event worlds` (lists farm worlds, owner online, players, ticking, block ticking, forced weather, parcel state)
- `/farm event growth <true|false|reset> [true|false]` (runtime override for owner-offline/server-empty growth gates)
- `/farm event weather <weatherId|clear> [force]` (force weather across farm worlds for debugging; `force` clears scheduler cache and reapplies)
- `/farm event list [weather|lunar|all]` (lists configured event ids/weights/weather ids)
- `/farm event start <weather|lunar> <eventId|random> [durationSec]` (force-start configured event)
- `/farm event stop` (force-stop active event and apply clear weather)
- `/farm reload [all|events|worlds|parcels|invites|economy|perks|names|shop]`
- `/farm admin status`
  - Global snapshot (worlds/parcels/event/shop timers).
- `/farm admin paths`
  - Prints active runtime data paths.
- `/farm admin parcel list|save|reload|info [player]`
  - `list`: lists parcels (truncated) for auditing.
  - `save` / `reload`: force parcel persistence operations.
  - `info [player]`: owner metadata (bounds, spawn, entries, file path).
- `/farm admin world status|backup <player>|backupall|restore <player>|ensure <player>`
  - `status`: inspect loaded farm worlds and snapshot availability.
  - `backup` / `backupall`: force world snapshots.
  - `restore`: force restore from snapshot (prune/recovery tests).
  - `ensure`: ensure world exists/loads for target owner.
- `/farm admin stock status|restock|set <shopId> <qty>`
  - `status`: per-item stock, prices, restock chance.
  - `restock`: force immediate cycle.
  - `set`: write direct global stock override.
- `/farm admin economy set|add|subtract <player> <amount>`
  - Balance debug/control.
- `/farm admin perks status [player]|setlevel <player> <level>|recalc [player]`
  - `status`: inspect level/cap/next upgrade.
  - `setlevel`: force target fertile level (clamped to configured levels).
  - `recalc`: force immediate stale-tracking reconciliation (target world must be loaded).

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
  3) Compute per-stack unit price (base + metadata modifiers) and apply parcel `SellMultiplier` perk.
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
  - Can require parcel build access (role-based).
  - Can optionally require bench proximity by scanning nearby blocks for ids in `BenchBlockIds`.
