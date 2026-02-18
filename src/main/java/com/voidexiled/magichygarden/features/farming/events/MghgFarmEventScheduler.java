package com.voidexiled.magichygarden.features.farming.events;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.asset.type.weather.config.Weather;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.builtin.weather.resources.WeatherResource;
import com.voidexiled.magichygarden.features.farming.worlds.MghgFarmWorldManager;
import com.voidexiled.magichygarden.features.farming.state.MghgWeatherIdUtil;
import com.voidexiled.magichygarden.features.farming.state.MutationEventType;

import javax.annotation.Nullable;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;

public final class MghgFarmEventScheduler {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final AtomicReference<MghgGlobalFarmEventState> CURRENT =
            new AtomicReference<>(MghgGlobalFarmEventState.none());

    private static volatile MghgFarmEventConfig CONFIG;
    private static volatile Instant nextRegularAt;
    private static volatile Instant nextLunarAt;
    private static final Map<UUID, String> LAST_APPLIED_WEATHER_BY_WORLD = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> LAST_BLOCK_TICKING_BY_WORLD = new ConcurrentHashMap<>();
    private static volatile @Nullable Boolean growthWhenOwnerOfflineOverride;
    private static volatile @Nullable Boolean growthWhenServerEmptyOverride;
    private static ScheduledFuture<?> task;

    private MghgFarmEventScheduler() {}

    public static void start() {
        if (task != null) return;
        reload();
        task = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(
                MghgFarmEventScheduler::tickSafe,
                1,
                1,
                TimeUnit.SECONDS
        );
    }

    public static void stop() {
        if (task != null) {
            task.cancel(false);
            task = null;
        }
        CURRENT.set(MghgGlobalFarmEventState.none());
        LAST_APPLIED_WEATHER_BY_WORLD.clear();
        LAST_BLOCK_TICKING_BY_WORLD.clear();
    }

    public static void clearWeatherStateCache() {
        LAST_APPLIED_WEATHER_BY_WORLD.clear();
    }

    public static void reload() {
        CONFIG = MghgFarmEventConfig.load();
        Instant now = Instant.now();
        nextRegularAt = scheduleNext(CONFIG.getRegular(), now);
        nextLunarAt = scheduleNext(CONFIG.getLunar(), now);
    }

    public static MghgGlobalFarmEventState getState() {
        return CURRENT.get();
    }

    public static @Nullable Instant getNextRegularAt() {
        return nextRegularAt;
    }

    public static @Nullable Instant getNextLunarAt() {
        return nextLunarAt;
    }

    public static @Nullable MghgFarmEventConfig getConfig() {
        return CONFIG;
    }

    public static void setGrowthPolicyOverrides(
            @Nullable Boolean allowWhenOwnerOffline,
            @Nullable Boolean allowWhenServerEmpty
    ) {
        growthWhenOwnerOfflineOverride = allowWhenOwnerOffline;
        growthWhenServerEmptyOverride = allowWhenServerEmpty;
    }

    public static @Nullable Boolean getGrowthWhenOwnerOfflineOverride() {
        return growthWhenOwnerOfflineOverride;
    }

    public static @Nullable Boolean getGrowthWhenServerEmptyOverride() {
        return growthWhenServerEmptyOverride;
    }

    public static void applyWeatherToFarmWorlds(@Nullable String weatherId) {
        applyWeatherIfNeeded(CONFIG, weatherId);
    }

