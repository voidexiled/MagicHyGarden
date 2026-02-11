package com.voidexiled.magichygarden.features.farming.parcels;

import com.hypixel.hytale.server.core.HytaleServer;
import com.voidexiled.magichygarden.features.farming.storage.MghgStoragePaths;

import java.nio.file.Path;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;

public final class MghgParcelManager {
    private static final MghgParcelManager INSTANCE = new MghgParcelManager();
    private static final long SAVE_DEBOUNCE_SECONDS = 1L;
    private static volatile ScheduledFuture<?> pendingSave;

    private volatile MghgParcelStore store;

    private MghgParcelManager() {
    }

    public static void load() {
        INSTANCE.store().load();
    }

    public static void save() {
        INSTANCE.store().save();
    }

    public static synchronized void saveSoon() {
        ScheduledFuture<?> current = pendingSave;
        if (current != null && !current.isDone()) {
            return;
        }
        pendingSave = HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
            try {
                save();
            } finally {
                pendingSave = null;
            }
        }, SAVE_DEBOUNCE_SECONDS, TimeUnit.SECONDS);
    }

    public static MghgParcel getByOwner(UUID owner) {
        return INSTANCE.store().getByOwner(owner);
    }

    public static MghgParcel getOrCreate(UUID owner, int originX, int originY, int originZ) {
        return INSTANCE.store().getOrCreate(owner, originX, originY, originZ);
    }

    public static Collection<MghgParcel> all() {
        return INSTANCE.store().all();
    }

    public static Path getStoreDirectory() {
        return INSTANCE.store().getActiveDirectory();
    }

    public static Path getOwnerFile(@Nullable UUID owner) {
        return INSTANCE.store().resolveOwnerFilePath(owner);
    }

    private MghgParcelStore store() {
        MghgParcelStore existing = store;
        if (existing != null) {
            return existing;
        }
        synchronized (this) {
            if (store == null) {
                Path directory = MghgStoragePaths.resolveInDataRoot("parcels");
                Path legacy = MghgStoragePaths.resolveInDataRoot("parcels.json");
                store = new MghgParcelStore(directory, legacy);
            }
            return store;
        }
    }
}
