package com.voidexiled.magichygarden.features.farming.modifiers;

import com.hypixel.hytale.builtin.adventure.farming.states.FarmingBlock;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.farming.GrowthModifierAsset;
import com.hypixel.hytale.server.core.asset.type.weather.config.Weather;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.voidexiled.magichygarden.features.farming.components.MghgCropData;
import com.voidexiled.magichygarden.features.farming.state.ClimateMutation;
import com.voidexiled.magichygarden.features.farming.state.MghgClimateMutationLogic;
import com.voidexiled.magichygarden.features.farming.state.MghgWeatherUtil;
import com.voidexiled.magichygarden.features.farming.visuals.MghgCropStageSync;
import it.unimi.dsi.fastutil.ints.IntSet;

import javax.annotation.Nullable;
import java.time.Duration;
import java.time.Instant;
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
    private static volatile MghgCropGrowthModifierAsset LAST_LOADED;

    public static final BuilderCodec<MghgCropGrowthModifierAsset> CODEC =
            BuilderCodec.builder(MghgCropGrowthModifierAsset.class, MghgCropGrowthModifierAsset::new, ABSTRACT_CODEC)
                    .documentation("MagicHyGarden: size slowdown + climate mutation rolls + stageSet sync.")

                    // --- size tuning ---
                    .<Integer>append(new KeyedCodec<>("SizeMin", Codec.INTEGER),
                            (a, v) -> a.sizeMin = v,
                            a -> a.sizeMin)
                    .add()
                    .<Integer>append(new KeyedCodec<>("SizeMax", Codec.INTEGER),
                            (a, v) -> a.sizeMax = v,
                            a -> a.sizeMax)
                    .add()
                    .<Double>append(new KeyedCodec<>("MinGrowthMultiplierAtMaxSize", Codec.DOUBLE),
                            (a, v) -> a.minGrowthMultiplierAtMaxSize = v,
                            a -> a.minGrowthMultiplierAtMaxSize)
                    .add()

                    // --- initial seed tuning ---
                    .<Float>append(new KeyedCodec<>("InitialRarityGoldChance", Codec.FLOAT),
                            (a, v) -> a.initialRarityGoldChance = v,
                            a -> a.initialRarityGoldChance)
                    .add()
                    .<Float>append(new KeyedCodec<>("InitialRarityRainbowChance", Codec.FLOAT),
                            (a, v) -> a.initialRarityRainbowChance = v,
                            a -> a.initialRarityRainbowChance)
                    .add()

                    // --- mutation tuning ---
                    .<Integer>append(new KeyedCodec<>("MutationRollCooldownSeconds", Codec.INTEGER),
                            (a, v) -> a.mutationRollCooldownSeconds = v,
                            a -> a.mutationRollCooldownSeconds)
                    .add()
                    .<Double>append(new KeyedCodec<>("MutationChanceRain", Codec.DOUBLE),
                            (a, v) -> a.mutationChanceRain = v,
                            a -> a.mutationChanceRain)
                    .add()
                    .<Double>append(new KeyedCodec<>("MutationChanceSnow", Codec.DOUBLE),
                            (a, v) -> a.mutationChanceSnow = v,
                            a -> a.mutationChanceSnow)
                    .add()
                    .<Double>append(new KeyedCodec<>("MutationChanceFrozen", Codec.DOUBLE),
                            (a, v) -> a.mutationChanceFrozen = v,
                            a -> a.mutationChanceFrozen)
                    .add()

                    // --- weather allowlists ---
                    .<String[]>append(new KeyedCodec<>("RainWeathers", Codec.STRING_ARRAY),
                            (a, v) -> a.rainWeathers = v,
                            a -> a.rainWeathers)
                    .addValidator(Weather.VALIDATOR_CACHE.getArrayValidator())
                    .add()
                    .<String[]>append(new KeyedCodec<>("SnowWeathers", Codec.STRING_ARRAY),
                            (a, v) -> a.snowWeathers = v,
                            a -> a.snowWeathers)
                    .addValidator(Weather.VALIDATOR_CACHE.getArrayValidator())
                    .add()
                    .<String[]>append(new KeyedCodec<>("FrozenWeathers", Codec.STRING_ARRAY),
                            (a, v) -> a.frozenWeathers = v,
                            a -> a.frozenWeathers)
                    .addValidator(Weather.VALIDATOR_CACHE.getArrayValidator())
                    .add()

                    // --- droplist overrides + extras ---
                    .<String[]>append(new KeyedCodec<>("DropListOverrideFrom", Codec.STRING_ARRAY),
                            (a, v) -> a.dropListOverrideFrom = v,
                            a -> a.dropListOverrideFrom)
                    .add()
                    .<String[]>append(new KeyedCodec<>("DropListOverrideTo", Codec.STRING_ARRAY),
                            (a, v) -> a.dropListOverrideTo = v,
                            a -> a.dropListOverrideTo)
                    .add()
                    .<String[]>append(new KeyedCodec<>("ExtraDropListsAll", Codec.STRING_ARRAY),
                            (a, v) -> a.extraDropListsAll = v,
                            a -> a.extraDropListsAll)
                    .add()
                    .<String[]>append(new KeyedCodec<>("ExtraDropListsGold", Codec.STRING_ARRAY),
                            (a, v) -> a.extraDropListsGold = v,
                            a -> a.extraDropListsGold)
                    .add()
                    .<String[]>append(new KeyedCodec<>("ExtraDropListsRainbow", Codec.STRING_ARRAY),
                            (a, v) -> a.extraDropListsRainbow = v,
                            a -> a.extraDropListsRainbow)
                    .add()

                    .afterDecode(MghgCropGrowthModifierAsset::buildCaches)
                    .build();

    // ---------- Config fields ----------
    protected int sizeMin = 50;
    protected int sizeMax = 100;

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

        MghgCropData data = commandBuffer.getComponent(blockRef, MghgCropData.getComponentType());
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
        a.rainWeatherIds = MghgWeatherUtil.toWeatherIdSet(a.rainWeathers);
        a.snowWeatherIds = MghgWeatherUtil.toWeatherIdSet(a.snowWeathers);
        a.frozenWeatherIds = MghgWeatherUtil.toWeatherIdSet(a.frozenWeathers);
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

    // ---------- Climate mutation (progresiva / compuesta) ----------
    private boolean maybeRollClimateMutation(
            CommandBuffer<ChunkStore> commandBuffer,
            @Nullable Ref<ChunkStore> chunkColRef,
            @Nullable BlockChunk blockChunk,
            MghgCropData data,
            int x, int y, int z
    ) {
        // Si no configuraste weathers, no hacemos nada
        if (rainWeatherIds == null && snowWeatherIds == null && frozenWeatherIds == null) {
            return false;
        }

        if (blockChunk == null || chunkColRef == null) {
            return false;
        }

        Store<EntityStore> store = commandBuffer.getExternalData().getWorld().getEntityStore().getStore();
        WorldTimeResource time = store.getResource(WorldTimeResource.getResourceType());
        Instant now = time.getGameTime();

        int weatherMask = MghgWeatherUtil.getWeatherMask(store, blockChunk, x, y, z,
                rainWeatherIds, snowWeatherIds, frozenWeatherIds);
        boolean raining = (weatherMask & MghgWeatherUtil.MASK_RAIN) != 0;
        boolean snowing = (weatherMask & MghgWeatherUtil.MASK_SNOW) != 0;

        // si no llueve ni nieva, no “gastamos” cooldown
        if (!raining && !snowing) {
            return false;
        }

        Instant last = data.getLastMutationRoll();
        if (last != null && mutationRollCooldownSeconds > 0) {
            long elapsed = Duration.between(last, now).getSeconds();
            if (elapsed < mutationRollCooldownSeconds) {
                return false;
            }
        }

        // marcamos intento
        data.setLastMutationRoll(now);

        ClimateMutation before = data.getClimate();
        ClimateMutation after = MghgClimateMutationLogic.computeNext(
                before,
                raining,
                snowing,
                mutationChanceRain,
                mutationChanceSnow,
                mutationChanceFrozen
        );

        if (after != before) {
            data.setClimate(after);
        }

        // dirty porque al menos lastMutationRoll cambió
        return true;
    }

}
