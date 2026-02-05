package com.voidexiled.magichygarden.features.farming.logic;

import com.voidexiled.magichygarden.features.farming.components.MghgCropData;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class MghgSupportDropMetaCache {
    private static final long TTL_NANOS = TimeUnit.SECONDS.toNanos(5);
    private static final ConcurrentHashMap<Long, Pending> PENDING = new ConcurrentHashMap<>();

    private MghgSupportDropMetaCache() {
    }

    public static void queue(int x, int y, int z, @Nonnull MghgCropData data, @Nullable String expectedItemId) {
        long key = key(x, y, z);
        long expiresAt = System.nanoTime() + TTL_NANOS;
        MghgCropData snapshot = new MghgCropData(
                data.getSize(),
                data.getClimate(),
                data.getLunar(),
                data.getRarity(),
                data.getWeightGrams(),
                data.getLastRegularRoll(),
                data.getLastLunarRoll(),
                data.getLastSpecialRoll(),
                data.getLastMutationRoll()
        );
        PENDING.put(key, new Pending(snapshot, expectedItemId, expiresAt));
    }

    @Nullable
    public static Pending peek(int x, int y, int z) {
        long key = key(x, y, z);
        Pending pending = PENDING.get(key);
        if (pending == null) {
            return null;
        }
        if (System.nanoTime() > pending.expiresAtNanos) {
            PENDING.remove(key, pending);
            return null;
        }
        return pending;
    }

    public static void consume(int x, int y, int z, @Nonnull Pending pending) {
        PENDING.remove(key(x, y, z), pending);
    }

    private static long key(int x, int y, int z) {
        return (((long) x) << 42) ^ (((long) z) << 20) ^ (long) y;
    }

    public static final class Pending {
        public final MghgCropData data;
        @Nullable
        public final String expectedItemId;
        public final long expiresAtNanos;

        private Pending(@Nonnull MghgCropData data, @Nullable String expectedItemId, long expiresAtNanos) {
            this.data = data;
            this.expectedItemId = expectedItemId;
            this.expiresAtNanos = expiresAtNanos;
        }
    }
}
