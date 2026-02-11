package com.voidexiled.magichygarden.features.farming.modifiers;

import com.hypixel.hytale.builtin.adventure.farming.states.FarmingBlock;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.util.RawJsonReader;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.farming.GrowthModifierAsset;
import com.hypixel.hytale.server.core.asset.type.weather.config.Weather;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.voidexiled.magichygarden.features.farming.components.MghgCropData;
import com.voidexiled.magichygarden.features.farming.state.MghgWeatherResolver;
import com.voidexiled.magichygarden.features.farming.visuals.MghgCropStageSync;
import it.unimi.dsi.fastutil.ints.IntSet;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * GrowthModifier para MagicHyGarden:
 * - Slowdown por size (50–100 por default).
 * - Mutación climática progresiva (NONE -> RAIN/SNOW -> FROZEN) con cooldown y allowlist de Weather IDs.
 * - Sincroniza FarmingBlock.currentStageSet con StageSets mghg_* y fuerza refresh visual del stage actual.
 */
public class MghgCropGrowthModifierAsset extends GrowthModifierAsset {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String RESOURCE_PATH = "Server/Farming/Modifiers/Size.json";
    private static volatile MghgCropGrowthModifierAsset LAST_LOADED;
    public static final int DEFAULT_SIZE_MIN = 50;
    public static final int DEFAULT_SIZE_MAX = 100;

    public static final BuilderCodec<MghgCropGrowthModifierAsset> CODEC =
            BuilderCodec.builder(MghgCropGrowthModifierAsset.class, MghgCropGrowthModifierAsset::new, ABSTRACT_CODEC)
                    .documentation("MagicHyGarden: size slowdown + climate mutation rolls + stageSet sync.")

                    // --- size tuning ---
                    .<Integer>append(new KeyedCodec<>("SizeMin", Codec.INTEGER),
                            (a, v) -> a.sizeMin = v,
                            a -> a.sizeMin)
                    .documentation("Minimum size rolled for new crops (seed).")
                    .add()
                    .<Integer>append(new KeyedCodec<>("SizeMax", Codec.INTEGER),
                            (a, v) -> a.sizeMax = v,
                            a -> a.sizeMax)
                    .documentation("Maximum size rolled for new crops (seed).")
                    .add()
                    .<Double>append(new KeyedCodec<>("MinGrowthMultiplierAtMaxSize", Codec.DOUBLE),
                            (a, v) -> a.minGrowthMultiplierAtMaxSize = v,
                            a -> a.minGrowthMultiplierAtMaxSize)
                    .documentation("Growth multiplier when Size == SizeMax (clamped 0.01..1.0).")
                    .add()

                    // --- initial seed tuning ---
                    .<Float>append(new KeyedCodec<>("InitialRarityGoldChance", Codec.FLOAT),
                            (a, v) -> a.initialRarityGoldChance = v,
                            a -> a.initialRarityGoldChance)
                    .documentation("Chance to seed GOLD rarity on newly planted crops.")
                    .add()
                    .<Float>append(new KeyedCodec<>("InitialRarityRainbowChance", Codec.FLOAT),
                            (a, v) -> a.initialRarityRainbowChance = v,
                            a -> a.initialRarityRainbowChance)
                    .documentation("Chance to seed RAINBOW rarity on newly planted crops.")
                    .add()

                    // --- mutation tuning ---
                    .<Integer>append(new KeyedCodec<>("MutationRollCooldownSeconds", Codec.INTEGER),
                            (a, v) -> a.mutationRollCooldownSeconds = v,
                            a -> a.mutationRollCooldownSeconds)
                    .documentation("Default cooldown per slot (seconds) when a rule omits CooldownSeconds.")
                    .add()
                    .<Double>append(new KeyedCodec<>("MutationChanceRain", Codec.DOUBLE),
                            (a, v) -> a.mutationChanceRain = v,
                            a -> a.mutationChanceRain)
                    .documentation("Fallback: used only if mutation rules asset is empty/unavailable.")
                    .add()
                    .<Double>append(new KeyedCodec<>("MutationChanceSnow", Codec.DOUBLE),
                            (a, v) -> a.mutationChanceSnow = v,
                            a -> a.mutationChanceSnow)
                    .documentation("Fallback: used only if mutation rules asset is empty/unavailable.")
                    .add()
                    .<Double>append(new KeyedCodec<>("MutationChanceFrozen", Codec.DOUBLE),
                            (a, v) -> a.mutationChanceFrozen = v,
                            a -> a.mutationChanceFrozen)
                    .documentation("Fallback: used only if mutation rules asset is empty/unavailable.")
                    .add()

