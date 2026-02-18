package com.voidexiled.magichygarden.features.farming.perks;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.voidexiled.magichygarden.features.farming.economy.MghgEconomyManager;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcel;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcelManager;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcelPerks;
import com.voidexiled.magichygarden.features.farming.state.MghgBlockIdUtil;
import com.voidexiled.magichygarden.features.farming.worlds.MghgFarmWorldManager;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

public final class MghgFarmPerkManager {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static volatile MghgFarmPerksConfig CONFIG = new MghgFarmPerksConfig();
    private static volatile NavigableMap<Integer, MghgFarmPerksConfig.FertileSoilLevel> FERTILE_LEVELS =
            defaultLevels();
    private static volatile NavigableMap<Integer, MghgFarmPerksConfig.SellMultiplierLevel> SELL_MULTIPLIER_LEVELS =
            defaultSellLevels();
    private static volatile Set<String> TILL_SOURCE_IDS = Set.of(
            "soil_dirt",
            "soil_dirt_burnt",
            "soil_dirt_cold",
            "soil_dirt_dry",
            "soil_grass",
            "soil_grass_burnt",
            "soil_grass_cold",
            "soil_grass_deep",
            "soil_grass_dry",
            "soil_grass_full",
            "soil_grass_sunny",
            "soil_leaves",
            "soil_mud",
            "soil_mud_dry",
            "soil_needles",
            "soil_pathway",
            "mghg_soil_dirt",
            "dirt"
    );
    private static volatile Set<String> FERTILE_BASE_IDS = Set.of("mghg_soil_dirt_tilled");
    private static volatile String PRIMARY_FERTILE_BASE_ID = "Mghg_Soil_Dirt_Tilled";
    private static volatile Set<String> ALLOWED_SEED_SOIL_IDS = Set.of("mghg_soil_dirt_tilled");
    private static volatile Set<String> HOE_ITEM_IDS = Set.of();
    private static volatile Set<String> HOE_ITEM_PREFIXES = Set.of();

    private MghgFarmPerkManager() {
    }

    public static void load() {
        reload();
    }

    public static synchronized void reload() {
        CONFIG = MghgFarmPerksConfig.load();
        rebuildCaches(CONFIG);
        LOGGER.atInfo().log(
                "[MGHG|PERKS] Loaded perks config: baseLevel=%d levels=%d reconcile=%ds",
                getBaseLevel(),
                FERTILE_LEVELS.size(),
                CONFIG.getReconcileIntervalSeconds()
        );
    }

    public static MghgFarmPerksConfig getConfig() {
        return CONFIG;
    }

    public static int getBaseLevel() {
        return Math.max(1, CONFIG.getBaseLevel());
    }

    public static int getReconcileIntervalSeconds() {
        return CONFIG.getReconcileIntervalSeconds();
    }

    public static MghgParcelPerks ensureParcelPerks(@Nullable MghgParcel parcel) {
        if (parcel == null) {
            MghgParcelPerks fallback = new MghgParcelPerks();
            fallback.setFertileSoilLevel(getBaseLevel());
            return fallback;
        }
        MghgParcelPerks perks = parcel.getPerks();
        boolean changed = false;
        if (perks == null) {
            perks = new MghgParcelPerks();
            perks.setFertileSoilLevel(getBaseLevel());
            parcel.setPerks(perks);
            changed = true;
        }
        int currentLevel = perks.getFertileSoilLevel();
        if (currentLevel < 1) {
            perks.setFertileSoilLevel(getBaseLevel());
            changed = true;
        }
        if (perks.getSellMultiplierLevel() < 1) {
            perks.setSellMultiplierLevel(getBaseLevel());
            changed = true;
        }
        if (changed) {
            MghgParcelManager.saveSoon();
        }
        return perks;
    }

    public static int getFertileSoilLevel(@Nullable MghgParcel parcel) {
        return ensureParcelPerks(parcel).getFertileSoilLevel();
    }

    public static int setFertileSoilLevel(@Nullable MghgParcel parcel, int desiredLevel) {
        MghgParcelPerks perks = ensureParcelPerks(parcel);
        int applied = clampToConfiguredLevel(Math.max(1, desiredLevel));
        if (perks.getFertileSoilLevel() != applied) {
            perks.setFertileSoilLevel(applied);
            MghgParcelManager.saveSoon();
        }
        return applied;
    }

