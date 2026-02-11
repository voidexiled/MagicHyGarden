# Persistence Paths (Farm/Economy/Shop)

MagicHyGarden now writes runtime persistence under the plugin data directory:

- `<pluginDataDirectory>/mghg/parcels/*.parcel.json`
- `<pluginDataDirectory>/mghg/parcels/invites.json`
- `<pluginDataDirectory>/mghg/shop_stock.json`
- `<pluginDataDirectory>/mghg/economy.json`
- `<pluginDataDirectory>/mghg/world_backups/<farm-world-name>/...`

`pluginDataDirectory` is provided by Hytale (`PluginBase#getDataDirectory()`), so this works in dedicated server environments regardless of current working directory.

## Why `run/run/mghg` appeared before

Older code mixed relative paths like `run/mghg/...` with runtime working directory resolution.  
If the process started inside `run/`, `run/mghg` became `run/run/mghg`.

## Migration behavior

On load, managers try the new primary path first, then legacy paths:

- `./mghg/...`
- `./run/mghg/...`
- `./data/mghg/...`

Legacy locations are read for migration compatibility, but new writes go to the plugin data directory path.

## Farm instance persistence model

Farm instances now use full-world snapshots as the primary persistence path:

- Source of truth: `run/universe/worlds/<farm-world-name>/...`.
- Snapshot mirror: `<pluginDataDirectory>/mghg/world_backups/<farm-world-name>/...`.
- Restore strategy: if `/world prune --confirm` removes a farm world folder, `/farm home` restores it from snapshot before loading.

This persistence is independent of place/break hooks and runs on a periodic scheduler plus shutdown flush.

## Parcel persistence model (legacy/metadata)

Parcel block persistence is now sparse (delta-based), not full-volume snapshots:

- Only modified blocks are saved (`Blocks.Entries`), relative to parcel origin.
- Entries are kept for compatibility/migration, but full world backup/restore is the primary persistence path.
- `bounds` are now metadata for spawn/origin and role scoping, not a hard edit limit.

This avoids losing farm changes after world prune and scales better than serializing a full 3D volume.
