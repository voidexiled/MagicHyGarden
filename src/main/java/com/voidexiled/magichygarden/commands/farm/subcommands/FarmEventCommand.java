package com.voidexiled.magichygarden.commands.farm.subcommands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.DefaultArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.voidexiled.magichygarden.features.farming.events.MghgFarmEventConfig;
import com.voidexiled.magichygarden.features.farming.events.MghgFarmEventScheduler;
import com.voidexiled.magichygarden.features.farming.events.MghgGlobalFarmEventState;
import com.voidexiled.magichygarden.features.farming.state.MutationEventType;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcelManager;
import com.voidexiled.magichygarden.features.farming.worlds.MghgFarmWorldManager;
import org.jspecify.annotations.NonNull;

import javax.annotation.Nullable;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

public class FarmEventCommand extends AbstractPlayerCommand {
    private final DefaultArg<String> actionArg;
    private final DefaultArg<String> value1Arg;
    private final DefaultArg<String> value2Arg;
    private final DefaultArg<String> value3Arg;

    public FarmEventCommand() {
        super("event", "magichygarden.command.farm.event.description");
        this.actionArg = withDefaultArg(
                "action",
                "magichygarden.command.farm.event.args.action.description",
                ArgTypes.STRING,
                "status",
                "status"
        );
        this.value1Arg = withDefaultArg(
                "value1",
                "magichygarden.command.farm.event.args.value1.description",
                ArgTypes.STRING,
                "",
                ""
        );
        this.value2Arg = withDefaultArg(
                "value2",
                "magichygarden.command.farm.event.args.value2.description",
                ArgTypes.STRING,
                "",
                ""
        );
        this.value3Arg = withDefaultArg(
                "value3",
                "magichygarden.command.farm.event.args.value3.description",
                ArgTypes.STRING,
                "",
                ""
        );
    }

    @Override
    protected void execute(
            @NonNull CommandContext ctx,
            @NonNull Store<EntityStore> store,
            @NonNull Ref<EntityStore> playerEntityRef,
            @NonNull PlayerRef playerRef,
            @NonNull World world
    ) {
        String action = normalize(actionArg.get(ctx));
        String value1 = raw(value1Arg.get(ctx));
        String value2 = raw(value2Arg.get(ctx));
        String value3 = raw(value3Arg.get(ctx));
        switch (action) {
            case "status" -> sendStatus(ctx);
            case "reload" -> {
                MghgFarmEventScheduler.reload();
                ctx.sendMessage(Message.raw("Farm events recargados."));
                sendStatus(ctx);
            }
            case "worlds" -> sendWorlds(ctx);
            case "growth" -> handleGrowth(ctx, value1, value2);
            case "weather" -> handleWeather(ctx, value1, value2);
            case "list" -> handleList(ctx, value1);
            case "start" -> handleStart(ctx, value1, value2, value3);
            case "stop" -> handleStop(ctx);
            default -> ctx.sendMessage(Message.raw(
                    "Accion invalida. Usa: status | reload | worlds | growth | weather | list | start | stop"
            ));
        }
    }

    private static void sendStatus(@NonNull CommandContext ctx) {
        Instant now = Instant.now();
        MghgGlobalFarmEventState state = MghgFarmEventScheduler.getState();

        if (state != null && state.isActive(now)) {
            long remaining = secondsBetween(now, state.endsAt());
            String eventId = fallback(state.eventId(), "-");
            String weatherId = fallback(state.weatherId(), "-");
            ctx.sendMessage(Message.raw(String.format(
                    Locale.ROOT,
                    "Evento activo: %s | id=%s | weather=%s | remaining=%s",
                    state.eventType(),
                    eventId,
                    weatherId,
                    formatDuration(remaining)
            )));
        } else {
            ctx.sendMessage(Message.raw("Evento activo: none"));
        }

        long nextRegular = secondsBetween(now, MghgFarmEventScheduler.getNextRegularAt());
        long nextLunar = secondsBetween(now, MghgFarmEventScheduler.getNextLunarAt());
        ctx.sendMessage(Message.raw(String.format(
                Locale.ROOT,
                "Next regular=%s | next lunar=%s",
                formatDuration(nextRegular),
                formatDuration(nextLunar)
        )));

        MghgFarmEventConfig cfg = MghgFarmEventScheduler.getConfig();
        if (cfg != null) {
            String prefix = fallback(cfg.getFarmWorldNamePrefix(), "-");
            String clearWeather = fallback(cfg.getClearWeatherId(), "none");
            double regularChance = normalizeChance(cfg.getRegular().getOccurrenceChance()) * 100.0d;
            double lunarChance = normalizeChance(cfg.getLunar().getOccurrenceChance()) * 100.0d;
            double offlineMultiplier = cfg.getOfflineMutationChanceMultiplier();
            ctx.sendMessage(Message.raw(String.format(
                    Locale.ROOT,
                    "Scope prefix=%s | clearWeather=%s",
                    prefix,
                    clearWeather
            )));
            ctx.sendMessage(Message.raw(String.format(
                    Locale.ROOT,
                    "Occurrence chance regular=%.2f%% | lunar=%.2f%%",
                    regularChance,
                    lunarChance
            )));
            ctx.sendMessage(Message.raw(String.format(
                    Locale.ROOT,
                    "Offline mutation policy: allow=%s | chanceMultiplier=%.2f",
                    cfg.isAllowMutationsWhenOwnerOffline(),
                    offlineMultiplier
            )));
            ctx.sendMessage(Message.raw(String.format(
                    Locale.ROOT,
                    "Offline growth policy: ownerOffline=%s | serverEmpty=%s",
                    cfg.isAllowGrowthWhenOwnerOffline(),
                    cfg.isAllowGrowthWhenServerEmpty()
            )));
            ctx.sendMessage(Message.raw(String.format(
                    Locale.ROOT,
                    "Growth overrides: ownerOffline=%s | serverEmpty=%s",
                    formatNullableBoolean(MghgFarmEventScheduler.getGrowthWhenOwnerOfflineOverride()),
                    formatNullableBoolean(MghgFarmEventScheduler.getGrowthWhenServerEmptyOverride())
            )));
        }
    }