                    // --- weather allowlists ---
                    .<String[]>append(new KeyedCodec<>("RainWeathers", Codec.STRING_ARRAY),
                            (a, v) -> a.rainWeathers = v,
                            a -> a.rainWeathers)
                    .documentation("Fallback weather list for RAIN (only when no rules exist).")
                    .addValidator(Weather.VALIDATOR_CACHE.getArrayValidator())
                    .add()
                    .<String[]>append(new KeyedCodec<>("SnowWeathers", Codec.STRING_ARRAY),
                            (a, v) -> a.snowWeathers = v,
                            a -> a.snowWeathers)
                    .documentation("Fallback weather list for SNOW (only when no rules exist).")
                    .addValidator(Weather.VALIDATOR_CACHE.getArrayValidator())
                    .add()
                    .<String[]>append(new KeyedCodec<>("FrozenWeathers", Codec.STRING_ARRAY),
                            (a, v) -> a.frozenWeathers = v,
                            a -> a.frozenWeathers)
                    .documentation("Fallback weather list that counts as rain+snow (only when no rules exist).")
                    .addValidator(Weather.VALIDATOR_CACHE.getArrayValidator())
                    .add()

                    // --- droplist overrides + extras ---
                    .<String[]>append(new KeyedCodec<>("DropListOverrideFrom", Codec.STRING_ARRAY),
                            (a, v) -> a.dropListOverrideFrom = v,
                            a -> a.dropListOverrideFrom)
                    .documentation("Parallel array: original dropListId to override.")
                    .add()
                    .<String[]>append(new KeyedCodec<>("DropListOverrideTo", Codec.STRING_ARRAY),
                            (a, v) -> a.dropListOverrideTo = v,
                            a -> a.dropListOverrideTo)
                    .documentation("Parallel array: replacement dropListId.")
                    .add()
                    .<String[]>append(new KeyedCodec<>("ExtraDropListsAll", Codec.STRING_ARRAY),
                            (a, v) -> a.extraDropListsAll = v,
                            a -> a.extraDropListsAll)
                    .documentation("Extra drop lists appended for all crops.")
                    .add()
                    .<String[]>append(new KeyedCodec<>("ExtraDropListsGold", Codec.STRING_ARRAY),
                            (a, v) -> a.extraDropListsGold = v,
                            a -> a.extraDropListsGold)
                    .documentation("Extra drop lists appended only for GOLD rarity.")
                    .add()
                    .<String[]>append(new KeyedCodec<>("ExtraDropListsRainbow", Codec.STRING_ARRAY),
                            (a, v) -> a.extraDropListsRainbow = v,
                            a -> a.extraDropListsRainbow)
                    .documentation("Extra drop lists appended only for RAINBOW rarity.")
                    .add()

                    .afterDecode(MghgCropGrowthModifierAsset::buildCaches)
                    .build();

    // ---------- Config fields ----------
    protected int sizeMin = DEFAULT_SIZE_MIN;
    protected int sizeMax = DEFAULT_SIZE_MAX;

    /** Multiplier mínimo cuando size == sizeMax (clamp 0.01..1.0). */
    protected double minGrowthMultiplierAtMaxSize = 0.65;

    protected float initialRarityGoldChance = 0.005f;
    protected float initialRarityRainbowChance = 0.0005f;

    protected int mutationRollCooldownSeconds = 300;
    protected double mutationChanceRain = 0.12;
    protected double mutationChanceSnow = 0.12;
    protected double mutationChanceFrozen = 0.18;

