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

Snapshot scheduler details:

- Backups do **not** run immediately right after a farm world is loaded (grace window) to avoid file-lock races on Windows.
- Backups skip farm worlds with players inside.
- Only one backup per farm world can run at a time.
- World open/restore (`/farm home`) and snapshot copy now share a per-world file lock.
- If a farm world is currently opening, snapshot for that world is skipped for that tick.
- Shutdown still performs a final backup pass.

These constraints are intentional to avoid `region.bin` lock crashes (`FileSystemException`) during `/farm home` after prune.

## Parcel persistence model (metadata-first)

Parcel files are metadata-first:

- Active fields: owner/roles/spawn/bounds metadata.
- Active fields also include `Perks` (fertile level + tracked fertile block keys).
- Legacy `Blocks` payload is read only for backward compatibility.
- When full-instance persistence is enabled and world/backup data exists, legacy `Blocks` is auto-purged on startup.
- `bounds` are metadata for spawn/origin and role scoping, not a hard edit limit.

Block-state continuity is handled by full world snapshot/restore, not by parcel JSON block volume data.
