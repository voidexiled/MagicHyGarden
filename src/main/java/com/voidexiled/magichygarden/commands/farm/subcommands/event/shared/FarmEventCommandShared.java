package com.voidexiled.magichygarden.commands.farm.subcommands.event.shared;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.voidexiled.magichygarden.features.farming.events.MghgFarmEventConfig;
import com.voidexiled.magichygarden.features.farming.events.MghgFarmEventScheduler;
import com.voidexiled.magichygarden.features.farming.events.MghgGlobalFarmEventState;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcelManager;
import com.voidexiled.magichygarden.features.farming.state.MutationEventType;
import com.voidexiled.magichygarden.features.farming.worlds.MghgFarmWorldManager;
import org.jspecify.annotations.NonNull;

import javax.annotation.Nullable;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

public final class FarmEventCommandShared {
    private FarmEventCommandShared() {
    }

    public static void sendStatus(@NonNull CommandContext ctx) {
        Instant now = Instant.now();
        MghgGlobalFarmEventState state = MghgFarmEventScheduler.getState();

        if (state != null && state.isActive(now)) {
            long remaining = secondsBetween(now, state.endsAt());
            String eventId = fallback(state.eventId(), "-");
            String weatherId = fallback(state.weatherId(), "-");
            ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text(String.format(
                    Locale.ROOT,
                    "Evento activo: %s | id=%s | weather=%s | remaining=%s",
                    state.eventType(),
                    eventId,
                    weatherId,
                    formatDuration(remaining)
            )));
        } else {
            ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("Evento activo: none"));
        }

        long nextRegular = secondsBetween(now, MghgFarmEventScheduler.getNextRegularAt());
        long nextLunar = secondsBetween(now, MghgFarmEventScheduler.getNextLunarAt());
        ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text(String.format(
                Locale.ROOT,
                "Next regular=%s | next lunar=%s",
                formatDuration(nextRegular),
                formatDuration(nextLunar)
        )));

        MghgFarmEventConfig cfg = MghgFarmEventScheduler.getConfig();
        if (cfg == null) {
            return;
        }

        String prefix = fallback(cfg.getFarmWorldNamePrefix(), "-");
        String clearWeather = fallback(cfg.getClearWeatherId(), "none");
        double regularChance = normalizeChance(cfg.getRegular().getOccurrenceChance()) * 100.0d;
        double lunarChance = normalizeChance(cfg.getLunar().getOccurrenceChance()) * 100.0d;
        double offlineMultiplier = cfg.getOfflineMutationChanceMultiplier();
        ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text(String.format(
                Locale.ROOT,
                "Scope prefix=%s | clearWeather=%s",
                prefix,
                clearWeather
        )));
        ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text(String.format(
                Locale.ROOT,
                "Occurrence chance regular=%.2f%% | lunar=%.2f%%",
                regularChance,
                lunarChance
        )));
        ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text(String.format(
                Locale.ROOT,
                "Offline mutation policy: allow=%s | chanceMultiplier=%.2f",
                cfg.isAllowMutationsWhenOwnerOffline(),
                offlineMultiplier
        )));
        ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text(String.format(
                Locale.ROOT,
                "Offline growth policy: ownerOffline=%s | serverEmpty=%s",
                cfg.isAllowGrowthWhenOwnerOffline(),
                cfg.isAllowGrowthWhenServerEmpty()
        )));
        ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text(String.format(
                Locale.ROOT,
                "Growth overrides: ownerOffline=%s | serverEmpty=%s",
                formatNullableBoolean(MghgFarmEventScheduler.getGrowthWhenOwnerOfflineOverride()),
                formatNullableBoolean(MghgFarmEventScheduler.getGrowthWhenServerEmptyOverride())
        )));
    }

    public static void sendWorlds(@NonNull CommandContext ctx) {
        Universe universe = Universe.get();
        if (universe == null) {
            ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("Universe no disponible."));
            return;
        }

        int totalFarmWorlds = 0;
        for (World world : universe.getWorlds().values()) {
            if (!MghgFarmEventScheduler.isFarmWorld(world)) {
                continue;
            }
            totalFarmWorlds++;
            UUID owner = MghgFarmWorldManager.getOwnerFromFarmWorld(world);
            boolean ownerOnline = MghgFarmEventScheduler.isFarmOwnerOnline(world);
            boolean hasParcel = owner != null && MghgParcelManager.getByOwner(owner) != null;
            String forcedWeather = fallback(world.getWorldConfig().getForcedWeather(), "none");
            String ownerLabel = owner == null ? "-" : owner.toString();
            int players = world.getPlayerRefs().size();
            boolean growthAllowedNow = MghgFarmEventScheduler.isGrowthAllowedForWorld(world);
            ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text(String.format(
                    Locale.ROOT,
                    "%s | owner=%s | ownerOnline=%s | players=%d | growthAllowed=%s | ticking=%s | blockTicking=%s | forcedWeather=%s | parcel=%s",
                    fallback(world.getName(), "(unnamed)"),
                    ownerLabel,
                    ownerOnline,
                    players,
                    growthAllowedNow,
                    world.isTicking(),
                    world.getWorldConfig().isBlockTicking(),
                    forcedWeather,
                    hasParcel
            )));
        }
        if (totalFarmWorlds == 0) {
            ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("No hay farm worlds activos."));
            return;
        }
        ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text(String.format(
                Locale.ROOT,
                "Farm worlds: %d | growthOverride ownerOffline=%s serverEmpty=%s",
                totalFarmWorlds,
                formatNullableBoolean(MghgFarmEventScheduler.getGrowthWhenOwnerOfflineOverride()),
                formatNullableBoolean(MghgFarmEventScheduler.getGrowthWhenServerEmptyOverride())
        )));
    }

    public static void sendEventGroupList(
            @NonNull CommandContext ctx,
            @NonNull String label,
            @Nullable MghgFarmEventConfig.EventGroup group
    ) {
        if (group == null || group.getEvents() == null || group.getEvents().length == 0) {
            ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text(label + ": (sin eventos)"));
            return;
        }
        ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text(String.format(
                Locale.ROOT,
                "%s interval=%ds-%ds duration=%ds occurrence=%.2f%%",
                label,
                group.getIntervalMinSeconds(),
                group.getIntervalMaxSeconds(),
                group.getDurationSeconds(),
                normalizeChance(group.getOccurrenceChance()) * 100.0d
        )));
        for (MghgFarmEventConfig.EventDefinition definition : group.getEvents()) {
            if (definition == null) {
                continue;
            }
            ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text(String.format(
                    Locale.ROOT,
                    " - id=%s | weather=%s | weight=%d",
                    fallback(definition.getId(), "-"),
                    fallback(definition.getWeatherId(), "-"),
                    Math.max(1, definition.getWeight())
            )));
        }
    }

    public static long secondsBetween(@NonNull Instant now, @Nullable Instant target) {
        if (target == null) {
            return 0L;
        }
        return Math.max(0L, Duration.between(now, target).getSeconds());
    }

    public static @NonNull String normalize(@Nullable String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }

    public static @NonNull String raw(@Nullable String value) {
        return value == null ? "" : value.trim();
    }

    public static @NonNull String fallback(@Nullable String raw, @NonNull String fallback) {
        return (raw == null || raw.isBlank()) ? fallback : raw;
    }

    public static @NonNull String formatDuration(long seconds) {
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
        if (chance < 0.0d) {
            return 0.0d;
        }
        if (chance > 1.0d) {
            return 1.0d;
        }
        return chance;
    }

    public static @Nullable Boolean parseBoolean(@Nullable String raw) {
        return switch (normalize(raw)) {
            case "true", "1", "on", "yes", "y" -> Boolean.TRUE;
            case "false", "0", "off", "no", "n" -> Boolean.FALSE;
            default -> null;
        };
    }

    public static @NonNull MutationEventType parseEventType(@Nullable String raw) {
        String token = normalize(raw);
        return switch (token) {
            case "weather", "regular", "rain", "snow" -> MutationEventType.WEATHER;
            case "lunar", "moon", "dawn", "amber" -> MutationEventType.LUNAR;
            case "", "any", "all" -> MutationEventType.ANY;
            default -> MutationEventType.ANY;
        };
    }

    public static @Nullable Integer parsePositiveInt(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            return value > 0 ? value : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public static @NonNull String formatNullableBoolean(@Nullable Boolean value) {
        return value == null ? "default" : value.toString();
    }
}
