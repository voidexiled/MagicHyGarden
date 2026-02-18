package com.voidexiled.magichygarden.commands.farm.subcommands.admin.shared;

import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.voidexiled.magichygarden.features.farming.storage.MghgPlayerNameManager;
import org.jspecify.annotations.NonNull;

import javax.annotation.Nullable;
import java.util.Locale;
import java.util.UUID;

public final class FarmAdminCommandShared {
    private FarmAdminCommandShared() {
    }

    private static boolean isBlank(@Nullable String value) {
        return value == null || value.isBlank();
    }

    public static @Nullable UUID resolveUuid(@NonNull PlayerRef executor, @Nullable String token) {
        if (isBlank(token) || "self".equals(normalize(token))) {
            return executor.getUuid();
        }
        UUID resolved = MghgPlayerNameManager.resolveUuid(token);
        if (resolved != null) {
            return resolved;
        }
        return resolveOnlineOnly(token);
    }

    public static @Nullable UUID resolveOnlineOnly(@Nullable String token) {
        if (isBlank(token)) {
            return null;
        }
        String raw = token.trim();
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            // Fallback to online player name lookup.
        }
        Universe universe = Universe.get();
        if (universe == null) {
            return null;
        }
        for (PlayerRef playerRef : universe.getPlayers()) {
            if (playerRef == null || playerRef.getUsername() == null) {
                continue;
            }
            if (raw.equalsIgnoreCase(playerRef.getUsername())) {
                return playerRef.getUuid();
            }
        }
        return null;
    }


    public static String normalize(@Nullable String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }

    public static String formatDuration(long seconds) {
        if (seconds <= 0L) {
            return "0s";
        }
        long h = seconds / 3600L;
        long m = (seconds % 3600L) / 60L;
        long s = seconds % 60L;
        if (h > 0L) {
            return String.format(Locale.ROOT, "%dh %dm %ds", h, m, s);
        }
        if (m > 0L) {
            return String.format(Locale.ROOT, "%dm %ds", m, s);
        }
        return String.format(Locale.ROOT, "%ds", s);
    }

    public static double normalizeChance(double raw) {
        if (Double.isNaN(raw) || raw <= 0.0d) {
            return 0.0d;
        }
        double chance = raw > 1.0d ? (raw / 100.0d) : raw;
        if (chance < 0.0d) return 0.0d;
        if (chance > 1.0d) return 1.0d;
        return chance;
    }

    public static String fallback(@Nullable String raw, String fallback) {
        return isBlank(raw) ? fallback : raw;
    }
}
