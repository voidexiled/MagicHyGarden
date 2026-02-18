package com.voidexiled.magichygarden.features.farming.perks;

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

public final class MghgFarmPerksConfig {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String RESOURCE_PATH = "Server/Farming/Perks/Mghg_Perks.json";
    private static final Path BUILD_PATH = Paths.get("build", "resources", "main", "Server", "Farming", "Perks", "Mghg_Perks.json");

    public static final BuilderCodec<MghgFarmPerksConfig> CODEC =
            BuilderCodec.builder(MghgFarmPerksConfig.class, MghgFarmPerksConfig::new)
                    .append(new KeyedCodec<>("BaseLevel", Codec.INTEGER, true),
                            (o, v) -> o.baseLevel = v == null ? o.baseLevel : v,
                            o -> o.baseLevel)
                    .documentation("Default fertile-soil perk level for parcels without explicit perk state.")
                    .add()
                    .append(new KeyedCodec<>("ReconcileIntervalSeconds", Codec.INTEGER, true),
                            (o, v) -> o.reconcileIntervalSeconds = v == null ? o.reconcileIntervalSeconds : v,
                            o -> o.reconcileIntervalSeconds)
                    .documentation("Interval in seconds for stale tracked-fertile reconciliation on loaded farm worlds.")
                    .add()
                    .append(new KeyedCodec<>("FertileSoilLevels", ArrayCodec.ofBuilderCodec(FertileSoilLevel.CODEC, FertileSoilLevel[]::new), true),
                            (o, v) -> o.fertileSoilLevels = v == null ? o.fertileSoilLevels : v,
                            o -> o.fertileSoilLevels)
                    .documentation("Per-level cap/cost definitions for fertile soil perk.")
                    .add()
                    .append(new KeyedCodec<>("SellMultiplierLevels", ArrayCodec.ofBuilderCodec(SellMultiplierLevel.CODEC, SellMultiplierLevel[]::new), true),
                            (o, v) -> o.sellMultiplierLevels = v == null ? o.sellMultiplierLevels : v,
                            o -> o.sellMultiplierLevels)
                    .documentation("Per-level multiplier/cost definitions for sell multiplier perk.")
                    .add()
                    .append(new KeyedCodec<>("FertileSoilRules", FertileSoilRules.CODEC, true),
                            (o, v) -> o.fertileSoilRules = v == null ? o.fertileSoilRules : v,
                            o -> o.fertileSoilRules)
                    .documentation("Block/item matching rules used for tilling cap tracking and seed placement checks.")
                    .add()
                    .build();

    private int baseLevel = 1;
    private int reconcileIntervalSeconds = 30;
    private FertileSoilLevel[] fertileSoilLevels = new FertileSoilLevel[]{
            new FertileSoilLevel(1, 100, 0.0),
            new FertileSoilLevel(2, 144, 250.0),
            new FertileSoilLevel(3, 196, 750.0),
            new FertileSoilLevel(4, 256, 2000.0)
    };
    private SellMultiplierLevel[] sellMultiplierLevels = new SellMultiplierLevel[]{
            new SellMultiplierLevel(1, 1.00, 0.0),
            new SellMultiplierLevel(2, 1.01, 500.0),
            new SellMultiplierLevel(3, 1.02, 1500.0),
            new SellMultiplierLevel(4, 1.03, 4500.0),
            new SellMultiplierLevel(5, 1.05, 12000.0)
    };
    private FertileSoilRules fertileSoilRules = new FertileSoilRules();

    public int getBaseLevel() {
        return Math.max(1, baseLevel);
    }

    public int getReconcileIntervalSeconds() {
        return Math.max(5, reconcileIntervalSeconds);
    }

    public FertileSoilLevel[] getFertileSoilLevels() {
        return fertileSoilLevels == null ? new FertileSoilLevel[0] : fertileSoilLevels;
    }

    public SellMultiplierLevel[] getSellMultiplierLevels() {
        return sellMultiplierLevels == null ? new SellMultiplierLevel[0] : sellMultiplierLevels;
    }

    public FertileSoilRules getFertileSoilRules() {
        return fertileSoilRules == null ? new FertileSoilRules() : fertileSoilRules;
    }

