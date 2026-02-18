package com.voidexiled.magichygarden.features.farming.worlds;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
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
import java.util.Locale;

public final class MghgFarmWorldConfig {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String RESOURCE_PATH = "Server/Farming/Farms/Mghg_FarmConfig.json";
    private static final Path BUILD_PATH = Paths.get("build", "resources", "main", "Server", "Farming", "Farms", "Mghg_FarmConfig.json");

    public static final BuilderCodec<MghgFarmWorldConfig> CODEC =
            BuilderCodec.builder(MghgFarmWorldConfig.class, MghgFarmWorldConfig::new)
                    .append(new KeyedCodec<>("FarmWorldNamePrefix", Codec.STRING, true),
                            (o, v) -> o.farmWorldNamePrefix = v,
                            o -> o.farmWorldNamePrefix)
                    .documentation("Prefix for farm world names (case-insensitive match).")
                    .add()
                    .append(new KeyedCodec<>("LobbyWorldName", Codec.STRING, true),
                            (o, v) -> o.lobbyWorldName = v,
                            o -> o.lobbyWorldName)
                    .documentation("Preferred lobby world name.")
                    .add()
                    .append(new KeyedCodec<>("SurvivalWorldName", Codec.STRING, true),
                            (o, v) -> o.survivalWorldName = v,
                            o -> o.survivalWorldName)
                    .documentation("Preferred survival world name.")
                    .add()
                    .append(new KeyedCodec<>("WorldGenProvider", Codec.STRING, true),
                            (o, v) -> o.worldGenProvider = v,
                            o -> o.worldGenProvider)
                    .documentation("World generator provider id (e.g., Flat, Void, Dummy).")
                    .add()
                    .append(new KeyedCodec<>("ChunkStorageProvider", Codec.STRING, true),
                            (o, v) -> o.chunkStorageProvider = v,
                            o -> o.chunkStorageProvider)
                    .documentation("Chunk storage provider id (e.g., Hytale, Empty, IndexedStorage).")
                    .add()
                    .append(new KeyedCodec<>("CreationMode", Codec.STRING, true),
                            (o, v) -> o.creationMode = v == null ? o.creationMode : v,
                            o -> o.creationMode)
                    .documentation("Farm world creation mode: Generator or TemplateCopy.")
                    .add()
                    .append(new KeyedCodec<>("TemplateWorldPath", Codec.STRING, true),
                            (o, v) -> o.templateWorldPath = v,
                            o -> o.templateWorldPath)
                    .documentation("Template world path used when CreationMode=TemplateCopy. Relative paths resolve from server root.")
                    .add()
                    .append(new KeyedCodec<>("TemplateRequireValidConfig", Codec.BOOLEAN, true),
                            (o, v) -> o.templateRequireValidConfig = v == null || v,
                            o -> o.templateRequireValidConfig)
                    .documentation("When true, template world path must contain a valid world config before copy.")
                    .add()
                    .append(new KeyedCodec<>("TemplateCopyRetries", Codec.INTEGER, true),
                            (o, v) -> o.templateCopyRetries = v == null ? o.templateCopyRetries : v,
                            o -> o.templateCopyRetries)
                    .documentation("How many template-copy attempts to run before failing farm creation.")
                    .add()
                    .append(new KeyedCodec<>("DisplayName", Codec.STRING, true),
                            (o, v) -> o.displayName = v,
                            o -> o.displayName)
                    .documentation("Optional display name for farm worlds.")
                    .add()
                    .append(new KeyedCodec<>("IsPvpEnabled", Codec.BOOLEAN, true),
                            (o, v) -> o.isPvpEnabled = v != null && v,
                            o -> o.isPvpEnabled)
                    .documentation("Enable/disable PvP in farm worlds.")
                    .add()
                    .append(new KeyedCodec<>("IsSpawningNPC", Codec.BOOLEAN, true),
                            (o, v) -> o.isSpawningNPC = v == null || v,
                            o -> o.isSpawningNPC)
                    .documentation("Enable/disable NPC spawning in farm worlds.")
                    .add()
                    .append(new KeyedCodec<>("EnableFullInstancePersistence", Codec.BOOLEAN, true),
                            (o, v) -> o.enableFullInstancePersistence = v == null || v,
                            o -> o.enableFullInstancePersistence)
                    .documentation("When true, farm worlds are periodically mirrored into mghg/world_backups for prune-safe restore.")
                    .add()
                    .append(new KeyedCodec<>("FullInstanceBackupIntervalSeconds", Codec.INTEGER, true),
                            (o, v) -> o.fullInstanceBackupIntervalSeconds = v == null ? 5 : v,
                            o -> o.fullInstanceBackupIntervalSeconds)
                    .documentation("Backup tick interval (seconds). One tick snapshots up to BackupWorldsPerTick farm worlds.")
                    .add()
                    .append(new KeyedCodec<>("BackupWorldsPerTick", Codec.INTEGER, true),
                            (o, v) -> o.fullInstanceBackupWorldsPerTick = v == null ? 1 : v,
                            o -> o.fullInstanceBackupWorldsPerTick)
                    .documentation("How many loaded farm worlds are snapshotted on each backup tick.")
                    .add()
                    .append(new KeyedCodec<>("ParcelSizeX", Codec.INTEGER, true),
                            (o, v) -> o.parcelSizeX = v == null ? 1000 : v,
                            o -> o.parcelSizeX)
                    .documentation("Parcel metadata width (used for spawn-centering and role UX).")
                    .add()
                    .append(new KeyedCodec<>("ParcelSizeY", Codec.INTEGER, true),
                            (o, v) -> o.parcelSizeY = v == null ? 256 : v,
                            o -> o.parcelSizeY)
                    .documentation("Parcel metadata height.")
                    .add()
                    .append(new KeyedCodec<>("ParcelSizeZ", Codec.INTEGER, true),
                            (o, v) -> o.parcelSizeZ = v == null ? 1000 : v,
                            o -> o.parcelSizeZ)
                    .documentation("Parcel metadata depth (used for spawn-centering and role UX).")
                    .add()
                    .append(new KeyedCodec<>("FarmSpawnX", Codec.INTEGER, true),
                            (o, v) -> o.farmSpawnX = v == null ? 4096 : v,
                            o -> o.farmSpawnX)
                    .documentation("Fallback farm spawn X used when resolved spawn is too close to world origin.")
                    .add()
                    .append(new KeyedCodec<>("FarmSpawnY", Codec.INTEGER, true),
                            (o, v) -> o.farmSpawnY = v == null ? 80 : v,
                            o -> o.farmSpawnY)
                    .documentation("Fallback farm spawn Y used when resolved spawn is too close to world origin.")
                    .add()
                    .append(new KeyedCodec<>("FarmSpawnZ", Codec.INTEGER, true),
                            (o, v) -> o.farmSpawnZ = v == null ? 4096 : v,
                            o -> o.farmSpawnZ)
                    .documentation("Fallback farm spawn Z used when resolved spawn is too close to world origin.")
                    .add()
                    .append(new KeyedCodec<>("FarmSpawnOriginGuardRadius", Codec.INTEGER, true),
                            (o, v) -> o.farmSpawnOriginGuardRadius = v == null ? 512 : v,
                            o -> o.farmSpawnOriginGuardRadius)
                    .documentation("If abs(x) and abs(z) are within this radius, farm spawn is forced to FarmSpawnX/Z.")
                    .add()
                    .build();

