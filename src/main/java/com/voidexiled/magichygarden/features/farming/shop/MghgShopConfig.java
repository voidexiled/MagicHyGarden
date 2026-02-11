package com.voidexiled.magichygarden.features.farming.shop;

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
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public final class MghgShopConfig {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String RESOURCE_PATH = "Server/Farming/Shop/Mghg_Shop.json";
    private static final Path BUILD_PATH = Paths.get("build", "resources", "main", "Server", "Farming", "Shop", "Mghg_Shop.json");

    public static final BuilderCodec<MghgShopConfig> CODEC =
            BuilderCodec.builder(MghgShopConfig.class, MghgShopConfig::new)
                    .append(new KeyedCodec<>("RestockIntervalMinSeconds", Codec.INTEGER),
                            (o, v) -> o.restockIntervalMinSeconds = v,
                            o -> o.restockIntervalMinSeconds)
                    .documentation("Minimum seconds between restocks.")
                    .add()
                    .append(new KeyedCodec<>("RestockIntervalMaxSeconds", Codec.INTEGER, true),
                            (o, v) -> o.restockIntervalMaxSeconds = v == null ? o.restockIntervalMaxSeconds : v,
                            o -> o.restockIntervalMaxSeconds)
                    .documentation("Maximum seconds between restocks.")
                    .add()
                    .append(new KeyedCodec<>("RequireFarmWorldForTransactions", Codec.BOOLEAN, true),
                            (o, v) -> o.requireFarmWorldForTransactions = v == null ? o.requireFarmWorldForTransactions : v,
                            o -> o.requireFarmWorldForTransactions)
                    .documentation("If true, buy/sell can only run in farm worlds.")
                    .add()
                    .append(new KeyedCodec<>("RequireParcelAccessForTransactions", Codec.BOOLEAN, true),
                            (o, v) -> o.requireParcelAccessForTransactions = v == null ? o.requireParcelAccessForTransactions : v,
                            o -> o.requireParcelAccessForTransactions)
                    .documentation("If true, buy/sell requires parcel membership and position inside parcel bounds.")
                    .add()
                    .append(new KeyedCodec<>("RequireBenchProximityForTransactions", Codec.BOOLEAN, true),
                            (o, v) -> o.requireBenchProximityForTransactions = v == null ? o.requireBenchProximityForTransactions : v,
                            o -> o.requireBenchProximityForTransactions)
                    .documentation("If true, buy/sell requires a configured bench block nearby.")
                    .add()
                    .append(new KeyedCodec<>("BenchSearchRadius", Codec.INTEGER, true),
                            (o, v) -> o.benchSearchRadius = v == null ? o.benchSearchRadius : v,
                            o -> o.benchSearchRadius)
                    .documentation("Bench proximity radius in blocks.")
                    .add()
                    .append(new KeyedCodec<>("BenchBlockIds", new ArrayCodec<>(Codec.STRING, String[]::new), true),
                            (o, v) -> o.benchBlockIds = v == null ? o.benchBlockIds : v,
                            o -> o.benchBlockIds)
                    .documentation("Block ids that count as benches for buy/sell proximity checks.")
                    .add()
                    .append(new KeyedCodec<>("Items",
                                    ArrayCodec.ofBuilderCodec(ShopItem.CODEC, ShopItem[]::new)),
                            (o, v) -> o.items = v,
                            o -> o.items)
                    .documentation("Items available in the shop.")
                    .add()
                    .build();

    private int restockIntervalMinSeconds = 1200;
    private int restockIntervalMaxSeconds = 1800;
    private boolean requireFarmWorldForTransactions = true;
    private boolean requireParcelAccessForTransactions = true;
    private boolean requireBenchProximityForTransactions = false;
    private int benchSearchRadius = 6;
    private String[] benchBlockIds = new String[0];
    private ShopItem[] items = new ShopItem[0];

    public int getRestockIntervalMinSeconds() {
        return restockIntervalMinSeconds;
    }

    public int getRestockIntervalMaxSeconds() {
        return restockIntervalMaxSeconds;
    }

    public boolean isRequireFarmWorldForTransactions() {
        return requireFarmWorldForTransactions;
    }

    public boolean isRequireParcelAccessForTransactions() {
        return requireParcelAccessForTransactions;
    }

    public boolean isRequireBenchProximityForTransactions() {
        return requireBenchProximityForTransactions;
    }

    public int getBenchSearchRadius() {
        return benchSearchRadius;
    }

    public String[] getBenchBlockIds() {
        return benchBlockIds == null ? new String[0] : benchBlockIds;
    }

    public ShopItem[] getItems() {
        return items == null ? new ShopItem[0] : items;
    }

    public static MghgShopConfig load() {
        try {
            if (Files.exists(BUILD_PATH)) {
                String payload = Files.readString(BUILD_PATH, StandardCharsets.UTF_8);
                RawJsonReader json = RawJsonReader.fromJsonString(payload);
                return CODEC.decodeJson(json, new ExtraInfo());
            }
        } catch (Exception e) {
            LOGGER.atWarning().log("[MGHG|SHOP] Failed to load from build/resources: %s", e.getMessage());
        }

        try (InputStream stream = MghgShopConfig.class.getClassLoader().getResourceAsStream(RESOURCE_PATH)) {
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
            LOGGER.atWarning().log("[MGHG|SHOP] Failed to load from resources: %s", e.getMessage());
        }

        LOGGER.atWarning().log("[MGHG|SHOP] No shop config found; using defaults.");
        return new MghgShopConfig();
    }

    public static final class ShopItem {
        public static final BuilderCodec<ShopItem> CODEC =
                BuilderCodec.builder(ShopItem.class, ShopItem::new)
                        .append(new KeyedCodec<>("Id", Codec.STRING),
                                (o, v) -> o.id = v,
                                o -> o.id)
                        .documentation("Item asset id.")
                        .add()
                        .append(new KeyedCodec<>("MinStock", Codec.INTEGER),
                                (o, v) -> o.minStock = v,
                                o -> o.minStock)
                        .documentation("Minimum stock on restock.")
                        .add()
                        .append(new KeyedCodec<>("MaxStock", Codec.INTEGER, true),
                                (o, v) -> o.maxStock = v == null ? o.maxStock : v,
                                o -> o.maxStock)
                        .documentation("Maximum stock on restock.")
                        .add()
                        .append(new KeyedCodec<>("RestockChance", Codec.DOUBLE, true),
                                (o, v) -> o.restockChance = v == null ? o.restockChance : v,
                                o -> o.restockChance)
                        .documentation("Chance to appear in a restock (0-1 or 0-100).")
                        .add()
                        .append(new KeyedCodec<>("BuyPrice", Codec.DOUBLE, true),
                                (o, v) -> o.buyPrice = v == null ? o.buyPrice : v,
                                o -> o.buyPrice)
                        .documentation("Price to buy from shop.")
                        .add()
                        .append(new KeyedCodec<>("BuyItemId", Codec.STRING, true),
                                (o, v) -> o.buyItemId = v == null ? o.buyItemId : v,
                                o -> o.buyItemId)
                        .documentation("Item id given to player when buying. Defaults to Id.")
                        .add()
                        .append(new KeyedCodec<>("SellPrice", Codec.DOUBLE, true),
                                (o, v) -> o.sellPrice = v == null ? o.sellPrice : v,
                                o -> o.sellPrice)
                        .documentation("Price to sell to shop.")
                        .add()
                        .append(new KeyedCodec<>("SellItemIds", new ArrayCodec<>(Codec.STRING, String[]::new), true),
                                (o, v) -> o.sellItemIds = v == null ? o.sellItemIds : v,
                                o -> o.sellItemIds)
                        .documentation("Accepted item ids for selling. If empty, BuyItemId/Id are used.")
                        .add()
                        .append(new KeyedCodec<>("EnableMetaSellPricing", Codec.BOOLEAN, true),
                                (o, v) -> o.enableMetaSellPricing = v == null ? o.enableMetaSellPricing : v,
                                o -> o.enableMetaSellPricing)
                        .documentation("When true, sell price can be adjusted by MGHG metadata.")
                        .add()
                        .append(new KeyedCodec<>("SellSizeBonusPerPoint", Codec.DOUBLE, true),
                                (o, v) -> o.sellSizeBonusPerPoint = v == null ? o.sellSizeBonusPerPoint : v,
                                o -> o.sellSizeBonusPerPoint)
                        .documentation("Deprecated legacy flat bonus per size point. Prefer size multipliers.")
                        .add()
                        .append(new KeyedCodec<>("SellWeightBonusPerKg", Codec.DOUBLE, true),
                                (o, v) -> o.sellWeightBonusPerKg = v == null ? o.sellWeightBonusPerKg : v,
                                o -> o.sellWeightBonusPerKg)
                        .documentation("Deprecated legacy flat bonus per weight kilogram.")
                        .add()
                        .append(new KeyedCodec<>("SellSizeMultiplierMinSize", Codec.DOUBLE, true),
                                (o, v) -> o.sellSizeMultiplierMinSize = v == null ? o.sellSizeMultiplierMinSize : v,
                                o -> o.sellSizeMultiplierMinSize)
                        .documentation("Size point that maps to SellSizeMultiplierAtMin.")
                        .add()
                        .append(new KeyedCodec<>("SellSizeMultiplierMaxSize", Codec.DOUBLE, true),
                                (o, v) -> o.sellSizeMultiplierMaxSize = v == null ? o.sellSizeMultiplierMaxSize : v,
                                o -> o.sellSizeMultiplierMaxSize)
                        .documentation("Size point that maps to SellSizeMultiplierAtMax.")
                        .add()
                        .append(new KeyedCodec<>("SellSizeMultiplierAtMin", Codec.DOUBLE, true),
                                (o, v) -> o.sellSizeMultiplierAtMin = v == null ? o.sellSizeMultiplierAtMin : v,
                                o -> o.sellSizeMultiplierAtMin)
                        .documentation("Price multiplier applied when size equals SellSizeMultiplierMinSize.")
                        .add()
                        .append(new KeyedCodec<>("SellSizeMultiplierAtMax", Codec.DOUBLE, true),
                                (o, v) -> o.sellSizeMultiplierAtMax = v == null ? o.sellSizeMultiplierAtMax : v,
                                o -> o.sellSizeMultiplierAtMax)
                        .documentation("Price multiplier applied when size equals SellSizeMultiplierMaxSize.")
                        .add()
                        .append(new KeyedCodec<>("ClimateMultiplierNone", Codec.DOUBLE, true),
                                (o, v) -> o.climateMultiplierNone = v == null ? o.climateMultiplierNone : v,
                                o -> o.climateMultiplierNone)
                        .add()
                        .append(new KeyedCodec<>("ClimateMultiplierRain", Codec.DOUBLE, true),
                                (o, v) -> o.climateMultiplierRain = v == null ? o.climateMultiplierRain : v,
                                o -> o.climateMultiplierRain)
                        .add()
                        .append(new KeyedCodec<>("ClimateMultiplierSnow", Codec.DOUBLE, true),
                                (o, v) -> o.climateMultiplierSnow = v == null ? o.climateMultiplierSnow : v,
                                o -> o.climateMultiplierSnow)
                        .add()
                        .append(new KeyedCodec<>("ClimateMultiplierFrozen", Codec.DOUBLE, true),
                                (o, v) -> o.climateMultiplierFrozen = v == null ? o.climateMultiplierFrozen : v,
                                o -> o.climateMultiplierFrozen)
                        .add()
                        .append(new KeyedCodec<>("LunarMultiplierNone", Codec.DOUBLE, true),
                                (o, v) -> o.lunarMultiplierNone = v == null ? o.lunarMultiplierNone : v,
                                o -> o.lunarMultiplierNone)
                        .add()
                        .append(new KeyedCodec<>("LunarMultiplierDawnlit", Codec.DOUBLE, true),
                                (o, v) -> o.lunarMultiplierDawnlit = v == null ? o.lunarMultiplierDawnlit : v,
                                o -> o.lunarMultiplierDawnlit)
                        .add()
                        .append(new KeyedCodec<>("LunarMultiplierDawnbound", Codec.DOUBLE, true),
                                (o, v) -> o.lunarMultiplierDawnbound = v == null ? o.lunarMultiplierDawnbound : v,
                                o -> o.lunarMultiplierDawnbound)
                        .add()
                        .append(new KeyedCodec<>("LunarMultiplierAmberlit", Codec.DOUBLE, true),
                                (o, v) -> o.lunarMultiplierAmberlit = v == null ? o.lunarMultiplierAmberlit : v,
                                o -> o.lunarMultiplierAmberlit)
                        .add()
                        .append(new KeyedCodec<>("LunarMultiplierAmberbound", Codec.DOUBLE, true),
                                (o, v) -> o.lunarMultiplierAmberbound = v == null ? o.lunarMultiplierAmberbound : v,
                                o -> o.lunarMultiplierAmberbound)
                        .add()
                        .append(new KeyedCodec<>("RarityMultiplierNone", Codec.DOUBLE, true),
                                (o, v) -> o.rarityMultiplierNone = v == null ? o.rarityMultiplierNone : v,
                                o -> o.rarityMultiplierNone)
                        .add()
                        .append(new KeyedCodec<>("RarityMultiplierGold", Codec.DOUBLE, true),
                                (o, v) -> o.rarityMultiplierGold = v == null ? o.rarityMultiplierGold : v,
                                o -> o.rarityMultiplierGold)
                        .add()
                        .append(new KeyedCodec<>("RarityMultiplierRainbow", Codec.DOUBLE, true),
                                (o, v) -> o.rarityMultiplierRainbow = v == null ? o.rarityMultiplierRainbow : v,
                                o -> o.rarityMultiplierRainbow)
                        .add()
                        .build();

        private String id;
        private int minStock = 0;
        private int maxStock = 0;
        private double restockChance = 1.0;
        private double buyPrice = 0.0;
        private double sellPrice = 0.0;
        private String buyItemId;
        private String[] sellItemIds = new String[0];
        private boolean enableMetaSellPricing = true;
        private double sellSizeBonusPerPoint = 0.0;
        private double sellWeightBonusPerKg = 0.0;
        private double sellSizeMultiplierMinSize = 50.0;
        private double sellSizeMultiplierMaxSize = 100.0;
        private double sellSizeMultiplierAtMin = 1.0;
        private double sellSizeMultiplierAtMax = 2.0;
        private double climateMultiplierNone = 1.0;
        private double climateMultiplierRain = 1.0;
        private double climateMultiplierSnow = 1.0;
        private double climateMultiplierFrozen = 1.0;
        private double lunarMultiplierNone = 1.0;
        private double lunarMultiplierDawnlit = 1.0;
        private double lunarMultiplierDawnbound = 1.0;
        private double lunarMultiplierAmberlit = 1.0;
        private double lunarMultiplierAmberbound = 1.0;
        private double rarityMultiplierNone = 1.0;
        private double rarityMultiplierGold = 1.0;
        private double rarityMultiplierRainbow = 1.0;

        public @Nullable String getId() {
            return id;
        }

        public int getMinStock() {
            return minStock;
        }

        public int getMaxStock() {
            return maxStock;
        }

        public double getRestockChance() {
            return restockChance;
        }

        public double getBuyPrice() {
            return buyPrice;
        }

        public double getSellPrice() {
            return sellPrice;
        }

        public @Nullable String getBuyItemId() {
            return buyItemId;
        }

        public String[] getSellItemIds() {
            return sellItemIds == null ? new String[0] : sellItemIds;
        }

        public boolean isEnableMetaSellPricing() {
            return enableMetaSellPricing;
        }

        public double getSellSizeBonusPerPoint() {
            return sellSizeBonusPerPoint;
        }

        public double getSellWeightBonusPerKg() {
            return sellWeightBonusPerKg;
        }

        public double getSellSizeMultiplierMinSize() {
            return sellSizeMultiplierMinSize;
        }

        public double getSellSizeMultiplierMaxSize() {
            return sellSizeMultiplierMaxSize;
        }

        public double getSellSizeMultiplierAtMin() {
            return sellSizeMultiplierAtMin;
        }

        public double getSellSizeMultiplierAtMax() {
            return sellSizeMultiplierAtMax;
        }

        public double getClimateMultiplier(@Nullable String climateName) {
            String key = climateName == null ? "NONE" : climateName.trim().toUpperCase(Locale.ROOT);
            return switch (key) {
                case "RAIN" -> climateMultiplierRain;
                case "SNOW" -> climateMultiplierSnow;
                case "FROZEN" -> climateMultiplierFrozen;
                default -> climateMultiplierNone;
            };
        }

        public double getLunarMultiplier(@Nullable String lunarName) {
            String key = lunarName == null ? "NONE" : lunarName.trim().toUpperCase(Locale.ROOT);
            return switch (key) {
                case "DAWNLIT" -> lunarMultiplierDawnlit;
                case "DAWNBOUND" -> lunarMultiplierDawnbound;
                case "AMBERLIT" -> lunarMultiplierAmberlit;
                case "AMBERBOUND" -> lunarMultiplierAmberbound;
                default -> lunarMultiplierNone;
            };
        }

        public double getRarityMultiplier(@Nullable String rarityName) {
            String key = rarityName == null ? "NONE" : rarityName.trim().toUpperCase(Locale.ROOT);
            return switch (key) {
                case "GOLD" -> rarityMultiplierGold;
                case "RAINBOW" -> rarityMultiplierRainbow;
                default -> rarityMultiplierNone;
            };
        }

        public @Nullable String resolveBuyItemId() {
            if (buyItemId != null && !buyItemId.isBlank()) {
                return buyItemId.trim();
            }
            return id == null || id.isBlank() ? null : id.trim();
        }

        public String[] resolveSellItemIds() {
            Set<String> resolved = new LinkedHashSet<>();
            for (String candidate : getSellItemIds()) {
                if (candidate == null || candidate.isBlank()) {
                    continue;
                }
                resolved.add(candidate.trim());
            }
            String buy = resolveBuyItemId();
            if (resolved.isEmpty() && buy != null) {
                resolved.add(buy);
                String seedDerived = deriveCropItemIdFromSeedId(buy);
                if (seedDerived != null) {
                    resolved.add(seedDerived);
                }
            }
            if (resolved.isEmpty() && id != null && !id.isBlank()) {
                resolved.add(id.trim());
            }
            return resolved.toArray(String[]::new);
        }

        private static @Nullable String deriveCropItemIdFromSeedId(@Nullable String value) {
            if (value == null || value.isBlank()) {
                return null;
            }
            String raw = value.trim();
            int idx = raw.indexOf("_Seeds_");
            if (idx < 0) {
                return null;
            }
            String prefix = raw.substring(0, idx);
            String suffix = raw.substring(idx + "_Seeds_".length());
            if (suffix.isBlank()) {
                return null;
            }
            return prefix + "_Crop_" + suffix + "_Item";
        }
    }
}
