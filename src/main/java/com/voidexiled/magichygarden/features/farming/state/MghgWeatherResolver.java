package com.voidexiled.magichygarden.features.farming.state;

import com.hypixel.hytale.builtin.weather.resources.WeatherResource;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.weather.config.Weather;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

import javax.annotation.Nullable;

public final class MghgWeatherResolver {
    private MghgWeatherResolver() {}

    public static final int MASK_RAIN = 1;
    public static final int MASK_SNOW = 2;

    public record WeatherSnapshot(boolean raining, boolean snowing) {
        public boolean hasAny() {
            return raining || snowing;
        }
    }

    /**
     * Convierte array de IDs string a set de índices de Weather (asset map).
     * Retorna null si no hay IDs válidos.
     */
    public static @Nullable IntOpenHashSet toWeatherIdSet(@Nullable String[] ids) {
        if (ids == null || ids.length == 0) return null;

        IntOpenHashSet set = new IntOpenHashSet(ids.length * 2);
        for (String id : ids) {
            int idx = Weather.getAssetMap().getIndex(id);
            if (idx != Weather.UNKNOWN_ID) {
                set.add(idx);
            }
        }

        return set.isEmpty() ? null : set;
    }

    /**
     * Retorna bitmask:
     *  - bit 0 (1): raining
     *  - bit 1 (2): snowing
     *
     * Notas:
     * - frozenWeathers se interpreta como "raining+snowing" simultáneo.
     * - si hay bloque sobre el crop (sky-check), se considera sin clima.
     */
    public static int resolveMask(
            Store<EntityStore> entityStore,
            BlockChunk blockChunk,
            int x, int y, int z,
            @Nullable IntSet rainWeatherIds,
            @Nullable IntSet snowWeatherIds,
            @Nullable IntSet frozenWeatherIds
    ) {
        if ((rainWeatherIds == null || rainWeatherIds.isEmpty())
                && (snowWeatherIds == null || snowWeatherIds.isEmpty())
                && (frozenWeatherIds == null || frozenWeatherIds.isEmpty())) {
            return 0;
        }

        // sky-check: si hay un bloque arriba que no sea aire ni el propio crop, no hay clima
        int cropId = blockChunk.getBlock(x, y, z);
        for (int ay = y + 1; ay < 320; ay++) {
            int above = blockChunk.getBlock(x, ay, z);
            if (above != 0 && above != cropId) {
                return 0;
            }
        }

        int weatherId = resolveWeatherId(entityStore, blockChunk, x, y, z);

        if (weatherId == Weather.UNKNOWN_ID) {
            return 0;
        }

        boolean isFrozen = frozenWeatherIds != null && frozenWeatherIds.contains(weatherId);
        boolean isRain = isFrozen || (rainWeatherIds != null && rainWeatherIds.contains(weatherId));
        boolean isSnow = isFrozen || (snowWeatherIds != null && snowWeatherIds.contains(weatherId));

        int mask = 0;
        if (isRain) mask |= MASK_RAIN;
        if (isSnow) mask |= MASK_SNOW;
        return mask;
    }

    public static WeatherSnapshot resolveSnapshot(
            Store<EntityStore> entityStore,
            BlockChunk blockChunk,
            int x, int y, int z,
            @Nullable IntSet rainWeatherIds,
            @Nullable IntSet snowWeatherIds,
            @Nullable IntSet frozenWeatherIds
    ) {
        int mask = resolveMask(entityStore, blockChunk, x, y, z, rainWeatherIds, snowWeatherIds, frozenWeatherIds);
        boolean raining = (mask & MASK_RAIN) != 0;
        boolean snowing = (mask & MASK_SNOW) != 0;
        return new WeatherSnapshot(raining, snowing);
    }

    public static int resolveWeatherId(
            Store<EntityStore> entityStore,
            BlockChunk blockChunk,
            int x, int y, int z
    ) {
        return resolveWeatherId(entityStore, blockChunk, x, y, z, false);
    }

    public static int resolveWeatherId(
            Store<EntityStore> entityStore,
            BlockChunk blockChunk,
            int x, int y, int z,
            boolean ignoreSkyCheck
    ) {
        if (!ignoreSkyCheck && !hasSkyAccess(blockChunk, x, y, z)) {
            return Weather.UNKNOWN_ID;
        }
        WeatherResource weatherResource = entityStore.getResource(WeatherResource.getResourceType());
        int forced = weatherResource.getForcedWeatherIndex();
        if (forced != Weather.UNKNOWN_ID) {
            return forced;
        }
        int envId = blockChunk.getEnvironment(x, y, z);
        return weatherResource.getWeatherIndexForEnvironment(envId);
    }

    private static boolean hasSkyAccess(BlockChunk blockChunk, int x, int y, int z) {
        int cropId = blockChunk.getBlock(x, y, z);
        for (int ay = y + 1; ay < 320; ay++) {
            int above = blockChunk.getBlock(x, ay, z);
            if (above != 0 && above != cropId) {
                return false;
            }
        }
        return true;
    }
}