    public static MghgFarmPerksConfig load() {
        try {
            if (Files.exists(BUILD_PATH)) {
                String payload = Files.readString(BUILD_PATH, StandardCharsets.UTF_8);
                RawJsonReader json = RawJsonReader.fromJsonString(payload);
                return CODEC.decodeJson(json, new ExtraInfo());
            }
        } catch (Exception e) {
            LOGGER.atWarning().log("[MGHG|PERKS] Failed to load from build/resources: %s", e.getMessage());
        }

        try (InputStream stream = MghgFarmPerksConfig.class.getClassLoader().getResourceAsStream(RESOURCE_PATH)) {
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
            LOGGER.atWarning().log("[MGHG|PERKS] Failed to load from resources: %s", e.getMessage());
        }

        LOGGER.atWarning().log("[MGHG|PERKS] No perks config found; using defaults.");
        return new MghgFarmPerksConfig();
    }

    public static final class FertileSoilLevel {
        public static final BuilderCodec<FertileSoilLevel> CODEC =
                BuilderCodec.builder(FertileSoilLevel.class, FertileSoilLevel::new)
                        .append(new KeyedCodec<>("Level", Codec.INTEGER),
                                (o, v) -> o.level = v == null ? 1 : v,
                                o -> o.level)
                        .documentation("Perk level number.")
                        .add()
                        .append(new KeyedCodec<>("MaxFertileBlocks", Codec.INTEGER),
                                (o, v) -> o.maxFertileBlocks = v == null ? 0 : v,
                                o -> o.maxFertileBlocks)
                        .documentation("Maximum tracked fertile blocks allowed at this level.")
                        .add()
                        .append(new KeyedCodec<>("UpgradeCost", Codec.DOUBLE, true),
                                (o, v) -> o.upgradeCost = v == null ? o.upgradeCost : v,
                                o -> o.upgradeCost)
                        .documentation("Economy cost to upgrade into this level.")
                        .add()
                        .build();

        private int level = 1;
        private int maxFertileBlocks = 100;
        private double upgradeCost = 0.0;

        public FertileSoilLevel() {
        }

        public FertileSoilLevel(int level, int maxFertileBlocks, double upgradeCost) {
            this.level = level;
            this.maxFertileBlocks = maxFertileBlocks;
            this.upgradeCost = upgradeCost;
        }

        public int getLevel() {
            return Math.max(1, level);
        }

        public int getMaxFertileBlocks() {
            return Math.max(0, maxFertileBlocks);
        }

        public double getUpgradeCost() {
            return Math.max(0.0, upgradeCost);
        }
    }

    public static final class FertileSoilRules {
        public static final BuilderCodec<FertileSoilRules> CODEC =
                BuilderCodec.builder(FertileSoilRules.class, FertileSoilRules::new)
                        .append(new KeyedCodec<>("TillSourceBlockIds", new ArrayCodec<>(Codec.STRING, String[]::new), true),
                                (o, v) -> o.tillSourceBlockIds = v == null ? o.tillSourceBlockIds : v,
                                o -> o.tillSourceBlockIds)
                        .documentation("Block ids that can be tilled into fertile soil via hoe use.")
                        .add()
                        .append(new KeyedCodec<>("FertileBaseBlockIds", new ArrayCodec<>(Codec.STRING, String[]::new), true),
                                (o, v) -> o.fertileBaseBlockIds = v == null ? o.fertileBaseBlockIds : v,
                                o -> o.fertileBaseBlockIds)
                        .documentation("Block ids counted as fertile for cap tracking.")
                        .add()
                        .append(new KeyedCodec<>("AllowedSeedSoilBaseBlockIds", new ArrayCodec<>(Codec.STRING, String[]::new), true),
                                (o, v) -> o.allowedSeedSoilBaseBlockIds = v == null ? o.allowedSeedSoilBaseBlockIds : v,
                                o -> o.allowedSeedSoilBaseBlockIds)
                        .documentation("Block ids accepted under MGHG seed/crop placement.")
                        .add()
                        .append(new KeyedCodec<>("HoeItemIds", new ArrayCodec<>(Codec.STRING, String[]::new), true),
                                (o, v) -> o.hoeItemIds = v == null ? o.hoeItemIds : v,
                                o -> o.hoeItemIds)
                        .documentation("Exact item ids treated as hoes for tilling checks.")
                        .add()
                        .append(new KeyedCodec<>("HoeItemIdPrefixes", new ArrayCodec<>(Codec.STRING, String[]::new), true),
                                (o, v) -> o.hoeItemIdPrefixes = v == null ? o.hoeItemIdPrefixes : v,
                                o -> o.hoeItemIdPrefixes)
                        .documentation("Item id prefixes treated as hoes for tilling checks.")
                        .add()
                        .build();

