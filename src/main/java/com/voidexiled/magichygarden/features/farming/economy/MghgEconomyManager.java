package com.voidexiled.magichygarden.features.farming.economy;

import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.util.BsonUtil;
import com.voidexiled.magichygarden.features.farming.storage.MghgStoragePaths;
import org.bson.BsonDocument;

import javax.annotation.Nullable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MghgEconomyManager {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Map<UUID, Double> BALANCES = new ConcurrentHashMap<>();
    private static volatile MghgEconomyConfig CONFIG = new MghgEconomyConfig();

    private MghgEconomyManager() {
    }

    public static void load() {
        CONFIG = MghgEconomyConfig.load();
        Path primary = storePath();
        Path sourcePath = primary;
        BsonDocument document = Files.exists(primary) ? BsonUtil.readDocumentNow(primary) : null;
        if (document == null) {
            for (Path candidate : MghgStoragePaths.legacyCandidates("economy.json")) {
                if (candidate.equals(primary)) {
                    continue;
                }
                if (!Files.exists(candidate)) {
                    continue;
                }
                document = BsonUtil.readDocumentNow(candidate);
                if (document != null) {
                    sourcePath = candidate;
                    LOGGER.atInfo().log("[MGHG|ECO] Loaded legacy economy state from %s", candidate);
                    break;
                }
            }
        }
        if (document == null) {
            BALANCES.clear();
            return;
        }
        try {
            ExtraInfo extraInfo = new ExtraInfo();
            MghgEconomyState state = MghgEconomyState.CODEC.decode(document, extraInfo);
            extraInfo.getValidationResults().logOrThrowValidatorExceptions(LOGGER);
            BALANCES.clear();
            if (state != null && state.getEntries() != null) {
                for (MghgEconomyState.Entry entry : state.getEntries()) {
                    if (entry == null || entry.getUuid() == null) continue;
                    BALANCES.put(entry.getUuid(), entry.getBalance());
                }
            }
            if (!primary.equals(sourcePath)) {
                save();
                LOGGER.atInfo().log("[MGHG|ECO] Migrated economy state into %s", primary);
            }
        } catch (Exception e) {
            LOGGER.atWarning().log("[MGHG|ECO] Failed to load economy state: %s", e.getMessage());
        }
    }

    public static void save() {
        try {
            MghgEconomyState.Entry[] entries = BALANCES.entrySet().stream()
                    .map(e -> new MghgEconomyState.Entry(e.getKey(), e.getValue()))
                    .toArray(MghgEconomyState.Entry[]::new);
            MghgEconomyState state = new MghgEconomyState(entries);
            BsonUtil.writeSync(storePath(), MghgEconomyState.CODEC, state, LOGGER);
        } catch (Exception e) {
            LOGGER.atWarning().log("[MGHG|ECO] Failed to save economy state: %s", e.getMessage());
        }
    }

    public static double getBalance(@Nullable UUID uuid) {
        if (uuid == null) return 0.0;
        ensureAccount(uuid);
        return BALANCES.getOrDefault(uuid, 0.0d);
    }

    public static void setBalance(@Nullable UUID uuid, double amount) {
        if (uuid == null) return;
        double safe = Math.max(0.0, amount);
        BALANCES.put(uuid, safe);
        save();
    }

    public static void deposit(@Nullable UUID uuid, double amount) {
        if (uuid == null) return;
        if (amount <= 0.0) return;
        double current = getBalance(uuid);
        setBalance(uuid, current + amount);
    }

    public static boolean withdraw(@Nullable UUID uuid, double amount) {
        if (uuid == null) return false;
        if (amount <= 0.0) return true;
        double current = getBalance(uuid);
        if (current < amount) return false;
        setBalance(uuid, current - amount);
        return true;
    }

    public static List<MghgEconomyState.Entry> getTopBalances(int limit) {
        int safeLimit = Math.max(1, limit);
        return BALANCES.entrySet()
                .stream()
                .sorted(Comparator.<Map.Entry<UUID, Double>>comparingDouble(Map.Entry::getValue).reversed())
                .limit(safeLimit)
                .map(e -> new MghgEconomyState.Entry(e.getKey(), e.getValue()))
                .toList();
    }

    private static Path storePath() {
        return MghgStoragePaths.resolveInDataRoot("economy.json");
    }

    public static Path getStorePath() {
        return storePath();
    }

    private static void ensureAccount(@Nullable UUID uuid) {
        if (uuid == null) {
            return;
        }

        if (BALANCES.containsKey(uuid)) {
            return;
        }

        MghgEconomyConfig cfg = CONFIG;
        if (cfg == null || !cfg.isAutoCreateAccountOnFirstAccess()) {
            return;
        }

        double initialBalance = cfg.getStartingBalance();
        Double previous = BALANCES.putIfAbsent(uuid, initialBalance);
        if (previous == null) {
            save();
            LOGGER.atInfo().log("[MGHG|ECO] Auto-created account %s with starting balance %.2f", uuid, initialBalance);
        }
    }
}
