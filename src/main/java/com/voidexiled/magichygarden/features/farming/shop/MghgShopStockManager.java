package com.voidexiled.magichygarden.features.farming.shop;

import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.util.BsonUtil;
import com.voidexiled.magichygarden.features.farming.storage.MghgStoragePaths;
import org.bson.BsonDocument;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public final class MghgShopStockManager {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static volatile MghgShopConfig CONFIG;
    private static volatile MghgShopStockState STATE;
    private static ScheduledFuture<?> task;

    private MghgShopStockManager() {
    }

    public static void start() {
        if (task != null) return;
        reload();
        loadState();
        if (STATE == null) {
            restock();
        }
        task = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(
                MghgShopStockManager::tickSafe,
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
        saveState();
    }

    public static void reload() {
        CONFIG = MghgShopConfig.load();
    }

    public static synchronized void reloadFromDisk() {
        reload();
        loadState();
        if (STATE == null) {
            restock();
        }
    }

    public static @Nullable MghgShopConfig getConfig() {
        return CONFIG;
    }

    public static @Nullable MghgShopStockState getState() {
        return STATE;
    }

    public static @Nullable Instant getNextRestockAt() {
        MghgShopStockState state = STATE;
        return state == null ? null : state.getNextRestockAt();
    }

    public static long getRemainingRestockSeconds() {
        Instant next = getNextRestockAt();
        if (next == null) {
            return 0L;
        }
        long seconds = Duration.between(Instant.now(), next).getSeconds();
        return Math.max(0L, seconds);
    }

    public static MghgShopConfig.ShopItem[] getConfiguredItems() {
        MghgShopConfig cfg = CONFIG;
        return cfg == null ? new MghgShopConfig.ShopItem[0] : cfg.getItems();
    }

    public static @Nullable MghgShopConfig.ShopItem getConfiguredItem(@Nullable String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return null;
        }
        MghgShopConfig cfg = CONFIG;
        if (cfg == null) {
            return null;
        }
        for (MghgShopConfig.ShopItem item : cfg.getItems()) {
            if (item == null || item.getId() == null) continue;
            if (itemId.equalsIgnoreCase(item.getId())) {
                return item;
            }
        }
        return null;
    }

    public static int getStock(String itemId) {
        MghgShopStockState state = STATE;
        if (state == null || itemId == null) return 0;
        for (MghgShopStockState.StockEntry entry : state.getStocks()) {
            if (entry == null) continue;
            if (itemId.equalsIgnoreCase(entry.getId())) {
                return entry.getStock();
            }
        }
        return 0;
    }

    public static int getPlayerStock(@Nullable UUID playerUuid, String itemId) {
        int base = getStock(itemId);
        if (base <= 0 || playerUuid == null || itemId == null) {
            return Math.max(0, base);
        }
        int purchased = getPlayerPurchased(playerUuid, itemId);
        return Math.max(0, base - purchased);
    }

    public static boolean consumeStock(String itemId, int amount) {
        return consumePlayerStock(null, itemId, amount);
    }

    public static synchronized boolean consumePlayerStock(@Nullable UUID playerUuid, String itemId, int amount) {
        if (amount <= 0) return true;
        MghgShopStockState state = STATE;
        if (state == null || itemId == null) return false;
        if (playerUuid == null) {
            for (MghgShopStockState.StockEntry entry : state.getStocks()) {
                if (entry == null) continue;
                if (itemId.equalsIgnoreCase(entry.getId())) {
                    if (entry.getStock() < amount) return false;
                    entry.setStock(entry.getStock() - amount);
                    saveState();
                    return true;
                }
            }
            return false;
        }

        int available = getPlayerStock(playerUuid, itemId);
        if (available < amount) {
            return false;
        }
        upsertPlayerPurchased(state, playerUuid, itemId, amount);
        saveState();
        return true;
    }

    public static boolean addStock(String itemId, int amount) {
        return releasePlayerStock(null, itemId, amount);
    }

    public static synchronized boolean releasePlayerStock(@Nullable UUID playerUuid, String itemId, int amount) {
        if (amount <= 0) return true;
        MghgShopStockState state = STATE;
        if (state == null || itemId == null) return false;
        if (playerUuid == null) {
            for (MghgShopStockState.StockEntry entry : state.getStocks()) {
                if (entry == null) continue;
                if (itemId.equalsIgnoreCase(entry.getId())) {
                    entry.setStock(entry.getStock() + amount);
                    saveState();
                    return true;
                }
            }
            return false;
        }

        List<MghgShopStockState.PlayerPurchaseEntry> purchases = copyPurchases(state.getPlayerPurchases());
        boolean changed = false;
        for (MghgShopStockState.PlayerPurchaseEntry entry : purchases) {
            if (entry == null || entry.getPlayerUuid() == null || entry.getId() == null) {
                continue;
            }
            if (playerUuid.equals(entry.getPlayerUuid()) && itemId.equalsIgnoreCase(entry.getId())) {
                int updated = Math.max(0, entry.getPurchased() - amount);
                entry.setPurchased(updated);
                changed = true;
                break;
            }
        }
        if (!changed) {
            return false;
        }
        purchases.removeIf(e -> e == null || e.getPurchased() <= 0);
        state.setPlayerPurchases(purchases.toArray(MghgShopStockState.PlayerPurchaseEntry[]::new));
        saveState();
        return true;
    }

    public static synchronized void forceRestockNow() {
        restock();
    }

    public static synchronized boolean setStock(String itemId, int amount) {
        if (itemId == null || itemId.isBlank()) {
            return false;
        }
        String normalizedItemId = itemId.trim();
        int safeAmount = Math.max(0, amount);
        MghgShopStockState state = STATE;
        if (state == null) {
            restock();
            state = STATE;
            if (state == null) {
                return false;
            }
        }
        for (MghgShopStockState.StockEntry entry : state.getStocks()) {
            if (entry == null || entry.getId() == null) {
                continue;
            }
            if (normalizedItemId.equalsIgnoreCase(entry.getId())) {
                entry.setStock(safeAmount);
                saveState();
                return true;
            }
        }

        ArrayList<MghgShopStockState.StockEntry> entries = new ArrayList<>();
        for (MghgShopStockState.StockEntry entry : state.getStocks()) {
            if (entry != null) {
                entries.add(entry);
            }
        }
        entries.add(new MghgShopStockState.StockEntry(normalizedItemId, safeAmount));
        state.setStocks(entries.toArray(MghgShopStockState.StockEntry[]::new));
        saveState();
        return true;
    }

    private static void tickSafe() {
        try {
            tick();
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("[MGHG|SHOP] Tick failed.");
        }
    }

    private static void tick() {
        if (CONFIG == null) return;
        MghgShopStockState state = STATE;
        if (state == null || state.getNextRestockAt() == null) {
            restock();
            return;
        }
        if (!Instant.now().isBefore(state.getNextRestockAt())) {
            restock();
        }
    }

    private static synchronized void restock() {
        MghgShopConfig cfg = CONFIG;
        if (cfg == null) return;
        MghgShopConfig.ShopItem[] items = cfg.getItems();

        Map<String, Integer> stock = new HashMap<>();
        int positiveRestocks = 0;
        for (MghgShopConfig.ShopItem item : items) {
            if (item == null || item.getId() == null || item.getId().isBlank()) continue;
            int value = resolveRestockQuantity(item);
            if (value > 0) {
                positiveRestocks++;
            }
            stock.put(item.getId(), value);
        }

        MghgShopStockState.StockEntry[] entries = stock.entrySet().stream()
                .map(e -> new MghgShopStockState.StockEntry(e.getKey(), e.getValue()))
                .toArray(MghgShopStockState.StockEntry[]::new);

        Instant next = Instant.now().plusSeconds(resolveNextRestockSeconds(cfg));
        STATE = new MghgShopStockState(next, entries, new MghgShopStockState.PlayerPurchaseEntry[0]);
        saveState();
        LOGGER.atInfo().log("[MGHG|SHOP] Restocked %d/%d items with positive stock. Next restock at %s.",
                positiveRestocks, entries.length, next);
    }

    private static int getPlayerPurchased(@Nonnull UUID playerUuid, @Nonnull String itemId) {
        MghgShopStockState state = STATE;
        if (state == null) {
            return 0;
        }
        int purchased = 0;
        for (MghgShopStockState.PlayerPurchaseEntry entry : state.getPlayerPurchases()) {
            if (entry == null || entry.getPlayerUuid() == null || entry.getId() == null) {
                continue;
            }
            if (playerUuid.equals(entry.getPlayerUuid()) && itemId.equalsIgnoreCase(entry.getId())) {
                purchased += Math.max(0, entry.getPurchased());
            }
        }
        return purchased;
    }

    private static void upsertPlayerPurchased(
            @Nonnull MghgShopStockState state,
            @Nonnull UUID playerUuid,
            @Nonnull String itemId,
            int delta
    ) {
        if (delta <= 0) {
            return;
        }
        List<MghgShopStockState.PlayerPurchaseEntry> purchases = copyPurchases(state.getPlayerPurchases());
        for (MghgShopStockState.PlayerPurchaseEntry entry : purchases) {
            if (entry == null || entry.getPlayerUuid() == null || entry.getId() == null) {
                continue;
            }
            if (playerUuid.equals(entry.getPlayerUuid()) && itemId.equalsIgnoreCase(entry.getId())) {
                entry.setPurchased(entry.getPurchased() + delta);
                state.setPlayerPurchases(purchases.toArray(MghgShopStockState.PlayerPurchaseEntry[]::new));
                return;
            }
        }
        purchases.add(new MghgShopStockState.PlayerPurchaseEntry(playerUuid, itemId, delta));
        state.setPlayerPurchases(purchases.toArray(MghgShopStockState.PlayerPurchaseEntry[]::new));
    }

    private static List<MghgShopStockState.PlayerPurchaseEntry> copyPurchases(
            @Nullable MghgShopStockState.PlayerPurchaseEntry[] source
    ) {
        ArrayList<MghgShopStockState.PlayerPurchaseEntry> out = new ArrayList<>();
        if (source == null) {
            return out;
        }
        for (MghgShopStockState.PlayerPurchaseEntry entry : source) {
            if (entry != null) {
                out.add(entry);
            }
        }
        return out;
    }

    private static int resolveNextRestockSeconds(MghgShopConfig cfg) {
        int min = Math.max(1, cfg.getRestockIntervalMinSeconds());
        int max = Math.max(min, cfg.getRestockIntervalMaxSeconds());
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    private static int resolveRestockQuantity(@Nonnull MghgShopConfig.ShopItem item) {
        int min = Math.max(0, item.getMinStock());
        int max = Math.max(min, item.getMaxStock());
        if (max <= 0) {
            return 0;
        }
        if (!rollRestock(item)) {
            return 0;
        }

        // RestockChance controls whether this item receives a positive stock this cycle.
        int positiveMin = Math.max(1, min);
        int positiveMax = Math.max(positiveMin, max);
        return (positiveMax == positiveMin)
                ? positiveMin
                : ThreadLocalRandom.current().nextInt(positiveMin, positiveMax + 1);
    }

    private static boolean rollRestock(MghgShopConfig.ShopItem item) {
        double raw = item.getRestockChance();
        if (Double.isNaN(raw) || raw <= 0.0) {
            return false;
        }
        double chance = raw;
        if (chance > 1.0) {
            chance = chance / 100.0;
        }
        if (chance >= 1.0) {
            return true;
        }
        return ThreadLocalRandom.current().nextDouble() <= chance;
    }

    private static void loadState() {
        try {
            Path primary = storePath();
            Path sourcePath = primary;
            BsonDocument document = Files.exists(primary) ? BsonUtil.readDocumentNow(primary) : null;
            if (document == null) {
                for (Path candidate : MghgStoragePaths.legacyCandidates("shop_stock.json")) {
                    if (candidate.equals(primary)) {
                        continue;
                    }
                    if (!Files.exists(candidate)) {
                        continue;
                    }
                    document = BsonUtil.readDocumentNow(candidate);
                    if (document != null) {
                        sourcePath = candidate;
                        LOGGER.atInfo().log("[MGHG|SHOP] Loaded legacy stock state from %s", candidate);
                        break;
                    }
                }
            }
            if (document == null) {
                STATE = null;
                return;
            }
            ExtraInfo extraInfo = new ExtraInfo();
            MghgShopStockState state = MghgShopStockState.CODEC.decode(document, extraInfo);
            extraInfo.getValidationResults().logOrThrowValidatorExceptions(LOGGER);
            STATE = state;
            if (STATE != null && !primary.equals(sourcePath)) {
                saveState();
                LOGGER.atInfo().log("[MGHG|SHOP] Migrated stock state into %s", primary);
            }
        } catch (Exception e) {
            LOGGER.atWarning().log("[MGHG|SHOP] Failed to load stock state: %s", e.getMessage());
            STATE = null;
        }
    }

    private static void saveState() {
        if (STATE == null) return;
        try {
            BsonUtil.writeSync(storePath(), MghgShopStockState.CODEC, STATE, LOGGER);
        } catch (Exception e) {
            LOGGER.atWarning().log("[MGHG|SHOP] Failed to save stock state: %s", e.getMessage());
        }
    }

    private static Path storePath() {
        return MghgStoragePaths.resolveInDataRoot("shop_stock.json");
    }

    public static Path getStorePath() {
        return storePath();
    }
}