        private String[] tillSourceBlockIds = new String[]{
                "Soil_Dirt",
                "Soil_Dirt_Burnt",
                "Soil_Dirt_Cold",
                "Soil_Dirt_Dry",
                "Soil_Grass",
                "Soil_Grass_Burnt",
                "Soil_Grass_Cold",
                "Soil_Grass_Deep",
                "Soil_Grass_Dry",
                "Soil_Grass_Full",
                "Soil_Grass_Sunny",
                "Soil_Leaves",
                "Soil_Mud",
                "Soil_Mud_Dry",
                "Soil_Needles",
                "Soil_Pathway",
                "Mghg_Soil_Dirt",
                "Dirt"
        };
        private String[] fertileBaseBlockIds = new String[]{"Mghg_Soil_Dirt_Tilled"};
        private String[] allowedSeedSoilBaseBlockIds = new String[]{"Mghg_Soil_Dirt_Tilled"};
        private String[] hoeItemIds = new String[]{"Tool_Hoe_Custom"};
        private String[] hoeItemIdPrefixes = new String[0];

        public String[] getTillSourceBlockIds() {
            return tillSourceBlockIds == null ? new String[0] : tillSourceBlockIds;
        }

        public String[] getFertileBaseBlockIds() {
            return fertileBaseBlockIds == null ? new String[0] : fertileBaseBlockIds;
        }

        public String[] getAllowedSeedSoilBaseBlockIds() {
            return allowedSeedSoilBaseBlockIds == null ? new String[0] : allowedSeedSoilBaseBlockIds;
        }

        public String[] getHoeItemIds() {
            return hoeItemIds == null ? new String[0] : hoeItemIds;
        }

        public String[] getHoeItemIdPrefixes() {
            return hoeItemIdPrefixes == null ? new String[0] : hoeItemIdPrefixes;
        }
    }

    public static final class SellMultiplierLevel {
        public static final BuilderCodec<SellMultiplierLevel> CODEC =
                BuilderCodec.builder(SellMultiplierLevel.class, SellMultiplierLevel::new)
                        .append(new KeyedCodec<>("Level", Codec.INTEGER),
                                (o, v) -> o.level = v == null ? 1 : v,
                                o -> o.level)
                        .documentation("Perk level number.")
                        .add()
                        .append(new KeyedCodec<>("Multiplier", Codec.DOUBLE),
                                (o, v) -> o.multiplier = v == null ? 1.0d : v,
                                o -> o.multiplier)
                        .documentation("Sell multiplier applied to final crop sell value at this level.")
                        .add()
                        .append(new KeyedCodec<>("UpgradeCost", Codec.DOUBLE, true),
                                (o, v) -> o.upgradeCost = v == null ? o.upgradeCost : v,
                                o -> o.upgradeCost)
                        .documentation("Economy cost to upgrade into this level.")
                        .add()
                        .build();

        private int level = 1;
        private double multiplier = 1.0d;
        private double upgradeCost = 0.0d;

        public SellMultiplierLevel() {
        }

        public SellMultiplierLevel(int level, double multiplier, double upgradeCost) {
            this.level = level;
            this.multiplier = multiplier;
            this.upgradeCost = upgradeCost;
        }

        public int getLevel() {
            return Math.max(1, level);
        }

        public double getMultiplier() {
            if (Double.isNaN(multiplier) || Double.isInfinite(multiplier)) {
                return 1.0d;
            }
            return Math.max(0.0d, multiplier);
        }

        public double getUpgradeCost() {
            return Math.max(0.0, upgradeCost);
        }
    }
}