    public static int getTrackedFertileCount(@Nullable MghgParcel parcel) {
        return ensureParcelPerks(parcel).getTrackedFertileBlockCount();
    }

    public static int getSellMultiplierLevel(@Nullable MghgParcel parcel) {
        return ensureParcelPerks(parcel).getSellMultiplierLevel();
    }

    public static int setSellMultiplierLevel(@Nullable MghgParcel parcel, int desiredLevel) {
        MghgParcelPerks perks = ensureParcelPerks(parcel);
        int applied = clampToConfiguredSellLevel(Math.max(1, desiredLevel));
        if (perks.getSellMultiplierLevel() != applied) {
            perks.setSellMultiplierLevel(applied);
            MghgParcelManager.saveSoon();
        }
        return applied;
    }

    public static double getSellMultiplier(@Nullable MghgParcel parcel) {
        int level = getSellMultiplierLevel(parcel);
        MghgFarmPerksConfig.SellMultiplierLevel def = resolveSellDefinitionForLevel(level);
        return def == null ? 1.0d : Math.max(0.0d, def.getMultiplier());
    }

    public static int getMaxConfiguredSellMultiplierLevel() {
        if (SELL_MULTIPLIER_LEVELS.isEmpty()) {
            return 1;
        }
        return SELL_MULTIPLIER_LEVELS.lastKey();
    }

    public static @Nullable MghgFarmPerksConfig.SellMultiplierLevel getNextSellMultiplierLevel(@Nullable MghgParcel parcel) {
        if (SELL_MULTIPLIER_LEVELS.isEmpty()) {
            return null;
        }
        int current = getSellMultiplierLevel(parcel);
        var next = SELL_MULTIPLIER_LEVELS.higherEntry(current);
        return next == null ? null : next.getValue();
    }

    public static int getFertileSoilCap(@Nullable MghgParcel parcel) {
        int level = getFertileSoilLevel(parcel);
        MghgFarmPerksConfig.FertileSoilLevel def = resolveDefinitionForLevel(level);
        return def == null ? 0 : Math.max(0, def.getMaxFertileBlocks());
    }

    public static int getMaxConfiguredFertileLevel() {
        if (FERTILE_LEVELS.isEmpty()) {
            return 1;
        }
        return FERTILE_LEVELS.lastKey();
    }

    public static @Nullable MghgFarmPerksConfig.FertileSoilLevel getNextFertileLevel(@Nullable MghgParcel parcel) {
        if (FERTILE_LEVELS.isEmpty()) {
            return null;
        }
        int current = getFertileSoilLevel(parcel);
        var next = FERTILE_LEVELS.higherEntry(current);
        return next == null ? null : next.getValue();
    }

    public static boolean canTrackFertileBlock(@Nullable MghgParcel parcel, @Nullable String key) {
        MghgParcelPerks perks = ensureParcelPerks(parcel);
        if (perks.containsTrackedFertileBlock(key)) {
            return true;
        }
        return perks.getTrackedFertileBlockCount() < getFertileSoilCap(parcel);
    }

    public static boolean trackFertileBlock(@Nullable MghgParcel parcel, @Nullable String key) {
        if (parcel == null) {
            return false;
        }
        MghgParcelPerks perks = ensureParcelPerks(parcel);
        if (perks.containsTrackedFertileBlock(key)) {
            return false;
        }
        if (perks.getTrackedFertileBlockCount() >= getFertileSoilCap(parcel)) {
            return false;
        }
        boolean changed = perks.addTrackedFertileBlock(key);
        if (changed) {
            MghgParcelManager.saveSoon();
        }
        return changed;
    }

    public static boolean untrackFertileBlock(@Nullable MghgParcel parcel, @Nullable String key) {
        if (parcel == null) {
            return false;
        }
        MghgParcelPerks perks = ensureParcelPerks(parcel);
        boolean changed = perks.removeTrackedFertileBlock(key);
        if (changed) {
            MghgParcelManager.saveSoon();
        }
        return changed;
    }

    public static Set<String> snapshotTrackedFertileBlocks(@Nullable MghgParcel parcel) {
        return ensureParcelPerks(parcel).snapshotTrackedFertileBlocks();
    }