    private String farmWorldNamePrefix = "MGHG_Farm_";
    @Nullable
    private String lobbyWorldName;
    @Nullable
    private String survivalWorldName;
    private String worldGenProvider = "Flat";
    private String chunkStorageProvider = "Hytale";
    private String creationMode = "Generator";
    @Nullable
    private String templateWorldPath;
    private boolean templateRequireValidConfig = true;
    private int templateCopyRetries = 2;
    @Nullable
    private String displayName;
    private boolean isPvpEnabled;
    private boolean isSpawningNPC = true;
    private boolean enableFullInstancePersistence = true;
    private int fullInstanceBackupIntervalSeconds = 5;
    private int fullInstanceBackupWorldsPerTick = 1;
    private int parcelSizeX = 1000;
    private int parcelSizeY = 256;
    private int parcelSizeZ = 1000;
    private int farmSpawnX = 4096;
    private int farmSpawnY = 80;
    private int farmSpawnZ = 4096;
    private int farmSpawnOriginGuardRadius = 512;

    public String getFarmWorldNamePrefix() {
        return farmWorldNamePrefix == null ? "MGHG_Farm_" : farmWorldNamePrefix;
    }

    public @Nullable String getLobbyWorldName() {
        return lobbyWorldName;
    }

    public @Nullable String getSurvivalWorldName() {
        return survivalWorldName;
    }