    @Nullable protected String[] rainWeathers;
    @Nullable protected String[] snowWeathers;
    @Nullable protected String[] frozenWeathers;

    @Nullable protected IntSet rainWeatherIds;
    @Nullable protected IntSet snowWeatherIds;
    @Nullable protected IntSet frozenWeatherIds;

    @Nullable protected String[] dropListOverrideFrom;
    @Nullable protected String[] dropListOverrideTo;
    @Nullable protected String[] extraDropListsAll;
    @Nullable protected String[] extraDropListsGold;
    @Nullable protected String[] extraDropListsRainbow;

    @Nullable protected Map<String, String> dropListOverrideMap;

    // ---------- GrowthModifierAsset ----------
    @Override
    public double getCurrentGrowthMultiplier(
            CommandBuffer<ChunkStore> commandBuffer,
            Ref<ChunkStore> sectionRef,
            Ref<ChunkStore> blockRef,
            int x, int y, int z,
            boolean initialTick
    ) {
        double base = super.getCurrentGrowthMultiplier(commandBuffer, sectionRef, blockRef, x, y, z, initialTick);

        MghgCropData data;
        try {
            ComponentType<ChunkStore, MghgCropData> type = MghgCropData.getComponentType();
            if (type == null) {
                return base;
            }
            data = commandBuffer.getComponent(blockRef, type);
        } catch (IllegalStateException e) {
            // Component type not registered for this store (safe fallback for vanilla crops / early load).
            return base;
        }
        if (data == null) {
            return base;
        }

        ChunkSection section = commandBuffer.getComponent(sectionRef, ChunkSection.getComponentType());
        Ref<ChunkStore> chunkColRef = section != null ? section.getChunkColumnReference() : null;
        BlockChunk blockChunk = chunkColRef != null ? commandBuffer.getComponent(chunkColRef, BlockChunk.getComponentType()) : null;
        BlockComponentChunk blockComponentChunk = chunkColRef != null ? commandBuffer.getComponent(chunkColRef, BlockComponentChunk.getComponentType()) : null;

        boolean dirty = false;

        // 1) stage-set sync + refresh visual
        FarmingBlock farmingBlock = commandBuffer.getComponent(blockRef, FarmingBlock.getComponentType());
        dirty |= MghgCropStageSync.syncStageSetAndRefresh(
                commandBuffer,
                sectionRef,
                blockRef,
                chunkColRef,
                blockChunk,
                data,
                farmingBlock,
                x, y, z
        );

        // 2) slowdown por size
        double sizeMultiplier = computeSizeMultiplier(data.getSize());

        if (dirty) {
            if (blockComponentChunk != null) blockComponentChunk.markNeedsSaving();
            if (blockChunk != null) blockChunk.markNeedsSaving();
        }

        return base * sizeMultiplier;
    }

    // ---------- Decode helpers ----------
    private static void buildCaches(MghgCropGrowthModifierAsset a) {
        a.rainWeatherIds = MghgWeatherResolver.toWeatherIdSet(a.rainWeathers);
        a.snowWeatherIds = MghgWeatherResolver.toWeatherIdSet(a.snowWeathers);
        a.frozenWeatherIds = MghgWeatherResolver.toWeatherIdSet(a.frozenWeathers);
        a.dropListOverrideMap = buildDropListOverrideMap(a.dropListOverrideFrom, a.dropListOverrideTo);
        LAST_LOADED = a;
    }

    private static @Nullable Map<String, String> buildDropListOverrideMap(
            @Nullable String[] from,
            @Nullable String[] to
    ) {
        if (from == null || to == null) {
            return null;
        }
        if (from.length != to.length) {
            LOGGER.atWarning().log(
                    "DropListOverrideFrom/To length mismatch: from=%d to=%d (extra entries ignored)",
                    from.length,
                    to.length
            );
        }

        int count = Math.min(from.length, to.length);
        if (count == 0) {
            return null;
        }

        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < count; i++) {
            String key = from[i];
            String value = to[i];
            if (key == null || key.isBlank() || value == null || value.isBlank()) {
                continue;
            }
            map.put(key, value);
        }