    private static void sendWorlds(@NonNull CommandContext ctx) {
        Universe universe = Universe.get();
        if (universe == null) {
            ctx.sendMessage(Message.raw("Universe no disponible."));
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
            ctx.sendMessage(Message.raw(String.format(
                    Locale.ROOT,
                    "%s | owner=%s | ownerOnline=%s | players=%d | ticking=%s | blockTicking=%s | forcedWeather=%s | parcel=%s",
                    fallback(world.getName(), "(unnamed)"),
                    ownerLabel,
                    ownerOnline,
                    players,
                    world.isTicking(),
                    world.getWorldConfig().isBlockTicking(),
                    forcedWeather,
                    hasParcel
            )));
        }
        if (totalFarmWorlds == 0) {
            ctx.sendMessage(Message.raw("No hay farm worlds activos."));
            return;
        }
        ctx.sendMessage(Message.raw(String.format(
                Locale.ROOT,
                "Farm worlds: %d | growthOverride ownerOffline=%s serverEmpty=%s",
                totalFarmWorlds,
                formatNullableBoolean(MghgFarmEventScheduler.getGrowthWhenOwnerOfflineOverride()),
                formatNullableBoolean(MghgFarmEventScheduler.getGrowthWhenServerEmptyOverride())
        )));
    }

    private static void handleGrowth(@NonNull CommandContext ctx, String value1, String value2) {
        if (value1.isBlank()) {
            ctx.sendMessage(Message.raw(String.format(
                    Locale.ROOT,
                    "Growth overrides actuales: ownerOffline=%s | serverEmpty=%s",
                    formatNullableBoolean(MghgFarmEventScheduler.getGrowthWhenOwnerOfflineOverride()),
                    formatNullableBoolean(MghgFarmEventScheduler.getGrowthWhenServerEmptyOverride())
            )));
            ctx.sendMessage(Message.raw("Uso: /farm event growth <true|false|reset> [serverEmpty true|false]"));
            return;
        }

        if ("reset".equals(value1)) {
            MghgFarmEventScheduler.setGrowthPolicyOverrides(null, null);
            ctx.sendMessage(Message.raw("Growth overrides reseteados (vuelven al JSON)."));
            return;
        }

        Boolean ownerOffline = parseBoolean(value1);
        if (ownerOffline == null) {
            ctx.sendMessage(Message.raw("Valor invalido para ownerOffline. Usa true/false o reset."));
            return;
        }
        Boolean serverEmpty = value2.isBlank() ? ownerOffline : parseBoolean(value2);
        if (serverEmpty == null) {
            ctx.sendMessage(Message.raw("Valor invalido para serverEmpty. Usa true/false."));
            return;
        }

        MghgFarmEventScheduler.setGrowthPolicyOverrides(ownerOffline, serverEmpty);
        ctx.sendMessage(Message.raw(String.format(
                Locale.ROOT,
                "Growth overrides actualizados: ownerOffline=%s | serverEmpty=%s",
                ownerOffline,
                serverEmpty
        )));
    }