    public String getWorldGenProvider() {
        return worldGenProvider == null ? "Flat" : worldGenProvider;
    }

    public String getChunkStorageProvider() {
        return chunkStorageProvider == null ? "Hytale" : chunkStorageProvider;
    }

    public CreationMode getCreationMode() {
        return CreationMode.fromRaw(creationMode);
    }

    public @Nullable String getTemplateWorldPath() {
        return templateWorldPath;
    }

    public boolean isTemplateRequireValidConfig() {
        return templateRequireValidConfig;
    }

    public int getTemplateCopyRetries() {
        return Math.max(1, templateCopyRetries);
    }

    public @Nullable String getDisplayName() {
        return displayName;
    }

    public boolean isPvpEnabled() {
        return isPvpEnabled;
    }

    public boolean isSpawningNPC() {
        return isSpawningNPC;
    }

    public boolean isEnableFullInstancePersistence() {
        return enableFullInstancePersistence;
    }

    public int getFullInstanceBackupIntervalSeconds() {
        return Math.max(5, fullInstanceBackupIntervalSeconds);
    }

    public int getFullInstanceBackupWorldsPerTick() {
        return Math.max(1, fullInstanceBackupWorldsPerTick);
    }

    public int getParcelSizeX() {
        return Math.max(1, parcelSizeX);
    }

    public int getParcelSizeY() {
        return Math.max(1, parcelSizeY);
    }

    public int getParcelSizeZ() {
        return Math.max(1, parcelSizeZ);
    }

    public int getFarmSpawnX() {
        return farmSpawnX;
    }

    public int getFarmSpawnY() {
        return Math.max(1, farmSpawnY);
    }

    public int getFarmSpawnZ() {
        return farmSpawnZ;
    }

    public int getFarmSpawnOriginGuardRadius() {
        return Math.max(0, farmSpawnOriginGuardRadius);
    }

    public static MghgFarmWorldConfig load() {
        try {
            if (Files.exists(BUILD_PATH)) {
                String payload = Files.readString(BUILD_PATH, StandardCharsets.UTF_8);
                RawJsonReader json = RawJsonReader.fromJsonString(payload);
                return CODEC.decodeJson(json, new ExtraInfo());
            }
        } catch (Exception e) {
            LOGGER.atWarning().log("[MGHG|FARM] Failed to load from build/resources: %s", e.getMessage());
        }

        try (InputStream stream = MghgFarmWorldConfig.class.getClassLoader().getResourceAsStream(RESOURCE_PATH)) {
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
            LOGGER.atWarning().log("[MGHG|FARM] Failed to load from resources: %s", e.getMessage());
        }

        return new MghgFarmWorldConfig();
    }

    public enum CreationMode {
        GENERATOR,
        TEMPLATE_COPY;

        public static CreationMode fromRaw(@Nullable String raw) {
            if (raw == null || raw.isBlank()) {
                return GENERATOR;
            }
            String normalized = raw.trim().toLowerCase(Locale.ROOT);
            if (normalized.equals("templatecopy")
                    || normalized.equals("template_copy")
                    || normalized.equals("template")) {
                return TEMPLATE_COPY;
            }
            return GENERATOR;
        }
    }
}
