package com.voidexiled.magichygarden.features.farming.perks;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcel;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcelManager;
import com.voidexiled.magichygarden.features.farming.worlds.MghgFarmWorldManager;

import javax.annotation.Nullable;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class MghgFertileSoilReconcileService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static volatile ScheduledFuture<?> task;

    private MghgFertileSoilReconcileService() {
    }

    public static synchronized void start() {
        stop();
        int intervalSeconds = Math.max(5, MghgFarmPerkManager.getReconcileIntervalSeconds());
        task = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(
                MghgFertileSoilReconcileService::tickSafe,
                intervalSeconds,
                intervalSeconds,
                TimeUnit.SECONDS
        );
    }

    public static synchronized void restart() {
        start();
    }

    public static synchronized void stop() {
        ScheduledFuture<?> current = task;
        task = null;
        if (current != null) {
            current.cancel(false);
        }
    }

    private static void tickSafe() {
        try {
            tick();
        } catch (Exception e) {
            LOGGER.atWarning().log("[MGHG|PERKS] Reconcile tick failed: %s", e.getMessage());
        }
    }

    private static void tick() {
        Universe universe = Universe.get();
        if (universe == null) {
            return;
        }
        for (World world : universe.getWorlds().values()) {
            if (!MghgFarmWorldManager.isFarmWorld(world)) {
                continue;
            }
            UUID owner = MghgFarmWorldManager.getOwnerFromFarmWorld(world);
            if (owner == null) {
                continue;
            }
            MghgParcel parcel = MghgParcelManager.getByOwner(owner);
            if (parcel == null) {
                continue;
            }
            world.execute(() -> reconcileWorld(world, owner, parcel));
        }
    }

    public static CompletableFuture<Integer> reconcileOwnerNow(@Nullable UUID owner) {
        CompletableFuture<Integer> future = new CompletableFuture<>();
        if (owner == null) {
            future.complete(0);
            return future;
        }
        Universe universe = Universe.get();
        if (universe == null) {
            future.complete(0);
            return future;
        }
        String worldName = MghgFarmWorldManager.getFarmWorldName(owner);
        World world = universe.getWorld(worldName);
        if (world == null) {
            future.complete(0);
            return future;
        }
        MghgParcel parcel = MghgParcelManager.getByOwner(owner);
        if (parcel == null) {
            future.complete(0);
            return future;
        }
        world.execute(() -> {
            try {
                int removed = reconcileWorld(world, owner, parcel);
                future.complete(removed);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    private static int reconcileWorld(@Nullable World world, @Nullable UUID owner, @Nullable MghgParcel parcel) {
        if (world == null || owner == null || parcel == null) {
            return 0;
        }

        Set<String> tracked = MghgFarmPerkManager.snapshotTrackedFertileBlocks(parcel);
        if (tracked.isEmpty()) {
            return 0;
        }

        int removed = 0;
        LinkedHashSet<String> valid = new LinkedHashSet<>();
        for (String key : tracked) {
            Vector3i pos = MghgFarmPerkManager.parseBlockKey(key);
            if (pos == null) {
                removed++;
                continue;
            }
            if (!MghgFarmPerkManager.isFertileBaseBlock(world.getBlockType(pos.x, pos.y, pos.z))) {
                removed++;
                continue;
            }
            valid.add(key);
        }

        int cap = MghgFarmPerkManager.getFertileSoilCap(parcel);
        if (cap >= 0 && valid.size() > cap) {
            int toTrim = valid.size() - cap;
            var iterator = valid.iterator();
            while (iterator.hasNext() && toTrim > 0) {
                iterator.next();
                iterator.remove();
                removed++;
                toTrim--;
            }
        }

        if (removed <= 0) {
            return 0;
        }
        MghgFarmPerkManager.replaceTrackedFertileBlocks(parcel, valid);
        LOGGER.atInfo().log(
                "[MGHG|PERKS] Reconciled fertile tracking for %s in %s: removed=%d now=%d",
                owner,
                world.getName(),
                removed,
                valid.size()
        );
        return removed;
    }
}
