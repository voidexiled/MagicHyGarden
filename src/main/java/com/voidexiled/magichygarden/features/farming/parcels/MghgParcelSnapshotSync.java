package com.voidexiled.magichygarden.features.farming.parcels;

import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.voidexiled.magichygarden.features.farming.events.MghgFarmEventScheduler;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class MghgParcelSnapshotSync {
    private static final long TARGET_SYNC_DELAY_MILLIS = 800L;
    private static final Map<String, ScheduledFuture<?>> TARGET_SYNC_TASKS = new ConcurrentHashMap<>();

    private MghgParcelSnapshotSync() {
    }

    public static void scheduleRecapture(@Nullable World world, @Nullable MghgParcel parcel) {
        // Legacy API kept for compatibility. Sparse mode does not recapture full regions.
        if (world == null || parcel == null) {
            return;
        }
        MghgParcelManager.saveSoon();
    }

    public static void scheduleTargetSync(@Nullable World world, int worldX, int worldY, int worldZ) {
        if (world == null || !MghgFarmEventScheduler.isFarmWorld(world)) {
            return;
        }
        String worldName = world.getName();
        if (worldName == null || worldName.isBlank()) {
            return;
        }
        String key = worldName + "|" + worldX + "|" + worldY + "|" + worldZ;
        ScheduledFuture<?> previous = TARGET_SYNC_TASKS.remove(key);
        if (previous != null && !previous.isDone()) {
            previous.cancel(false);
        }
        ScheduledFuture<?> future = HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> world.execute(() -> {
            try {
                syncTargetBlock(world, worldX, worldY, worldZ);
            } finally {
                TARGET_SYNC_TASKS.remove(key);
            }
        }), TARGET_SYNC_DELAY_MILLIS, TimeUnit.MILLISECONDS);
        TARGET_SYNC_TASKS.put(key, future);
    }

    public static void syncTargetBlock(@Nullable World world, int worldX, int worldY, int worldZ) {
        if (world == null || !MghgFarmEventScheduler.isFarmWorld(world)) {
            return;
        }

        MghgParcel parcel = MghgParcelAccess.resolveParcel(world);
        if (parcel == null) {
            return;
        }
        MghgParcelBounds bounds = parcel.getBounds();
        if (bounds == null) {
            return;
        }

        MghgParcelBlocks blocks = parcel.getBlocks();
        if (blocks == null) {
            blocks = new MghgParcelBlocks();
            parcel.setBlocks(blocks);
        }

        String blockId = resolveBlockId(world.getBlockType(worldX, worldY, worldZ));
        if (blocks.setWorldBlock(bounds, worldX, worldY, worldZ, blockId)) {
            MghgParcelManager.saveSoon();
        }
    }

    private static @Nullable String resolveBlockId(@Nullable BlockType type) {
        if (type == null) {
            return "";
        }
        String id = type.getId();
        if (id == null) {
            return "";
        }
        return id.trim();
    }
}
