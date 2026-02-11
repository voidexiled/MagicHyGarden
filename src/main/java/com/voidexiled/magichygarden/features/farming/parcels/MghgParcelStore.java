package com.voidexiled.magichygarden.features.farming.parcels;

import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.util.BsonUtil;
import com.voidexiled.magichygarden.features.farming.storage.MghgStoragePaths;
import org.bson.BsonDocument;
import org.bson.BsonValue;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public final class MghgParcelStore {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String FILE_SUFFIX = ".parcel.json";
    private static final Path DEFAULT_DIRECTORY = Path.of("mghg", "parcels");

    private final Path preferredDirectory;
    private final Path legacyPath;
    private volatile Path activeDirectory;
    private final Map<UUID, MghgParcel> parcelsById = new ConcurrentHashMap<>();
    private final Map<UUID, MghgParcel> parcelsByOwner = new ConcurrentHashMap<>();

    public MghgParcelStore(Path directory) {
        this(directory, null);
    }

    public MghgParcelStore(Path directory, Path legacyPath) {
        this.preferredDirectory = normalizePath(directory, DEFAULT_DIRECTORY);
        this.legacyPath = legacyPath == null ? null : normalizePath(legacyPath, Path.of("mghg", "parcels.json"));
        this.activeDirectory = this.preferredDirectory;
    }

    public void load() {
        Path effectiveDirectory = preferredDirectory;

        parcelsById.clear();
        parcelsByOwner.clear();

        try {
            Files.createDirectories(effectiveDirectory);
        } catch (Exception e) {
            LOGGER.atWarning().log("[MGHG|PARCEL] Failed to create parcel directory %s: %s", effectiveDirectory, e.getMessage());
            return;
        }

        LOGGER.atInfo().log("[MGHG|PARCEL] Store directory: %s", effectiveDirectory);

        int loadedFiles = loadPerOwnerFiles(effectiveDirectory);
        if (loadedFiles == 0) {
            Path legacyDirectory = findLegacyDirectoryWithData(effectiveDirectory);
            if (legacyDirectory != null) {
                int migrated = loadPerOwnerFiles(legacyDirectory);
                if (migrated > 0) {
                    LOGGER.atInfo().log("[MGHG|PARCEL] Migrated %d parcel file(s) from legacy directory %s", migrated, legacyDirectory);
                    loadedFiles = migrated;
                    save();
                }
            }
        }
        if (loadedFiles > 0) {
            LOGGER.atInfo().log("[MGHG|PARCEL] Loaded %d parcel file(s).", loadedFiles);
            activeDirectory = effectiveDirectory;
            return;
        }

        // Legacy migration (single parcels.json).
        for (Path legacy : legacyCandidates()) {
            if (legacy == null || !Files.isRegularFile(legacy)) {
                continue;
            }
            BsonDocument document = BsonUtil.readDocumentNow(legacy);
            if (document == null) {
                continue;
            }
            List<MghgParcel> migrated = decodeLegacyStore(document);
            for (MghgParcel parcel : migrated) {
                upsertDecoded(parcel);
            }
            if (!migrated.isEmpty()) {
                LOGGER.atInfo().log("[MGHG|PARCEL] Migrated %d parcel(s) from legacy store %s", migrated.size(), legacy);
                save();
                break;
            }
        }
        activeDirectory = effectiveDirectory;
    }

    private Path findLegacyDirectoryWithData(Path target) {
        for (Path legacy : legacyDirectories(target)) {
            if (legacy == null) {
                continue;
            }
            if (!Files.isDirectory(legacy)) {
                continue;
            }
            try (Stream<Path> stream = Files.list(legacy)) {
                boolean hasData = stream
                        .filter(Files::isRegularFile)
                        .anyMatch(path -> path.getFileName().toString().endsWith(FILE_SUFFIX));
                if (hasData) {
                    return legacy;
                }
            } catch (Exception ignored) {
                // Best-effort migration discovery.
            }
        }
        return null;
    }

    public void save() {
        Path effectiveDirectory = resolveDirectory();

        try {
            Files.createDirectories(effectiveDirectory);
            Set<Path> expected = new HashSet<>();

            for (MghgParcel parcel : parcelsById.values()) {
                if (parcel == null || parcel.getOwner() == null || parcel.getId() == null) {
                    continue;
                }
                Path file = resolveOwnerFile(effectiveDirectory, parcel.getOwner());
                if (file == null) {
                    continue;
                }
                expected.add(file.toAbsolutePath().normalize());
                writeParcelFile(file, parcel);
            }

            try (Stream<Path> stream = Files.list(effectiveDirectory)) {
                stream.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(FILE_SUFFIX))
                        .map(Path::toAbsolutePath)
                        .map(Path::normalize)
                        .filter(path -> !expected.contains(path))
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException e) {
                                LOGGER.atWarning().log("[MGHG|PARCEL] Failed to delete stale parcel file %s: %s", path, e.getMessage());
                            }
                        });
            }
        } catch (Exception e) {
            LOGGER.atWarning().log("[MGHG|PARCEL] Failed to save parcels: %s", e.getMessage());
        }
    }

    public MghgParcel getById(UUID id) {
        return id == null ? null : parcelsById.get(id);
    }

    public MghgParcel getByOwner(UUID owner) {
        return owner == null ? null : parcelsByOwner.get(owner);
    }

    public MghgParcel getOrCreate(UUID owner, int originX, int originY, int originZ) {
        MghgParcel existing = getByOwner(owner);
        if (existing != null) return existing;

        MghgParcelBounds bounds = new MghgParcelBounds(originX, originY, originZ,
                MghgParcelBounds.DEFAULT_SIZE_X, MghgParcelBounds.DEFAULT_SIZE_Y, MghgParcelBounds.DEFAULT_SIZE_Z);
        MghgParcel parcel = new MghgParcel(UUID.randomUUID(), owner, bounds);
        parcelsById.put(parcel.getId(), parcel);
        parcelsByOwner.put(owner, parcel);
        return parcel;
    }

    public Collection<MghgParcel> all() {
        return parcelsById.values();
    }

    public Path getActiveDirectory() {
        return resolveDirectory();
    }

    public @Nullable Path resolveOwnerFilePath(@Nullable UUID owner) {
        if (owner == null) {
            return null;
        }
        return resolveOwnerFile(resolveDirectory(), owner);
    }

    private int loadPerOwnerFiles(Path effectiveDirectory) {
        int loaded = 0;
        try (Stream<Path> stream = Files.list(effectiveDirectory)) {
            List<Path> files = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(FILE_SUFFIX))
                    .toList();

            for (Path file : files) {
                try {
                    BsonDocument document = BsonUtil.readDocumentNow(file);
                    if (document == null) {
                        continue;
                    }
                    ExtraInfo extraInfo = new ExtraInfo();
                    MghgParcel parcel = MghgParcel.CODEC.decode(document, extraInfo);
                    extraInfo.getValidationResults().logOrThrowValidatorExceptions(LOGGER);
                    if (upsertDecoded(parcel)) {
                        loaded++;
                    }
                } catch (Exception e) {
                    LOGGER.atWarning().log("[MGHG|PARCEL] Failed to read parcel file %s: %s", file, e.getMessage());
                }
            }
        } catch (Exception e) {
            LOGGER.atWarning().log("[MGHG|PARCEL] Failed to list parcel directory %s: %s", effectiveDirectory, e.getMessage());
        }
        return loaded;
    }

    private List<MghgParcel> decodeLegacyStore(BsonDocument document) {
        List<MghgParcel> result = new ArrayList<>();
        try {
            ExtraInfo extraInfo = new ExtraInfo();
            MghgParcelStoreData data = MghgParcelStoreData.CODEC.decode(document, extraInfo);
            extraInfo.getValidationResults().logOrThrowValidatorExceptions(LOGGER);
            if (data == null || data.getParcels() == null) {
                return result;
            }
            for (MghgParcel parcel : data.getParcels()) {
                if (parcel != null) {
                    result.add(parcel);
                }
            }
        } catch (Exception e) {
            LOGGER.atWarning().log("[MGHG|PARCEL] Failed to decode legacy parcel store: %s", e.getMessage());
        }
        return result;
    }

    private void writeParcelFile(Path file, MghgParcel parcel) throws Exception {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        if (Files.isRegularFile(file)) {
            Path backup = file.resolveSibling(file.getFileName() + ".bak");
            Files.move(file, backup, StandardCopyOption.REPLACE_EXISTING);
        }

        ExtraInfo extraInfo = ExtraInfo.THREAD_LOCAL.get();
        BsonValue bsonValue = MghgParcel.CODEC.encode(parcel, extraInfo);
        extraInfo.getValidationResults().logOrThrowValidatorExceptions(LOGGER);
        BsonDocument document = bsonValue.asDocument();
        String json = BsonUtil.toJson(document);
        Files.writeString(file, json, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private boolean upsertDecoded(@Nullable MghgParcel parcel) {
        if (parcel == null || parcel.getId() == null || parcel.getOwner() == null) {
            return false;
        }
        parcelsById.put(parcel.getId(), parcel);
        parcelsByOwner.put(parcel.getOwner(), parcel);
        return true;
    }

    private Path resolveOwnerFile(Path effectiveDirectory, UUID owner) {
        return effectiveDirectory.resolve(owner.toString() + FILE_SUFFIX);
    }

    private List<Path> legacyCandidates() {
        LinkedHashSet<Path> set = new LinkedHashSet<>();
        if (legacyPath != null) {
            set.add(legacyPath);
        }
        set.addAll(MghgStoragePaths.legacyCandidates("parcels.json"));
        return new ArrayList<>(set);
    }

    private List<Path> legacyDirectories(Path targetDirectory) {
        LinkedHashSet<Path> set = new LinkedHashSet<>();
        set.add(targetDirectory);
        set.addAll(MghgStoragePaths.legacyCandidates("parcels"));
        return new ArrayList<>(set);
    }

    private static Path normalizePath(@Nullable Path input, Path fallback) {
        Path base = input == null ? fallback : input;
        return base.toAbsolutePath().normalize();
    }

    private Path resolveDirectory() {
        Path current = activeDirectory;
        if (current != null) {
            return current;
        }
        return preferredDirectory;
    }
}