    public static synchronized boolean forceStartConfiguredEvent(
            MutationEventType type,
            @Nullable String eventId,
            @Nullable Integer durationOverrideSeconds
    ) {
        MghgFarmEventConfig cfg = CONFIG;
        if (cfg == null) {
            return false;
        }
        MghgFarmEventConfig.EventGroup group = resolveGroup(cfg, type);
        if (group == null || group.getEvents() == null || group.getEvents().length == 0) {
            return false;
        }

        MghgFarmEventConfig.EventDefinition chosen;
        if (eventId == null || eventId.isBlank() || "random".equalsIgnoreCase(eventId)) {
            chosen = pickWeighted(group.getEvents());
        } else {
            chosen = findEventDefinition(group, eventId);
        }
        if (chosen == null) {
            return false;
        }

        Instant now = Instant.now();
        int duration = durationOverrideSeconds == null
                ? group.getDurationSeconds()
                : Math.max(1, durationOverrideSeconds);

        MghgGlobalFarmEventState state = new MghgGlobalFarmEventState(
                type,
                chosen.getId(),
                chosen.getWeatherId(),
                now,
                now.plusSeconds(duration)
        );
        CURRENT.set(state);
        applyWeatherIfNeeded(cfg, chosen.getWeatherId());

        if (type == MutationEventType.LUNAR) {
            nextLunarAt = scheduleNext(cfg.getLunar(), now);
            nextRegularAt = scheduleNext(cfg.getRegular(), now);
        } else if (type == MutationEventType.WEATHER) {
            nextRegularAt = scheduleNext(cfg.getRegular(), now);
        }

        LOGGER.atInfo().log(
                "[MGHG|EVENTS] Forced start %s event '%s' (weather=%s) for %ds",
                type, chosen.getId(), chosen.getWeatherId(), duration
        );
        return true;
    }

    public static synchronized void forceStopActiveEvent() {
        MghgFarmEventConfig cfg = CONFIG;
        Instant now = Instant.now();
        CURRENT.set(MghgGlobalFarmEventState.none());
        if (cfg != null) {
            applyWeatherIfNeeded(cfg, cfg.getClearWeatherId());
            nextRegularAt = scheduleNext(cfg.getRegular(), now);
            nextLunarAt = scheduleNext(cfg.getLunar(), now);
        }
        LOGGER.atInfo().log("[MGHG|EVENTS] Forced stop active event.");
    }

    public static boolean isFarmOwnerOnline(@Nullable World world) {
        if (world == null || !isFarmWorld(world)) {
            return true;
        }
        Universe universe = Universe.get();
        if (universe == null) {
            return false;
        }
        UUID owner = MghgFarmWorldManager.getOwnerFromFarmWorld(world);
        if (owner == null) {
            return false;
        }
        return universe.getPlayer(owner) != null;
    }

