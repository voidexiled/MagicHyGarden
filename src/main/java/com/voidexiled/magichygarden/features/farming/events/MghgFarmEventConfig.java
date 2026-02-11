package com.voidexiled.magichygarden.features.farming.events;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;
import com.hypixel.hytale.codec.util.RawJsonReader;
import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class MghgFarmEventConfig {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String RESOURCE_PATH = "Server/Farming/Events/Mghg_Events.json";
    private static final Path BUILD_PATH = Paths.get("build", "resources", "main", "Server", "Farming", "Events", "Mghg_Events.json");

    public static final BuilderCodec<MghgFarmEventConfig> CODEC =
            BuilderCodec.builder(MghgFarmEventConfig.class, MghgFarmEventConfig::new)
                    .append(new KeyedCodec<>("ClearWeatherId", Codec.STRING, true),
                            (o, v) -> o.clearWeatherId = v,
                            o -> o.clearWeatherId)
                    .documentation("Weather asset id to force between events (null = no forced weather).")
                    .add()
                    .append(new KeyedCodec<>("FarmWorldNames", Codec.STRING_ARRAY, true),
                            (o, v) -> o.farmWorldNames = v,
                            o -> o.farmWorldNames)
                    .documentation("World names considered farm instances (case-insensitive).")
                    .add()
                    .append(new KeyedCodec<>("FarmWorldUuids", Codec.STRING_ARRAY, true),
                            (o, v) -> o.farmWorldUuids = v,
                            o -> o.farmWorldUuids)
                    .documentation("World UUIDs considered farm instances.")
                    .add()
                    .append(new KeyedCodec<>("FarmWorldNamePrefix", Codec.STRING, true),
                            (o, v) -> o.farmWorldNamePrefix = v,
                            o -> o.farmWorldNamePrefix)
                    .documentation("Optional prefix for farm world names (case-insensitive).")
                    .add()
                    .append(new KeyedCodec<>("ApplyToAllWorldsIfEmpty", Codec.BOOLEAN, true),
                            (o, v) -> o.applyToAllWorldsIfEmpty = v != null && v,
                            o -> o.applyToAllWorldsIfEmpty)
                    .documentation("If true and no farm worlds are listed, apply to all worlds.")
                    .add()
                    .append(new KeyedCodec<>("AllowMutationsWhenOwnerOffline", Codec.BOOLEAN, true),
                            (o, v) -> o.allowMutationsWhenOwnerOffline = v == null || v,
                            o -> o.allowMutationsWhenOwnerOffline)
                    .documentation("If false, mutation rolls are skipped while the farm owner is offline.")
                    .add()
                    .append(new KeyedCodec<>("AllowGrowthWhenOwnerOffline", Codec.BOOLEAN, true),
                            (o, v) -> o.allowGrowthWhenOwnerOffline = v == null || v,
                            o -> o.allowGrowthWhenOwnerOffline)
                    .documentation("If false, block ticking is paused in farm world while owner is offline.")
                    .add()
                    .append(new KeyedCodec<>("AllowGrowthWhenServerEmpty", Codec.BOOLEAN, true),
                            (o, v) -> o.allowGrowthWhenServerEmpty = v == null || v,
                            o -> o.allowGrowthWhenServerEmpty)
                    .documentation("If false, block ticking is paused in farm worlds while no players are online.")
                    .add()
                    .append(new KeyedCodec<>("OfflineMutationChanceMultiplier", Codec.DOUBLE, true),
                            (o, v) -> o.offlineMutationChanceMultiplier = v == null ? o.offlineMutationChanceMultiplier : v,
                            o -> o.offlineMutationChanceMultiplier)
                    .documentation("Global chance multiplier applied to mutation rolls when owner is offline (0..1+).")
                    .add()
                    .append(new KeyedCodec<>("Regular", EventGroup.CODEC),
                            (o, v) -> o.regular = v,
                            o -> o.regular)
                    .documentation("Regular weather events.")
                    .add()
                    .append(new KeyedCodec<>("Lunar", EventGroup.CODEC),
                            (o, v) -> o.lunar = v,
                            o -> o.lunar)
                    .documentation("Lunar events.")
                    .add()
                    .build();

    @Nullable
    private String clearWeatherId;
    @Nullable
    private String[] farmWorldNames;
    @Nullable
    private String[] farmWorldUuids;
    @Nullable
    private String farmWorldNamePrefix;
    private boolean applyToAllWorldsIfEmpty;
    private boolean allowMutationsWhenOwnerOffline = true;
    private boolean allowGrowthWhenOwnerOffline = true;
    private boolean allowGrowthWhenServerEmpty = true;
    private double offlineMutationChanceMultiplier = 1.0d;
    private EventGroup regular = new EventGroup();
    private EventGroup lunar = new EventGroup();

    public MghgFarmEventConfig() {}

    public @Nullable String getClearWeatherId() {
        return clearWeatherId;
    }

    public @Nullable String[] getFarmWorldNames() {
        return farmWorldNames;
    }

    public @Nullable String[] getFarmWorldUuids() {
        return farmWorldUuids;
    }

    public @Nullable String getFarmWorldNamePrefix() {
        return farmWorldNamePrefix;
    }

    public boolean isApplyToAllWorldsIfEmpty() {
        return applyToAllWorldsIfEmpty;
    }

    public boolean isAllowMutationsWhenOwnerOffline() {
        return allowMutationsWhenOwnerOffline;
    }

    public boolean isAllowGrowthWhenOwnerOffline() {
        return allowGrowthWhenOwnerOffline;
    }

    public boolean isAllowGrowthWhenServerEmpty() {
        return allowGrowthWhenServerEmpty;
    }

    public double getOfflineMutationChanceMultiplier() {
        if (Double.isNaN(offlineMutationChanceMultiplier) || offlineMutationChanceMultiplier < 0.0d) {
            return 0.0d;
        }
        return offlineMutationChanceMultiplier;
    }

    public EventGroup getRegular() {
        return regular;
    }

    public EventGroup getLunar() {
        return lunar;
    }

    public static MghgFarmEventConfig load() {
        try {
            if (Files.exists(BUILD_PATH)) {
                String payload = Files.readString(BUILD_PATH, StandardCharsets.UTF_8);
                RawJsonReader json = RawJsonReader.fromJsonString(payload);
                return CODEC.decodeJson(json, new ExtraInfo());
            }
        } catch (Exception e) {
            LOGGER.atWarning().log("[MGHG|EVENTS] Failed to load from build/resources: %s", e.getMessage());
        }

        try (InputStream stream = MghgFarmEventConfig.class.getClassLoader().getResourceAsStream(RESOURCE_PATH)) {
            if (stream != null) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                    StringBuilder payload = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        payload.append(line).append('\n');
                    }
                    RawJsonReader json = RawJsonReader.fromJsonString(payload.toString());
                    return CODEC.decodeJson(json, new ExtraInfo());
                }
            }
        } catch (Exception e) {
            LOGGER.atWarning().log("[MGHG|EVENTS] Failed to load from resources: %s", e.getMessage());
        }

        LOGGER.atWarning().log("[MGHG|EVENTS] No events config found; using defaults.");
        return new MghgFarmEventConfig();
    }

    public static final class EventGroup {
        public static final BuilderCodec<EventGroup> CODEC =
                BuilderCodec.builder(EventGroup.class, EventGroup::new)
                        .append(new KeyedCodec<>("OccurrenceChance", Codec.DOUBLE, true),
                                (o, v) -> o.occurrenceChance = v == null ? o.occurrenceChance : v,
                                o -> o.occurrenceChance)
                        .documentation("Chance this event group starts when its interval triggers (0-1 or 0-100).")
                        .add()
                        .append(new KeyedCodec<>("IntervalMinSeconds", Codec.INTEGER),
                                (o, v) -> o.intervalMinSeconds = v,
                                o -> o.intervalMinSeconds)
                        .documentation("Minimum seconds between events.")
                        .add()
                        .append(new KeyedCodec<>("IntervalMaxSeconds", Codec.INTEGER, true),
                                (o, v) -> o.intervalMaxSeconds = v == null ? o.intervalMaxSeconds : v,
                                o -> o.intervalMaxSeconds)
                        .documentation("Maximum seconds between events.")
                        .add()
                        .append(new KeyedCodec<>("DurationSeconds", Codec.INTEGER),
                                (o, v) -> o.durationSeconds = v,
                                o -> o.durationSeconds)
                        .documentation("Event duration in seconds.")
                        .add()
                        .append(new KeyedCodec<>("Events",
                                        ArrayCodec.ofBuilderCodec(EventDefinition.CODEC, EventDefinition[]::new)),
                                (o, v) -> o.events = v,
                                o -> o.events)
                        .documentation("List of weighted events.")
                        .add()
                        .build();

        private int intervalMinSeconds = 1200;
        private int intervalMaxSeconds = 1800;
        private int durationSeconds = 300;
        private double occurrenceChance = 1.0;
        private EventDefinition[] events = new EventDefinition[0];

        public double getOccurrenceChance() {
            return occurrenceChance;
        }

        public int getIntervalMinSeconds() {
            return intervalMinSeconds;
        }

        public int getIntervalMaxSeconds() {
            return intervalMaxSeconds;
        }

        public int getDurationSeconds() {
            return durationSeconds;
        }

        public EventDefinition[] getEvents() {
            return events;
        }
    }

    public static final class EventDefinition {
        public static final BuilderCodec<EventDefinition> CODEC =
                BuilderCodec.builder(EventDefinition.class, EventDefinition::new)
                        .append(new KeyedCodec<>("Id", Codec.STRING),
                                (o, v) -> o.id = v,
                                o -> o.id)
                        .documentation("Logical event id (debug/telemetry).")
                        .add()
                        .append(new KeyedCodec<>("WeatherId", Codec.STRING),
                                (o, v) -> o.weatherId = v,
                                o -> o.weatherId)
                        .documentation("Weather asset id to force during this event.")
                        .add()
                        .append(new KeyedCodec<>("Weight", Codec.INTEGER, true),
                                (o, v) -> o.weight = v == null ? o.weight : v,
                                o -> o.weight)
                        .documentation("Weighted chance for selection.")
                        .add()
                        .build();

        private String id;
        private String weatherId;
        private int weight = 1;

        public String getId() {
            return id;
        }

        public String getWeatherId() {
            return weatherId;
        }

        public int getWeight() {
            return weight <= 0 ? 1 : weight;
        }
    }
}