    private static void handleWeather(@NonNull CommandContext ctx, String value1, String value2) {
        if (value1.isBlank()) {
            ctx.sendMessage(Message.raw("Uso: /farm event weather <weatherId|clear> [force]"));
            return;
        }
        String weatherId = switch (normalize(value1)) {
            case "clear", "none", "reset" -> null;
            default -> value1;
        };
        boolean force = switch (normalize(value2)) {
            case "force", "reapply", "refresh" -> true;
            default -> false;
        };
        if (force) {
            MghgFarmEventScheduler.clearWeatherStateCache();
        }
        MghgFarmEventScheduler.applyWeatherToFarmWorlds(weatherId);
        ctx.sendMessage(Message.raw("Weather aplicado a farm worlds: " + fallback(weatherId, "clear")
                + (force ? " (forced reapply)" : "")));
    }

    private static void handleList(@NonNull CommandContext ctx, String value1) {
        MutationEventType type = parseEventType(value1);
        MghgFarmEventConfig cfg = MghgFarmEventScheduler.getConfig();
        if (cfg == null) {
            ctx.sendMessage(Message.raw("Config de events no disponible."));
            return;
        }
        if (type == MutationEventType.WEATHER || type == MutationEventType.ANY) {
            sendEventGroupList(ctx, "WEATHER", cfg.getRegular());
        }
        if (type == MutationEventType.LUNAR || type == MutationEventType.ANY) {
            sendEventGroupList(ctx, "LUNAR", cfg.getLunar());
        }
    }

    private static void handleStart(
            @NonNull CommandContext ctx,
            String value1,
            String value2,
            String value3
    ) {
        MutationEventType type = parseEventType(value1);
        if (type == MutationEventType.ANY) {
            ctx.sendMessage(Message.raw("Uso: /farm event start <weather|lunar> <eventId|random> [durationSec]"));
            return;
        }
        Integer duration = parsePositiveInt(value3);
        boolean started = MghgFarmEventScheduler.forceStartConfiguredEvent(type, value2, duration);
        if (!started) {
            ctx.sendMessage(Message.raw("No pude iniciar evento. Revisa tipo/id con /farm event list."));
            return;
        }
        ctx.sendMessage(Message.raw("Evento forzado iniciado."));
        sendStatus(ctx);
    }

    private static void handleStop(@NonNull CommandContext ctx) {
        MghgFarmEventScheduler.forceStopActiveEvent();
        ctx.sendMessage(Message.raw("Evento activo forzado a detenerse."));
        sendStatus(ctx);
    }

    private static void sendEventGroupList(
            @NonNull CommandContext ctx,
            @NonNull String label,
            @Nullable MghgFarmEventConfig.EventGroup group
    ) {
        if (group == null || group.getEvents() == null || group.getEvents().length == 0) {
            ctx.sendMessage(Message.raw(label + ": (sin eventos)"));
            return;
        }
        ctx.sendMessage(Message.raw(String.format(
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
            ctx.sendMessage(Message.raw(String.format(
                    Locale.ROOT,
                    " - id=%s | weather=%s | weight=%d",
                    fallback(definition.getId(), "-"),
                    fallback(definition.getWeatherId(), "-"),
                    Math.max(1, definition.getWeight())
            )));
        }
    }

    private static long secondsBetween(Instant now, @Nullable Instant target) {
        if (target == null) {
            return 0L;
        }
        return Math.max(0L, Duration.between(now, target).getSeconds());
    }

    private static String normalize(@Nullable String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }

    private static String fallback(@Nullable String raw, String fallback) {
        return (raw == null || raw.isBlank()) ? fallback : raw;
    }

    private static String formatDuration(long seconds) {
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

    private static double normalizeChance(double raw) {
        if (Double.isNaN(raw) || raw <= 0.0d) {
            return 0.0d;
        }
        double chance = raw > 1.0d ? (raw / 100.0d) : raw;
        if (chance < 0.0d) return 0.0d;
        if (chance > 1.0d) return 1.0d;
        return chance;
    }

    private static @Nullable Boolean parseBoolean(String raw) {
        return switch (normalize(raw)) {
            case "true", "1", "on", "yes", "y" -> Boolean.TRUE;
            case "false", "0", "off", "no", "n" -> Boolean.FALSE;
            default -> null;
        };
    }

    private static MutationEventType parseEventType(@Nullable String raw) {
        String token = normalize(raw);
        return switch (token) {
            case "weather", "regular", "rain", "snow" -> MutationEventType.WEATHER;
            case "lunar", "moon", "dawn", "amber" -> MutationEventType.LUNAR;
            case "", "any", "all" -> MutationEventType.ANY;
            default -> MutationEventType.ANY;
        };
    }

    private static @Nullable Integer parsePositiveInt(@Nullable String raw) {
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

    private static String formatNullableBoolean(@Nullable Boolean value) {
        return value == null ? "default" : value.toString();
    }

    private static String raw(@Nullable String value) {
        return value == null ? "" : value.trim();
    }
}