    public static void replaceTrackedFertileBlocks(@Nullable MghgParcel parcel, @Nullable Set<String> keys) {
        if (parcel == null) {
            return;
        }
        MghgParcelPerks perks = ensureParcelPerks(parcel);
        perks.replaceTrackedFertileBlocks(keys);
        MghgParcelManager.saveSoon();
    }

    public static UpgradeResult tryUpgradeFertileSoil(@Nullable UUID actorUuid, @Nullable MghgParcel parcel) {
        if (parcel == null || parcel.getOwner() == null) {
            return UpgradeResult.invalidTarget();
        }
        MghgParcelPerks perks = ensureParcelPerks(parcel);
        MghgFarmPerksConfig.FertileSoilLevel next = getNextFertileLevel(parcel);
        if (next == null) {
            return UpgradeResult.maxLevel(
                    perks.getFertileSoilLevel(),
                    getFertileSoilCap(parcel)
            );
        }

        UUID payer = actorUuid == null ? parcel.getOwner() : actorUuid;
        double cost = Math.max(0.0d, next.getUpgradeCost());
        if (cost > 0.0d && !MghgEconomyManager.withdraw(payer, cost)) {
            double balance = MghgEconomyManager.getBalance(payer);
            return UpgradeResult.insufficientFunds(
                    perks.getFertileSoilLevel(),
                    getFertileSoilCap(parcel),
                    next.getLevel(),
                    next.getMaxFertileBlocks(),
                    cost,
                    balance
            );
        }

        perks.setFertileSoilLevel(next.getLevel());
        MghgParcelManager.saveSoon();
        return UpgradeResult.success(
                perks.getFertileSoilLevel(),
                getFertileSoilCap(parcel),
                next.getLevel(),
                next.getMaxFertileBlocks(),
                cost,
                MghgEconomyManager.getBalance(payer)
        );
    }

    public static UpgradeResult tryUpgradeSellMultiplier(@Nullable UUID actorUuid, @Nullable MghgParcel parcel) {
        if (parcel == null || parcel.getOwner() == null) {
            return UpgradeResult.invalidTarget();
        }
        MghgParcelPerks perks = ensureParcelPerks(parcel);
        MghgFarmPerksConfig.SellMultiplierLevel next = getNextSellMultiplierLevel(parcel);
        if (next == null) {
            return UpgradeResult.maxLevel(
                    perks.getSellMultiplierLevel(),
                    getMaxConfiguredSellMultiplierLevel()
            );
        }

        UUID payer = actorUuid == null ? parcel.getOwner() : actorUuid;
        double cost = Math.max(0.0d, next.getUpgradeCost());
        if (cost > 0.0d && !MghgEconomyManager.withdraw(payer, cost)) {
            double balance = MghgEconomyManager.getBalance(payer);
            return UpgradeResult.insufficientFunds(
                    perks.getSellMultiplierLevel(),
                    getMaxConfiguredSellMultiplierLevel(),
                    next.getLevel(),
                    next.getLevel(),
                    cost,
                    balance
            );
        }

        perks.setSellMultiplierLevel(next.getLevel());
        MghgParcelManager.saveSoon();
        return UpgradeResult.success(
                perks.getSellMultiplierLevel(),
                getMaxConfiguredSellMultiplierLevel(),
                next.getLevel(),
                next.getLevel(),
                cost,
                MghgEconomyManager.getBalance(payer)
        );
    }

    public static double resolveSellMultiplierForContext(@Nullable UUID playerUuid, @Nullable World world) {
        MghgParcel parcel = null;
        if (world != null && MghgFarmWorldManager.isFarmWorld(world)) {
            UUID owner = MghgFarmWorldManager.getOwnerFromFarmWorld(world);
            if (owner != null) {
                parcel = MghgParcelManager.getByOwner(owner);
            }
        }
        if (parcel == null && playerUuid != null) {
            parcel = MghgParcelManager.getByOwner(playerUuid);
        }
        return getSellMultiplier(parcel);
    }

    public static boolean isHoeItem(@Nullable String itemId) {
        String normalized = normalize(itemId);
        if (normalized == null) {
            return false;
        }
        if (HOE_ITEM_IDS.contains(normalized)) {
            return true;
        }
        String base = normalize(MghgBlockIdUtil.resolveBaseIdFromStateId(itemId));
        if (base != null && HOE_ITEM_IDS.contains(base)) {
            return true;
        }
        for (String prefix : HOE_ITEM_PREFIXES) {
            if (normalized.startsWith(prefix) || (base != null && base.startsWith(prefix))) {
                return true;
            }
        }
        return false;
    }