        return map.isEmpty() ? null : map;
    }

    public static @Nullable MghgCropGrowthModifierAsset reloadFromDisk() {
        MghgCropGrowthModifierAsset cfg = loadConfig();
        if (cfg == null) {
            LOGGER.atWarning().log("[MGHG|GROWTH_MOD] Failed to reload Size.json (no config found).");
            return null;
        }
        LOGGER.atInfo().log("[MGHG|GROWTH_MOD] Reloaded Size.json successfully.");
        return cfg;
    }

    private static @Nullable MghgCropGrowthModifierAsset loadConfig() {
        Path buildPath = Paths.get("build", "resources", "main", "Server", "Farming", "Modifiers", "Size.json");
        if (Files.exists(buildPath)) {
            try {
                String payload = Files.readString(buildPath, StandardCharsets.UTF_8);
                RawJsonReader json = RawJsonReader.fromJsonString(payload);
                return CODEC.decodeJson(json, new ExtraInfo());
            } catch (IOException e) {
                LOGGER.atWarning().log("[MGHG|GROWTH_MOD] Failed to read Size.json from build/resources: %s", e.getMessage());
            }
        }

        try (InputStream stream = MghgCropGrowthModifierAsset.class.getClassLoader().getResourceAsStream(RESOURCE_PATH)) {
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
        } catch (IOException e) {
            LOGGER.atWarning().log("[MGHG|GROWTH_MOD] Failed to read Size.json from classpath: %s", e.getMessage());
        }

        return null;
    }

    public static @Nullable MghgCropGrowthModifierAsset getLastLoaded() {
        return LAST_LOADED;
    }

    public int getMutationRollCooldownSeconds() {
        return mutationRollCooldownSeconds;
    }

    public double getMutationChanceRain() {
        return mutationChanceRain;
    }

    public double getMutationChanceSnow() {
        return mutationChanceSnow;
    }

    public double getMutationChanceFrozen() {
        return mutationChanceFrozen;
    }

    public @Nullable IntSet getRainWeatherIds() {
        return rainWeatherIds;
    }

    public @Nullable IntSet getSnowWeatherIds() {
        return snowWeatherIds;
    }

    public @Nullable IntSet getFrozenWeatherIds() {
        return frozenWeatherIds;
    }

    public int getSizeMin() {
        return sizeMin;
    }

    public int getSizeMax() {
        return sizeMax;
    }

    public double getMinGrowthMultiplierAtMaxSize() {
        return minGrowthMultiplierAtMaxSize;
    }

    public double getSizeMultiplierFor(int size) {
        return computeSizeMultiplier(size);
    }

    public float getInitialRarityGoldChance() {
        return initialRarityGoldChance;
    }

    public float getInitialRarityRainbowChance() {
        return initialRarityRainbowChance;
    }

    public @Nullable String resolveDropListId(@Nullable String dropListId) {
        if (dropListId == null) {
            return null;
        }
        if (dropListOverrideMap == null) {
            return dropListId;
        }
        return dropListOverrideMap.getOrDefault(dropListId, dropListId);
    }

    public @Nullable String[] getExtraDropListsAll() {
        return extraDropListsAll;
    }

    public @Nullable String[] getExtraDropListsGold() {
        return extraDropListsGold;
    }

    public @Nullable String[] getExtraDropListsRainbow() {
        return extraDropListsRainbow;
    }

    // ---------- Size slowdown ----------
    private double computeSizeMultiplier(int size) {
        if (size <= 0) return 1.0;

        int min = Math.min(sizeMin, sizeMax);
        int max = Math.max(sizeMin, sizeMax);
        if (max == min) return 1.0;

        int clamped = Math.max(min, Math.min(max, size));
        double t = (double) (clamped - min) / (double) (max - min);

        double minMul = minGrowthMultiplierAtMaxSize;
        if (minMul <= 0.0) minMul = 0.01;
        else if (minMul > 1.0) minMul = 1.0;

        return (1.0 * (1.0 - t)) + (minMul * t);
    }

}
