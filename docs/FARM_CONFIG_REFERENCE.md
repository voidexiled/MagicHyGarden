# Farm Config Reference

This document is the operational reference for all farm-related JSON configs loaded from `src/main/resources/Server/Farming`.

## Load/Reload
- Runtime source: files are loaded from the built assets path (`build/resources/main/...`) when the server runs.
- Use `/farm reload ...` and `/crop reload ...` to apply changes without restarting.
- If you edit source files under `src/main/resources`, mirror/sync to runtime assets before testing.

## `Server/Farming/Farms/Mghg_FarmConfig.json`
- `FarmWorldNamePrefix` (`string`): Prefix used to identify farm worlds (example: `MGHG_Farm_`).
- `LobbyWorldName` (`string`): Lobby world target for `/farm lobby`.
- `SurvivalWorldName` (`string`): Survival world target for `/farm survival`.
- `WorldGenProvider` (`string`): World generator provider id for farm worlds.
- `ChunkStorageProvider` (`string`): Chunk storage backend id.
- `CreationMode` (`string`): `Generator` (default) or `TemplateCopy`.
- `TemplateWorldPath` (`string`): Source world folder used when `CreationMode=TemplateCopy`.
  - Absolute path works directly.
  - Relative path resolves from server root (parent of `universe/`).
- `TemplateRequireValidConfig` (`bool`): If true, template folder must contain `config.json`/`config.bson`.
- `TemplateCopyRetries` (`int`): Template copy attempts before fallback to generator mode.
- `IsPvpEnabled` (`bool`): Enables/disables PvP in farm worlds.
- `IsSpawningNPC` (`bool`): Enables/disables NPC spawning in farm worlds.
- `EnableFullInstancePersistence` (`bool`): Enables world-folder snapshot persistence.
- `FullInstanceBackupIntervalSeconds` (`int`): Snapshot interval in seconds.
- `BackupWorldsPerTick` (`int`): Number of farm worlds processed per snapshot tick.
- `ParcelSizeX/Y/Z` (`int`): Default parcel bounds metadata (spawn fallback center + admin/debug context).
- `FarmSpawnX/Y/Z` (`int`): Fallback farm spawn (safe fallback if computed spawn is invalid).
- `FarmSpawnOriginGuardRadius` (`int`): Prevents spawn too close to origin hotspot.

## `Server/Farming/Events/Mghg_Events.json`
- `ClearWeatherId` (`string`): Weather forced when no event is active.
- `ApplyToAllWorldsIfEmpty` (`bool`): If true, applies event weather to all worlds when no farm worlds are loaded.
- `FarmWorldNamePrefix` (`string`): Event scheduler scope for farm worlds.
- `AllowMutationsWhenOwnerOffline` (`bool`): Mutation rolls allowed when parcel owner is offline.
- `AllowGrowthWhenOwnerOffline` (`bool`): Growth ticking allowed when parcel owner is offline.
- `AllowGrowthWhenServerEmpty` (`bool`): Growth ticking allowed when server has no players online.
- `OfflineMutationChanceMultiplier` (`double`): Multiplier applied to mutation chance when owner is offline.
- `Regular` / `Lunar` (`object`): Event groups.
  - `OccurrenceChance` (`double`): Chance a cycle starts an event (`0..1` or `0..100` accepted).
  - `IntervalMinSeconds`, `IntervalMaxSeconds` (`int`): Next cycle roll window.
  - `DurationSeconds` (`int`): Event duration.
  - `Events[]`:
    - `Id` (`string`): Logical event id (used by admin commands).
    - `WeatherId` (`string`): Weather asset id to force while active.
    - `Weight` (`int`): Weighted random selection in group.

## `Server/Farming/Shop/Mghg_Shop.json`
- `RestockIntervalMinSeconds`, `RestockIntervalMaxSeconds` (`int`): Restock cycle window.
- `RequireFarmWorldForTransactions` (`bool`): Buy/sell only from farm worlds.
- `RequireParcelAccessForTransactions` (`bool`): Buy/sell only with parcel build access (role-based, not by position bounds).
- `RequireBenchProximityForTransactions` (`bool`): Buy/sell requires nearby bench block.
- `BenchSearchRadius` (`int`): Radius for bench lookup.
- `BenchBlockIds[]` (`string[]`): Accepted bench block ids.
- `Items[]`:
  - `Id` (`string`): Shop id used in commands/UI (`/farm buy <Id>`).
  - `BuyItemId` (`string`): Item granted by buy.
  - `SellItemIds[]` (`string[]`): Item ids accepted for selling.
  - `MinStock`, `MaxStock` (`int`): Positive restock quantity range.
  - `RestockChance` (`double`): Chance item gets positive stock in each cycle (`0..1` or `0..100`). If the roll fails, stock is `0`.
  - `BuyPrice` (`double`): Unit buy price.
  - `SellPrice` (`double`): Base unit sell price.
  - `EnableMetaSellPricing` (`bool`): Enables metadata-based multipliers.
  - `SellSizeMultiplierMinSize`, `SellSizeMultiplierMaxSize` (`double`): Size curve domain.
  - `SellSizeMultiplierAtMin`, `SellSizeMultiplierAtMax` (`double`): Size curve values.
  - `ClimateMultiplier*` (`double`): Climate multipliers.
  - `LunarMultiplier*` (`double`): Lunar multipliers.
  - `RarityMultiplier*` (`double`): Rarity multipliers.

