package com.voidexiled.magichygarden.features.farming.state;

import com.hypixel.hytale.server.core.asset.type.weather.config.Weather;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

import javax.annotation.Nullable;
import java.util.Locale;
import java.util.Set;

public final class MghgWeatherIdUtil {
    private MghgWeatherIdUtil() {}

    public static int resolveWeatherIndex(@Nullable String weatherId) {
        if (weatherId == null || weatherId.isBlank()) {
            return Weather.UNKNOWN_ID;
        }
        int direct = Weather.getAssetMap().getIndex(weatherId);
        if (direct != Weather.UNKNOWN_ID) {
            return direct;
        }
        String alias = normalizeWeatherAlias(weatherId);
        if (alias == null) {
            return Weather.UNKNOWN_ID;
        }
        return Weather.getAssetMap().getIndex(alias);
    }

    public static void addWeatherIndex(IntOpenHashSet out, @Nullable String weatherId) {
        if (out == null || weatherId == null || weatherId.isBlank()) {
            return;
        }
        int direct = Weather.getAssetMap().getIndex(weatherId);
        if (direct != Weather.UNKNOWN_ID) {
            out.add(direct);
        }
        String alias = normalizeWeatherAlias(weatherId);
        if (alias == null || alias.equalsIgnoreCase(weatherId)) {
            return;
        }
        int aliasIndex = Weather.getAssetMap().getIndex(alias);
        if (aliasIndex != Weather.UNKNOWN_ID) {
            out.add(aliasIndex);
        }
    }

    public static void addWeatherKeys(Set<String> out, @Nullable String weatherId) {
        if (out == null || weatherId == null || weatherId.isBlank()) {
            return;
        }
        String key = normalizeWeatherKey(weatherId);
        if (key != null) {
            out.add(key);
        }
        String alias = normalizeWeatherAlias(weatherId);
        String aliasKey = normalizeWeatherKey(alias);
        if (aliasKey != null) {
            out.add(aliasKey);
        }
    }

    public static @Nullable String normalizeWeatherAlias(@Nullable String weatherId) {
        if (weatherId == null || weatherId.isBlank()) {
            return null;
        }
        if (weatherId.equalsIgnoreCase("Zone1_Rain")) {
            return "Mghg_Zone1_Rain";
        }
        if (weatherId.equalsIgnoreCase("Zone3_Snow")) {
            return "Mghg_Zone3_Snow";
        }
        if (weatherId.equalsIgnoreCase("Mghg_Zone1_Rain")) {
            return "Zone1_Rain";
        }
        if (weatherId.equalsIgnoreCase("Mghg_Zone3_Snow")) {
            return "Zone3_Snow";
        }
        return null;
    }

    public static @Nullable String normalizeWeatherKey(@Nullable String weatherId) {
        if (weatherId == null || weatherId.isBlank()) {
            return null;
        }
        return weatherId.trim().toLowerCase(Locale.ROOT);
    }
}
