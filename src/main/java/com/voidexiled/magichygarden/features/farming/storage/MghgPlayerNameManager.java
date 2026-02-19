package com.voidexiled.magichygarden.features.farming.storage;

import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.NameMatching;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.util.BsonUtil;
import org.bson.BsonDocument;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MghgPlayerNameManager {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Map<UUID, String> NAMES = new ConcurrentHashMap<>();

    private MghgPlayerNameManager() {
    }

    public static void load() {
        Path primary = storePath();
        Path sourcePath = primary;
        BsonDocument document = Files.exists(primary) ? BsonUtil.readDocumentNow(primary) : null;
        if (document == null) {
            for (Path candidate : MghgStoragePaths.legacyCandidates("player_names.json")) {
                if (candidate.equals(primary)) {
                    continue;
                }
                if (!Files.exists(candidate)) {
                    continue;
                }
                document = BsonUtil.readDocumentNow(candidate);
                if (document != null) {
                    sourcePath = candidate;
                    LOGGER.atInfo().log("[MGHG|NAMES] Loaded legacy name state from %s", candidate);
                    break;
                }
            }
        }
        if (document == null) {
            NAMES.clear();
            return;
        }
        try {
            ExtraInfo extraInfo = new ExtraInfo();
            MghgPlayerNamesState state = MghgPlayerNamesState.CODEC.decode(document, extraInfo);
            extraInfo.getValidationResults().logOrThrowValidatorExceptions(LOGGER);
            NAMES.clear();
            if (state != null && state.getEntries() != null) {
                for (MghgPlayerNamesState.Entry entry : state.getEntries()) {
                    if (entry == null || entry.getUuid() == null) {
                        continue;
                    }
                    String name = normalizeName(entry.getName());
                    if (name != null) {
                        NAMES.put(entry.getUuid(), name);
                    }
                }
            }
            if (!primary.equals(sourcePath)) {
                save();
                LOGGER.atInfo().log("[MGHG|NAMES] Migrated name state into %s", primary);
            }
        } catch (Exception e) {
            LOGGER.atWarning().log("[MGHG|NAMES] Failed to load name state: %s", e.getMessage());
            NAMES.clear();
        }
    }

    public static void save() {
        try {
            MghgPlayerNamesState.Entry[] entries = NAMES.entrySet().stream()
                    .map(e -> new MghgPlayerNamesState.Entry(e.getKey(), e.getValue()))
                    .toArray(MghgPlayerNamesState.Entry[]::new);
            BsonUtil.writeSync(storePath(), MghgPlayerNamesState.CODEC, new MghgPlayerNamesState(entries), LOGGER);
        } catch (Exception e) {
            LOGGER.atWarning().log("[MGHG|NAMES] Failed to save name state: %s", e.getMessage());
        }
    }

    public static void remember(@Nullable UUID uuid, @Nullable String name) {
        if (uuid == null) {
            return;
        }
        String normalized = normalizeName(name);
        if (normalized == null) {
            return;
        }
        String previous = NAMES.put(uuid, normalized);
        if (!normalized.equals(previous)) {
            save();
        }
    }

    public static void remember(@Nullable PlayerRef playerRef) {
        if (playerRef == null) {
            return;
        }
        remember(playerRef.getUuid(), playerRef.getUsername());
    }

    public static @Nonnull String resolve(@Nullable UUID uuid) {
        if (uuid == null) {
            return "unknown";
        }

        Universe universe = Universe.get();
        if (universe != null) {
            PlayerRef ref = universe.getPlayer(uuid);
            if (ref != null) {
                String onlineName = normalizeName(ref.getUsername());
                if (onlineName != null) {
                    remember(uuid, onlineName);
                    return onlineName;
                }
            }
        }

        String persisted = NAMES.get(uuid);
        if (persisted != null && !persisted.isBlank()) {
            return persisted;
        }
        String raw = uuid.toString();
        return raw.length() <= 8 ? raw : raw.substring(0, 8);
    }

    public static @Nullable UUID resolveUuid(@Nullable String rawTarget) {
        String target = normalizeName(rawTarget);
        if (target == null) {
            return null;
        }

        try {
            return UUID.fromString(target);
        } catch (IllegalArgumentException ignored) {
        }

        Universe universe = Universe.get();
        if (universe != null) {
            PlayerRef exact = universe.getPlayer(target, NameMatching.EXACT);
            if (exact != null) {
                remember(exact);
                return exact.getUuid();
            }

            UUID onlineIgnoreCase = null;
            for (PlayerRef online : universe.getPlayers()) {
                if (online == null || online.getUuid() == null) {
                    continue;
                }
                String onlineName = normalizeName(online.getUsername());
                if (onlineName == null || !onlineName.equalsIgnoreCase(target)) {
                    continue;
                }
                remember(online);
                if (onlineIgnoreCase != null && !onlineIgnoreCase.equals(online.getUuid())) {
                    return null;
                }
                onlineIgnoreCase = online.getUuid();
            }
            if (onlineIgnoreCase != null) {
                return onlineIgnoreCase;
            }
        }

        UUID exactCache = null;
        for (Map.Entry<UUID, String> entry : NAMES.entrySet()) {
            String name = normalizeName(entry.getValue());
            if (name == null || !name.equals(target)) {
                continue;
            }
            exactCache = entry.getKey();
            break;
        }
        if (exactCache != null) {
            return exactCache;
        }

        UUID cachedIgnoreCase = null;
        for (Map.Entry<UUID, String> entry : NAMES.entrySet()) {
            String name = normalizeName(entry.getValue());
            if (name == null || !name.equalsIgnoreCase(target)) {
                continue;
            }
            if (cachedIgnoreCase != null && !cachedIgnoreCase.equals(entry.getKey())) {
                return null;
            }
            cachedIgnoreCase = entry.getKey();
        }
        return cachedIgnoreCase;
    }

    public static Path getStorePath() {
        return storePath();
    }

    private static Path storePath() {
        return MghgStoragePaths.resolveInDataRoot("player_names.json");
    }

    private static @Nullable String normalizeName(@Nullable String name) {
        if (name == null) {
            return null;
        }
        String normalized = name.trim();
        return normalized.isBlank() ? null : normalized;
    }
}