    public static boolean isFarmWorld(@Nullable World world) {
        if (world == null) return false;
        MghgFarmEventConfig cfg = CONFIG;
        if (cfg == null) return false;

        Set<String> names = normalizeSet(cfg.getFarmWorldNames());
        Set<UUID> uuids = parseUuidSet(cfg.getFarmWorldUuids());
        String prefix = cfg.getFarmWorldNamePrefix();

        if ((names.isEmpty() && uuids.isEmpty()) && cfg.isApplyToAllWorldsIfEmpty()) {
            return true;
        }

        if (prefix != null && !prefix.isBlank()) {
            String name = world.getName();
            if (name != null && name.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }

        if (!names.isEmpty()) {
            String name = world.getName();
            if (name != null && names.contains(name.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }

        if (!uuids.isEmpty()) {
            UUID worldUuid = world.getWorldConfig().getUuid();
            return worldUuid != null && uuids.contains(worldUuid);
        }
        return false;
    }

    public static boolean isGrowthAllowedForWorld(@Nullable World world) {
        if (world == null) {
            return true;
        }
        UUID worldUuid = world.getWorldConfig().getUuid();
        if (worldUuid == null) {
            return true;
        }
        Boolean cached = LAST_BLOCK_TICKING_BY_WORLD.get(worldUuid);
        return cached == null || cached;
    }

    private static void tickSafe() {
        try {
            tick();
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("[MGHG|EVENTS] Tick failed.");
        }
    }

    private static void tick() {
        MghgFarmEventConfig cfg = CONFIG;
        if (cfg == null) return;
        syncFarmWorldTicking(cfg);

        Instant now = Instant.now();
        MghgGlobalFarmEventState current = CURRENT.get();

        if (current != null && current.isActive(now)) {
            applyWeatherIfNeeded(cfg, current.weatherId());
            return;
        }

        if (current != null && current.endsAt() != null) {
            // Event ended; clear weather and force a fresh schedule window.
            applyWeatherIfNeeded(cfg, cfg.getClearWeatherId());
            LOGGER.atInfo().log("[MGHG|EVENTS] Ended %s event '%s'. Applying clear weather '%s'.",
                    current.eventType(), current.eventId(), cfg.getClearWeatherId());
            CURRENT.set(MghgGlobalFarmEventState.none());
            rescheduleOverdueEventStarts(cfg, now);
            return;
        }

        // Start new events (lunar has priority).
        if (nextLunarAt != null && !now.isBefore(nextLunarAt)) {
            boolean lunarStarted = startEventIfRolled(cfg.getLunar(), MutationEventType.LUNAR, now);
            nextLunarAt = scheduleNext(cfg.getLunar(), now);
            if (lunarStarted) {
                nextRegularAt = scheduleNext(cfg.getRegular(), now);
                return;
            }
        }

        if (nextRegularAt != null && !now.isBefore(nextRegularAt)) {
            startEventIfRolled(cfg.getRegular(), MutationEventType.WEATHER, now);
            nextRegularAt = scheduleNext(cfg.getRegular(), now);
        }
    }

    private static void rescheduleOverdueEventStarts(MghgFarmEventConfig cfg, Instant now) {
        if (nextRegularAt != null && !now.isBefore(nextRegularAt)) {
            nextRegularAt = scheduleNext(cfg.getRegular(), now);
        }
        if (nextLunarAt != null && !now.isBefore(nextLunarAt)) {
            nextLunarAt = scheduleNext(cfg.getLunar(), now);
        }
    }

    private static boolean startEventIfRolled(
            @Nullable MghgFarmEventConfig.EventGroup group,
            MutationEventType type,
            Instant now
    ) {
        if (group == null) return false;
        if (!passesOccurrenceChance(group)) {
            LOGGER.atInfo().log("[MGHG|EVENTS] Skipped %s group this cycle (occurrenceChance=%.3f).",
                    type, normalizeChance(group.getOccurrenceChance()));
            return false;
        }
        return startEvent(group, type, now);
    }

    private static boolean startEvent(MghgFarmEventConfig.EventGroup group, MutationEventType type, Instant now) {
        if (group == null || group.getEvents() == null || group.getEvents().length == 0) {
            return false;
        }
        MghgFarmEventConfig.EventDefinition chosen = pickWeighted(group.getEvents());
        if (chosen == null) {
            return false;
        }
        Instant endsAt = now.plusSeconds(Math.max(1, group.getDurationSeconds()));
        MghgGlobalFarmEventState state = new MghgGlobalFarmEventState(
                type,
                chosen.getId(),
                chosen.getWeatherId(),
                now,
                endsAt
        );
        CURRENT.set(state);
        applyWeatherIfNeeded(CONFIG, chosen.getWeatherId());
        LOGGER.atInfo().log("[MGHG|EVENTS] Started %s event '%s' (weather=%s) for %ds",
                type, chosen.getId(), chosen.getWeatherId(), group.getDurationSeconds());
        return true;
    }

    private static @Nullable MghgFarmEventConfig.EventDefinition pickWeighted(MghgFarmEventConfig.EventDefinition[] events) {
        int total = 0;
        for (MghgFarmEventConfig.EventDefinition e : events) {
            if (e != null) total += Math.max(0, e.getWeight());
        }
        if (total <= 0) return null;
        int roll = ThreadLocalRandom.current().nextInt(total);
        for (MghgFarmEventConfig.EventDefinition e : events) {
            if (e == null) continue;
            roll -= Math.max(0, e.getWeight());
            if (roll < 0) return e;
        }
        return null;
    }

    private static @Nullable MghgFarmEventConfig.EventDefinition findEventDefinition(
            MghgFarmEventConfig.EventGroup group,
            String eventId
    ) {
        if (group.getEvents() == null || eventId == null || eventId.isBlank()) {
            return null;
        }
        for (MghgFarmEventConfig.EventDefinition definition : group.getEvents()) {
            if (definition == null || definition.getId() == null) {
                continue;
            }
            if (eventId.equalsIgnoreCase(definition.getId())) {
                return definition;
            }
        }
        return null;
    }

    private static @Nullable MghgFarmEventConfig.EventGroup resolveGroup(
            MghgFarmEventConfig config,
            MutationEventType type
    ) {
        if (config == null || type == null) {
            return null;
        }
        return switch (type) {
            case WEATHER -> config.getRegular();
            case LUNAR -> config.getLunar();
            default -> null;
        };
    }

    private static @Nullable Instant scheduleNext(@Nullable MghgFarmEventConfig.EventGroup group, Instant now) {
        if (group == null) return null;
        int min = Math.max(1, group.getIntervalMinSeconds());
        int max = Math.max(min, group.getIntervalMaxSeconds());
        int offset = ThreadLocalRandom.current().nextInt(min, max + 1);
        return now.plusSeconds(offset);
    }

    private static boolean passesOccurrenceChance(MghgFarmEventConfig.EventGroup group) {
        double chance = normalizeChance(group.getOccurrenceChance());
        if (chance <= 0.0d) {
            return false;
        }
        if (chance >= 1.0d) {
            return true;
        }
        return ThreadLocalRandom.current().nextDouble() <= chance;
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

    private static void applyWeatherIfNeeded(MghgFarmEventConfig cfg, @Nullable String weatherId) {
        String normalized = resolveWeatherId(cfg, weatherId);
        String clearWeather = resolveWeatherId(cfg, cfg == null ? null : cfg.getClearWeatherId());
        Universe universe = Universe.get();
        if (universe == null) return;

        Set<UUID> farmWorldUuids = new HashSet<>();
        for (World world : universe.getWorlds().values()) {
            if (!isFarmWorld(world)) continue;
            UUID uuid = world.getWorldConfig().getUuid();
            if (uuid == null) {
                continue;
            }
            farmWorldUuids.add(uuid);
            String lastApplied = LAST_APPLIED_WEATHER_BY_WORLD.get(uuid);
            if (equalsIgnoreCase(normalized, lastApplied)) {
                continue;
            }
            LAST_APPLIED_WEATHER_BY_WORLD.put(uuid, normalized);
            world.execute(() -> {
                applyWeatherToWorld(world, uuid, normalized);
            });
        }
        LAST_APPLIED_WEATHER_BY_WORLD.keySet().removeIf(uuid -> !farmWorldUuids.contains(uuid));
    }

    private static void applyWeatherToWorld(World world, UUID uuid, @Nullable String weatherId) {
        WeatherResource weather = world.getEntityStore().getStore().getResource(WeatherResource.getResourceType());
        if (weather != null) {
            weather.setForcedWeather(weatherId);
        }
        // Never persist custom weather ids in world config; they can fail validation on restart.
        if (world.getWorldConfig().getForcedWeather() != null) {
            world.getWorldConfig().setForcedWeather(null);
            world.getWorldConfig().markChanged();
        }
        LOGGER.atInfo().log("[MGHG|EVENTS] Applied weather '%s' to world '%s' (%s).",
                weatherId == null ? "<none>" : weatherId,
                world.getName(),
                uuid);
    }

    private static @Nullable String resolveWeatherId(
            @Nullable MghgFarmEventConfig cfg,
            @Nullable String weatherId
    ) {
        String requested = (weatherId == null || weatherId.isBlank()) ? null : weatherId;
        if (requested == null) {
            return null;
        }

        // Prefer exact id first (lets farm events use custom Mghg weather assets directly).
        if (assetExists(requested)) {
            return requested;
        }

        String normalizedAlias = MghgWeatherIdUtil.normalizeWeatherAlias(requested);
        if (normalizedAlias != null && assetExists(normalizedAlias)) {
            LOGGER.atInfo().log(
                    "[MGHG|EVENTS] Weather id '%s' remapped to '%s'.",
                    requested,
                    normalizedAlias
            );
            return normalizedAlias;
        }

        String clear = cfg == null ? null : cfg.getClearWeatherId();
        if (clear != null && !clear.isBlank() && assetExists(clear)) {
            LOGGER.atWarning().log(
                    "[MGHG|EVENTS] Unknown weather id '%s'. Falling back to clear weather '%s'.",
                    requested,
                    clear
            );
            return clear;
        }

        LOGGER.atWarning().log(
                "[MGHG|EVENTS] Unknown weather id '%s' and clear weather is invalid/unset. Clearing forced weather.",
                requested
        );
        return null;
    }

    private static boolean assetExists(String weatherId) {
        return Weather.getAssetMap().getIndex(weatherId) != Weather.UNKNOWN_ID;
    }

    private static void syncFarmWorldTicking(MghgFarmEventConfig cfg) {
        Universe universe = Universe.get();
        if (universe == null) return;

        boolean anyPlayersOnline = !universe.getPlayers().isEmpty();
        boolean allowWhenOwnerOffline = growthWhenOwnerOfflineOverride != null
                ? growthWhenOwnerOfflineOverride
                : cfg.isAllowGrowthWhenOwnerOffline();
        boolean allowWhenServerEmpty = growthWhenServerEmptyOverride != null
                ? growthWhenServerEmptyOverride
                : cfg.isAllowGrowthWhenServerEmpty();

        Set<UUID> farmWorldUuids = new HashSet<>();
        for (World world : universe.getWorlds().values()) {
            if (!isFarmWorld(world)) {
                continue;
            }
            UUID worldUuid = world.getWorldConfig().getUuid();
            if (worldUuid == null) {
                continue;
            }
            farmWorldUuids.add(worldUuid);

            boolean ownerOnline = isFarmOwnerOnline(world);
            boolean shouldTickBlocks = (allowWhenOwnerOffline || ownerOnline)
                    && (allowWhenServerEmpty || anyPlayersOnline);

            Boolean last = LAST_BLOCK_TICKING_BY_WORLD.get(worldUuid);
            if (last != null && last == shouldTickBlocks) {
                continue;
            }
            LAST_BLOCK_TICKING_BY_WORLD.put(worldUuid, shouldTickBlocks);
        }
        LAST_BLOCK_TICKING_BY_WORLD.keySet().removeIf(uuid -> !farmWorldUuids.contains(uuid));
    }

    private static boolean equalsIgnoreCase(@Nullable String a, @Nullable String b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equalsIgnoreCase(b);
    }

    private static Set<String> normalizeSet(@Nullable String[] values) {
        Set<String> out = new HashSet<>();
        if (values == null) return out;
        for (String v : values) {
            if (v == null || v.isBlank()) continue;
            out.add(v.trim().toLowerCase(Locale.ROOT));
        }
        return out;
    }

    private static Set<UUID> parseUuidSet(@Nullable String[] values) {
        Set<UUID> out = new HashSet<>();
        if (values == null) return out;
        for (String v : values) {
            if (v == null || v.isBlank()) continue;
            try {
                out.add(UUID.fromString(v.trim()));
            } catch (IllegalArgumentException ignored) {
                LOGGER.atWarning().log("[MGHG|EVENTS] Invalid world UUID '%s' in config.", v);
            }
        }
        return out;
    }
}