    public static boolean isAnyHoeItem(@Nullable String itemId) {
        String normalized = normalize(itemId);
        if (normalized == null) {
            return false;
        }
        String base = normalize(MghgBlockIdUtil.resolveBaseIdFromStateId(itemId));
        if (normalized.startsWith("tool_hoe_")
                || normalized.startsWith("mghg_tool_hoe_")
                || (base != null && (base.startsWith("tool_hoe_") || base.startsWith("mghg_tool_hoe_")))) {
            return true;
        }
        if (HOE_ITEM_IDS.contains(normalized) || (base != null && HOE_ITEM_IDS.contains(base))) {
            return true;
        }
        for (String prefix : HOE_ITEM_PREFIXES) {
            if (normalized.startsWith(prefix) || (base != null && base.startsWith(prefix))) {
                return true;
            }
        }
        return false;
    }

    public static boolean isTillSourceBlock(@Nullable BlockType blockType) {
        return matchesBlockIds(blockType, TILL_SOURCE_IDS);
    }

    public static boolean isFertileBaseBlock(@Nullable BlockType blockType) {
        return matchesBlockIds(blockType, FERTILE_BASE_IDS);
    }

    public static boolean isAllowedSeedSoil(@Nullable BlockType blockType) {
        return matchesBlockIds(blockType, ALLOWED_SEED_SOIL_IDS);
    }

    public static @Nullable String getPrimaryFertileBaseBlockId() {
        return PRIMARY_FERTILE_BASE_ID;
    }

    public static @Nullable String toBlockKey(@Nullable Vector3i blockPos) {
        if (blockPos == null) {
            return null;
        }
        return toBlockKey(blockPos.x, blockPos.y, blockPos.z);
    }

    public static String toBlockKey(int x, int y, int z) {
        return x + ":" + y + ":" + z;
    }

