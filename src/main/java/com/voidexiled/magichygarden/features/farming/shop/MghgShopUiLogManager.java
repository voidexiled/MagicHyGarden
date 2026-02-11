package com.voidexiled.magichygarden.features.farming.shop;

import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.util.BsonUtil;
import com.voidexiled.magichygarden.features.farming.storage.MghgStoragePaths;
import org.bson.BsonDocument;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MghgShopUiLogManager {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final int MAX_LINES_PER_PLAYER = 80;
    private static final Map<UUID, ArrayDeque<String>> LOGS = new ConcurrentHashMap<>();

    private MghgShopUiLogManager() {
    }

    public static synchronized void load() {
        Path primary = storePath();
        Path sourcePath = primary;
        BsonDocument document = Files.exists(primary) ? BsonUtil.readDocumentNow(primary) : null;
        if (document == null) {
            for (Path candidate : MghgStoragePaths.legacyCandidates("shop_ui_logs.json")) {
                if (candidate.equals(primary)) {
                    continue;
                }
                if (!Files.exists(candidate)) {
                    continue;
                }
                document = BsonUtil.readDocumentNow(candidate);
                if (document != null) {
                    sourcePath = candidate;
                    LOGGER.atInfo().log("[MGHG|SHOP_UI] Loaded legacy UI logs from %s", candidate);
                    break;
                }
            }
        }

        LOGS.clear();
        if (document == null) {
            return;
        }
        try {
            ExtraInfo extraInfo = new ExtraInfo();
            MghgShopUiLogState state = MghgShopUiLogState.CODEC.decode(document, extraInfo);
            extraInfo.getValidationResults().logOrThrowValidatorExceptions(LOGGER);
            if (state != null) {
                for (MghgShopUiLogState.PlayerLogEntry entry : state.getPlayers()) {
                    if (entry == null || entry.getPlayerUuid() == null) {
                        continue;
                    }
                    ArrayDeque<String> deque = new ArrayDeque<>();
                    for (String line : entry.getLines()) {
                        String safe = sanitize(line);
                        if (safe == null) {
                            continue;
                        }
                        deque.addLast(safe);
                        trim(deque);
                    }
                    LOGS.put(entry.getPlayerUuid(), deque);
                }
            }
            if (!primary.equals(sourcePath)) {
                save();
                LOGGER.atInfo().log("[MGHG|SHOP_UI] Migrated UI logs into %s", primary);
            }
        } catch (Exception e) {
            LOGGER.atWarning().log("[MGHG|SHOP_UI] Failed to load UI logs: %s", e.getMessage());
        }
    }

    public static synchronized void save() {
        try {
            List<MghgShopUiLogState.PlayerLogEntry> entries = new ArrayList<>();
            for (Map.Entry<UUID, ArrayDeque<String>> entry : LOGS.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null || entry.getValue().isEmpty()) {
                    continue;
                }
                entries.add(new MghgShopUiLogState.PlayerLogEntry(
                        entry.getKey(),
                        entry.getValue().toArray(String[]::new)
                ));
            }
            MghgShopUiLogState state = new MghgShopUiLogState(entries.toArray(MghgShopUiLogState.PlayerLogEntry[]::new));
            BsonUtil.writeSync(storePath(), MghgShopUiLogState.CODEC, state, LOGGER);
        } catch (Exception e) {
            LOGGER.atWarning().log("[MGHG|SHOP_UI] Failed to save UI logs: %s", e.getMessage());
        }
    }

    public static synchronized void append(@Nullable UUID playerUuid, @Nullable String message) {
        if (playerUuid == null) {
            return;
        }
        String safe = sanitize(message);
        if (safe == null) {
            return;
        }
        ArrayDeque<String> deque = LOGS.computeIfAbsent(playerUuid, id -> new ArrayDeque<>());
        long nowMillis = Instant.now().toEpochMilli();
        String timestamp = String.format(Locale.ROOT, "[%tT] ", nowMillis);
        deque.addLast(timestamp + safe);
        trim(deque);
        save();
    }

    public static synchronized void appendAll(@Nullable UUID playerUuid, @Nullable String[] lines) {
        if (playerUuid == null || lines == null || lines.length == 0) {
            return;
        }
        ArrayDeque<String> deque = LOGS.computeIfAbsent(playerUuid, id -> new ArrayDeque<>());
        long nowMillis = Instant.now().toEpochMilli();
        String timestamp = String.format(Locale.ROOT, "[%tT] ", nowMillis);
        boolean changed = false;
        for (String line : lines) {
            String safe = sanitize(line);
            if (safe == null) {
                continue;
            }
            deque.addLast(timestamp + safe);
            trim(deque);
            changed = true;
        }
        if (changed) {
            save();
        }
    }

    public static synchronized int clear(@Nullable UUID playerUuid) {
        if (playerUuid == null) {
            return 0;
        }
        ArrayDeque<String> removed = LOGS.remove(playerUuid);
        int count = removed == null ? 0 : removed.size();
        save();
        return count;
    }

    public static synchronized String[] getRecentLines(@Nullable UUID playerUuid, int limit) {
        if (playerUuid == null || limit <= 0) {
            return new String[0];
        }
        ArrayDeque<String> deque = LOGS.get(playerUuid);
        if (deque == null || deque.isEmpty()) {
            return new String[0];
        }
        int max = Math.min(limit, deque.size());
        String[] out = new String[max];
        int skip = deque.size() - max;
        int i = 0;
        int idx = 0;
        for (String line : deque) {
            if (i++ < skip) {
                continue;
            }
            out[idx++] = line;
        }
        return out;
    }

    public static Path getStorePath() {
        return storePath();
    }

    private static Path storePath() {
        return MghgStoragePaths.resolveInDataRoot("shop_ui_logs.json");
    }

    private static void trim(@Nonnull ArrayDeque<String> deque) {
        while (deque.size() > MAX_LINES_PER_PLAYER) {
            deque.removeFirst();
        }
    }

    private static @Nullable String sanitize(@Nullable String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String normalized = input.trim().replace('\n', ' ').replace('\r', ' ');
        if (normalized.length() <= 160) {
            return normalized;
        }
        return normalized.substring(0, 160) + "...";
    }
}