Sell formula (current):
- `Unit = BaseSell x Rarity x Size x Climate x Lunar`
- `Stack = Unit x Quantity`

Stock semantics:
- Restock values are global per cycle, but consumption is personal per player per cycle.

## `Server/Farming/Economy/Mghg_Economy.json`
- `AutoCreateAccountOnFirstAccess` (`bool`): Creates account lazily on first economy read.
- `StartingBalance` (`double`): Initial balance for first-time accounts.

## `Server/Farming/Perks/Mghg_Perks.json`
- `BaseLevel` (`int`): Default fertile-soil perk level for new parcel perk state.
- `ReconcileIntervalSeconds` (`int`): Interval used by stale tracked-fertile reconciliation task.
- `FertileSoilLevels[]`:
  - `Level` (`int`): Perk level id.
  - `MaxFertileBlocks` (`int`): Max tracked fertile blocks allowed at this level.
  - `UpgradeCost` (`double`): Economy cost to upgrade into this level.
- `SellMultiplierLevels[]`:
  - `Level` (`int`): Perk level id.
  - `Multiplier` (`double`): Final sell-value multiplier applied by shop transactions.
  - `UpgradeCost` (`double`): Economy cost to upgrade into this level.
- `FertileSoilRules`:
  - `TillSourceBlockIds[]` (`string[]`): Blocks that can be hoed into fertile soil (cap checked before conversion).
  - `FertileBaseBlockIds[]` (`string[]`): Blocks counted as fertile for cap tracking.
  - `AllowedSeedSoilBaseBlockIds[]` (`string[]`): Shared allowlist for seed-soil compatibility metadata. Intended to match your seed interaction/crop support assets.
  - `HoeItemIds[]` (`string[]`): Exact hoe item ids explicitly allowed to till in farm parcel worlds.
  - `HoeItemIdPrefixes[]` (`string[]`): Optional prefix-based allowlist (leave empty to force exact-id only, e.g. only `Tool_Hoe_Custom`).

## `Server/Farming/Crops/Mghg_Crops.json`
- `Definitions[]`:
  - `Id` (`string`): Logical crop id.
  - `BlockId` (`string`): Crop block asset id.
  - `ItemId` (`string`): Harvestable crop item asset id.
  - `BaseWeightGrams` (`double`): Base weight metadata value.
  - `GrowTimeSeconds` (`int`, optional): Approximate full grow time (seed to mature) shown in seed tooltips/UI.

## `Server/Farming/Modifiers/Size.json`
- `Type` (`string`): Modifier asset type id (must match expected type).
- `Modifier` (`double`): Generic growth modifier scalar.
- `RainWeathers[]` (`string[]`): Weather ids treated as rain.
- `SnowWeathers[]` (`string[]`): Weather ids treated as snow.
- `MutationChanceSnow`, `MutationChanceRain` (`double`): Legacy chance controls.
- `MutationRollCooldownSeconds` (`int`): Fallback cooldown for mutation rules without explicit cooldown.
- `DropListOverrideFrom[]`, `DropListOverrideTo[]` (`string[]`): Optional droplist remapping.
- `ExtraDropListsGold[]`, `ExtraDropListsRainbow[]` (`string[]`): Extra drops by rarity.

## `Server/Farming/Mutations/Mghg_Mutations.json`
- `CooldownClock` (`string`): Clock mode for cooldown computation (`RealTime` recommended).
- `Rules[]`: Mutation rules list.

Each rule supports many fields (`EventType`, `Slot`, `Set`, `Chance`, `Priority`, adjacency/light/time filters, etc.).  
Full rule-by-rule field reference is maintained in:
- `docs/MUTATION_RULES.md`

## `Server/Farming/Visuals/Mghg_MutationVisuals.json`
- `Defaults.BlockType.*`: Default visual overrides (sound ids, particle color defaults, etc.).
- `Overrides.<mutationKey>.BlockType.*`: Per-mutation visual overrides.
- Mutation keys must match your state naming conventions (example: `mghg_rain`, `mghg_frozen`).

## Runtime data files (not source config)
- `run/mghg/economy.json`: Persistent balances.
- `run/mghg/shop_stock.json`: Current global cycle stock data.
- `run/mghg/shop_ui_logs.json`: Per-player shop activity logs.
- `run/mghg/player_names.json`: UUID/name cache.
- `run/mghg/parcels/*.parcel.json`: Parcel metadata/roles/spawn.
  - Includes `Perks.FertileSoilLevel` and tracked fertile block keys.
- `run/mghg/world_backups/*`: Farm world snapshots for full-instance persistence.