    public static @Nullable Vector3i parseBlockKey(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] parts = raw.trim().split(":");
        if (parts.length != 3) {
            return null;
        }
        try {
            int x = Integer.parseInt(parts[0]);
            int y = Integer.parseInt(parts[1]);
            int z = Integer.parseInt(parts[2]);
            return new Vector3i(x, y, z);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static boolean matchesBlockIds(@Nullable BlockType blockType, Set<String> configuredIds) {
        if (blockType == null || configuredIds.isEmpty()) {
            return false;
        }
        for (String candidate : collectBlockIdCandidates(blockType)) {
            if (configuredIds.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> collectBlockIdCandidates(@Nullable BlockType blockType) {
        if (blockType == null) {
            return Set.of();
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();
        addNormalized(out, blockType.getId());
        addNormalized(out, MghgBlockIdUtil.resolveBaseIdFromStateId(blockType.getId()));

        BlockType base = MghgBlockIdUtil.resolveBaseBlockType(blockType);
        if (base != null) {
            addNormalized(out, base.getId());
            addNormalized(out, MghgBlockIdUtil.resolveBaseIdFromStateId(base.getId()));
            if (base.getItem() != null) {
                addNormalized(out, base.getItem().getId());
            }
        }
        if (blockType.getItem() != null) {
            addNormalized(out, blockType.getItem().getId());
        }
        return out;
    }

    private static int clampToConfiguredLevel(int desired) {
        if (FERTILE_LEVELS.isEmpty()) {
            return Math.max(1, desired);
        }
        int min = FERTILE_LEVELS.firstKey();
        int max = FERTILE_LEVELS.lastKey();
        if (desired < min) {
            return min;
        }
        if (desired > max) {
            return max;
        }
        return desired;
    }

    private static int clampToConfiguredSellLevel(int desired) {
        if (SELL_MULTIPLIER_LEVELS.isEmpty()) {
            return Math.max(1, desired);
        }
        int min = SELL_MULTIPLIER_LEVELS.firstKey();
        int max = SELL_MULTIPLIER_LEVELS.lastKey();
        if (desired < min) {
            return min;
        }
        if (desired > max) {
            return max;
        }
        return desired;
    }

    private static @Nullable MghgFarmPerksConfig.FertileSoilLevel resolveDefinitionForLevel(int level) {
        if (FERTILE_LEVELS.isEmpty()) {
            return null;
        }
        var exact = FERTILE_LEVELS.get(level);
        if (exact != null) {
            return exact;
        }
        var floor = FERTILE_LEVELS.floorEntry(level);
        if (floor != null) {
            return floor.getValue();
        }
        return FERTILE_LEVELS.firstEntry().getValue();
    }

    private static @Nullable MghgFarmPerksConfig.SellMultiplierLevel resolveSellDefinitionForLevel(int level) {
        if (SELL_MULTIPLIER_LEVELS.isEmpty()) {
            return null;
        }
        var exact = SELL_MULTIPLIER_LEVELS.get(level);
        if (exact != null) {
            return exact;
        }
        var floor = SELL_MULTIPLIER_LEVELS.floorEntry(level);
        if (floor != null) {
            return floor.getValue();
        }
        return SELL_MULTIPLIER_LEVELS.firstEntry().getValue();
    }

    private static void rebuildCaches(@Nullable MghgFarmPerksConfig config) {
        MghgFarmPerksConfig safe = config == null ? new MghgFarmPerksConfig() : config;

        TreeMap<Integer, MghgFarmPerksConfig.FertileSoilLevel> levels = new TreeMap<>();
        for (MghgFarmPerksConfig.FertileSoilLevel level : safe.getFertileSoilLevels()) {
            if (level == null) {
                continue;
            }
            levels.put(level.getLevel(), level);
        }
        if (levels.isEmpty()) {
            levels.putAll(defaultLevels());
        }

        TreeMap<Integer, MghgFarmPerksConfig.SellMultiplierLevel> sellLevels = new TreeMap<>();
        for (MghgFarmPerksConfig.SellMultiplierLevel level : safe.getSellMultiplierLevels()) {
            if (level == null) {
                continue;
            }
            sellLevels.put(level.getLevel(), level);
        }
        if (sellLevels.isEmpty()) {
            sellLevels.putAll(defaultSellLevels());
        }

        MghgFarmPerksConfig.FertileSoilRules rules = safe.getFertileSoilRules();
        Set<String> tillSources = toNormalizedSet(rules.getTillSourceBlockIds());
        Set<String> fertileBases = toNormalizedSet(rules.getFertileBaseBlockIds());
        Set<String> seedSoils = toNormalizedSet(rules.getAllowedSeedSoilBaseBlockIds());
        Set<String> hoeIds = toNormalizedSet(rules.getHoeItemIds());
        Set<String> hoePrefixes = toNormalizedSet(rules.getHoeItemIdPrefixes());

        FERTILE_LEVELS = Collections.unmodifiableNavigableMap(levels);
        SELL_MULTIPLIER_LEVELS = Collections.unmodifiableNavigableMap(sellLevels);
        TILL_SOURCE_IDS = tillSources.isEmpty()
                ? Set.of(
                "soil_dirt",
                "soil_dirt_burnt",
                "soil_dirt_cold",
                "soil_dirt_dry",
                "soil_grass",
                "soil_grass_burnt",
                "soil_grass_cold",
                "soil_grass_deep",
                "soil_grass_dry",
                "soil_grass_full",
                "soil_grass_sunny",
                "soil_leaves",
                "soil_mud",
                "soil_mud_dry",
                "soil_needles",
                "soil_pathway",
                "mghg_soil_dirt",
                "dirt"
        )
                : Collections.unmodifiableSet(tillSources);
        FERTILE_BASE_IDS = fertileBases.isEmpty()
                ? Set.of("mghg_soil_dirt_tilled")
                : Collections.unmodifiableSet(fertileBases);
        PRIMARY_FERTILE_BASE_ID = resolvePrimaryFertileBaseId(rules.getFertileBaseBlockIds());
        ALLOWED_SEED_SOIL_IDS = seedSoils.isEmpty()
                ? Set.of("mghg_soil_dirt_tilled")
                : Collections.unmodifiableSet(seedSoils);
        HOE_ITEM_IDS = Collections.unmodifiableSet(hoeIds);
        HOE_ITEM_PREFIXES = Collections.unmodifiableSet(hoePrefixes);
    }

    private static NavigableMap<Integer, MghgFarmPerksConfig.FertileSoilLevel> defaultLevels() {
        TreeMap<Integer, MghgFarmPerksConfig.FertileSoilLevel> levels = new TreeMap<>();
        levels.put(1, new MghgFarmPerksConfig.FertileSoilLevel(1, 100, 0.0));
        return levels;
    }

    private static NavigableMap<Integer, MghgFarmPerksConfig.SellMultiplierLevel> defaultSellLevels() {
        TreeMap<Integer, MghgFarmPerksConfig.SellMultiplierLevel> levels = new TreeMap<>();
        levels.put(1, new MghgFarmPerksConfig.SellMultiplierLevel(1, 1.0d, 0.0d));
        return levels;
    }

    private static Set<String> toNormalizedSet(@Nullable String[] raw) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (raw == null) {
            return out;
        }
        for (String value : raw) {
            addNormalized(out, value);
        }
        return out;
    }

    private static String resolvePrimaryFertileBaseId(@Nullable String[] raw) {
        if (raw != null) {
            for (String value : raw) {
                if (value == null) {
                    continue;
                }
                String trimmed = value.trim();
                if (!trimmed.isEmpty()) {
                    return trimmed;
                }
            }
        }
        return "Mghg_Soil_Dirt_Tilled";
    }

    private static void addNormalized(Set<String> out, @Nullable String value) {
        String normalized = normalize(value);
        if (normalized != null) {
            out.add(normalized);
        }
    }

    private static @Nullable String normalize(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }

    public enum UpgradeStatus {
        SUCCESS,
        MAX_LEVEL,
        INVALID_TARGET,
        INSUFFICIENT_FUNDS
    }

    public static final class UpgradeResult {
        private final UpgradeStatus status;
        private final int currentLevel;
        private final int currentCap;
        private final int nextLevel;
        private final int nextCap;
        private final double upgradeCost;
        private final double balanceAfter;

        private UpgradeResult(
                UpgradeStatus status,
                int currentLevel,
                int currentCap,
                int nextLevel,
                int nextCap,
                double upgradeCost,
                double balanceAfter
        ) {
            this.status = status;
            this.currentLevel = currentLevel;
            this.currentCap = currentCap;
            this.nextLevel = nextLevel;
            this.nextCap = nextCap;
            this.upgradeCost = upgradeCost;
            this.balanceAfter = balanceAfter;
        }

        public static UpgradeResult success(
                int currentLevel,
                int currentCap,
                int nextLevel,
                int nextCap,
                double upgradeCost,
                double balanceAfter
        ) {
            return new UpgradeResult(
                    UpgradeStatus.SUCCESS,
                    currentLevel,
                    currentCap,
                    nextLevel,
                    nextCap,
                    upgradeCost,
                    balanceAfter
            );
        }

        public static UpgradeResult maxLevel(int currentLevel, int currentCap) {
            return new UpgradeResult(
                    UpgradeStatus.MAX_LEVEL,
                    currentLevel,
                    currentCap,
                    currentLevel,
                    currentCap,
                    0.0d,
                    0.0d
            );
        }

        public static UpgradeResult invalidTarget() {
            return new UpgradeResult(
                    UpgradeStatus.INVALID_TARGET,
                    0,
                    0,
                    0,
                    0,
                    0.0d,
                    0.0d
            );
        }

        public static UpgradeResult insufficientFunds(
                int currentLevel,
                int currentCap,
                int nextLevel,
                int nextCap,
                double upgradeCost,
                double balanceAfter
        ) {
            return new UpgradeResult(
                    UpgradeStatus.INSUFFICIENT_FUNDS,
                    currentLevel,
                    currentCap,
                    nextLevel,
                    nextCap,
                    upgradeCost,
                    balanceAfter
            );
        }

        public UpgradeStatus getStatus() {
            return status;
        }

        public int getCurrentLevel() {
            return currentLevel;
        }

        public int getCurrentCap() {
            return currentCap;
        }

        public int getNextLevel() {
            return nextLevel;
        }

        public int getNextCap() {
            return nextCap;
        }

        public double getUpgradeCost() {
            return upgradeCost;
        }

        public double getBalanceAfter() {
            return balanceAfter;
        }
    }
}
