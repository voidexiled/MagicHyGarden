package com.voidexiled.magichygarden.features.farming.modifiers;

import com.hypixel.hytale.builtin.adventure.farming.states.FarmingBlock;
import com.hypixel.hytale.builtin.weather.resources.WeatherResource;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.farming.FarmingData;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.farming.FarmingStageData;
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
import com.voidexiled.magichygarden.features.farming.visuals.MghgCropVisualStateResolver;

import javax.annotation.Nullable;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * GrowthModifier para MagicHyGarden:
 * - Slowdown por size (50–100 por default).
 * - Mutación climática progresiva (NONE -> RAIN/SNOW -> FROZEN) con cooldown y allowlist de Weather IDs.
 * - Sincroniza FarmingBlock.currentStageSet con StageSets mghg_* y fuerza refresh visual del stage actual.
 */
public class MghgCropGrowthModifierAsset extends GrowthModifierAsset {

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

                    .afterDecode(MghgCropGrowthModifierAsset::buildWeatherCaches)
                    .build();

    // ---------- Config fields ----------
    protected int sizeMin = 50;
    protected int sizeMax = 100;

    /** Multiplier mínimo cuando size == sizeMax (clamp 0.01..1.0). */
    protected double minGrowthMultiplierAtMaxSize = 0.65;

    protected int mutationRollCooldownSeconds = 300;
    protected double mutationChanceRain = 0.12;
    protected double mutationChanceSnow = 0.12;
    protected double mutationChanceFrozen = 0.18;

    @Nullable protected String[] rainWeathers;
    @Nullable protected String[] snowWeathers;
    @Nullable protected String[] frozenWeathers;

    @Nullable protected Set<Integer> rainWeatherIds;
    @Nullable protected Set<Integer> snowWeatherIds;
    @Nullable protected Set<Integer> frozenWeatherIds;

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

        // 1) mutación climática progresiva
        dirty |= maybeRollClimateMutation(commandBuffer, chunkColRef, blockChunk, data, x, y, z);

        // 2) stage-set sync + refresh visual
        dirty |= syncStageSetAndRefresh(commandBuffer, sectionRef, blockRef, chunkColRef, blockChunk, data, x, y, z);

        // 3) slowdown por size
        double sizeMultiplier = computeSizeMultiplier(data.getSize());

        if (dirty) {
            if (blockComponentChunk != null) blockComponentChunk.markNeedsSaving();
            if (blockChunk != null) blockChunk.markNeedsSaving();
        }

        return base * sizeMultiplier;
    }

    // ---------- Decode helpers ----------
    private static void buildWeatherCaches(MghgCropGrowthModifierAsset a) {
        a.rainWeatherIds = toWeatherIds(a.rainWeathers);
        a.snowWeatherIds = toWeatherIds(a.snowWeathers);
        a.frozenWeatherIds = toWeatherIds(a.frozenWeathers);
    }

    @Nullable
    private static Set<Integer> toWeatherIds(@Nullable String[] ids) {
        if (ids == null || ids.length == 0) return null;

        HashSet<Integer> set = new HashSet<>(ids.length * 2);
        for (String id : ids) {
            int idx = Weather.getAssetMap().getIndex(id);
            if (idx != Weather.UNKNOWN_ID) set.add(idx);
        }
        return set.isEmpty() ? null : set;
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

        int weatherMask = getWeatherMask(store, blockChunk, x, y, z);
        boolean raining = (weatherMask & 1) != 0;
        boolean snowing = (weatherMask & 2) != 0;

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
        ClimateMutation after = computeNextClimate(before, raining, snowing);

        if (after != before) {
            data.setClimate(after);
        }

        // dirty porque al menos lastMutationRoll cambió
        return true;
    }

    /**
     * Retorna bitmask:
     *  - bit 0 (1): raining
     *  - bit 1 (2): snowing
     *
     * frozenWeathers se interpreta como "raining+snowing" simultáneo.
     */
    private int getWeatherMask(Store<EntityStore> store, BlockChunk blockChunk, int x, int y, int z) {
        WeatherResource weatherResource = store.getResource(WeatherResource.getResourceType());
        int environment = blockChunk.getEnvironment(x, y, z);

        int forced = weatherResource.getForcedWeatherIndex();
        int weatherId = forced != Weather.UNKNOWN_ID ? forced : weatherResource.getWeatherIndexForEnvironment(environment);
        if (weatherId == Weather.UNKNOWN_ID) {
            return 0;
        }

        // sky-check (idéntico al que ya tenías)
        int cropId = blockChunk.getBlock(x, y, z);
        for (int searchY = y + 1; searchY < 320; searchY++) {
            int block = blockChunk.getBlock(x, searchY, z);
            if (block != 0 && block != cropId) {
                return 0;
            }
        }

        boolean isRain = rainWeatherIds != null && rainWeatherIds.contains(weatherId);
        boolean isSnow = snowWeatherIds != null && snowWeatherIds.contains(weatherId);
        boolean isFrozen = frozenWeatherIds != null && frozenWeatherIds.contains(weatherId);

        boolean raining = isRain || isFrozen;
        boolean snowing = isSnow || isFrozen;

        int mask = 0;
        if (raining) mask |= 1;
        if (snowing) mask |= 2;
        return mask;
    }

    private ClimateMutation computeNextClimate(ClimateMutation current, boolean raining, boolean snowing) {
        if (current == null) current = ClimateMutation.NONE;
        if (current == ClimateMutation.FROZEN) return ClimateMutation.FROZEN;

        ThreadLocalRandom rnd = ThreadLocalRandom.current();

        // Upgrade path: RAIN/SNOW -> FROZEN cuando llega el complemento
        if (current == ClimateMutation.RAIN) {
            if (snowing && mutationChanceFrozen > 0.0 && rnd.nextDouble() <= mutationChanceFrozen) {
                return ClimateMutation.FROZEN;
            }
            return ClimateMutation.RAIN;
        }

        if (current == ClimateMutation.SNOW) {
            if (raining && mutationChanceFrozen > 0.0 && rnd.nextDouble() <= mutationChanceFrozen) {
                return ClimateMutation.FROZEN;
            }
            return ClimateMutation.SNOW;
        }

        // current == NONE: adquirir RAIN o SNOW según clima (sin upgrade a FROZEN en el mismo tick)
        if (current == ClimateMutation.NONE) {
            if (raining && !snowing) {
                if (mutationChanceRain > 0.0 && rnd.nextDouble() <= mutationChanceRain) return ClimateMutation.RAIN;
                return ClimateMutation.NONE;
            }

            if (snowing && !raining) {
                if (mutationChanceSnow > 0.0 && rnd.nextDouble() <= mutationChanceSnow) return ClimateMutation.SNOW;
                return ClimateMutation.NONE;
            }

            if (raining && snowing) {
                // intentar ambos, orden aleatorio
                boolean firstRain = rnd.nextBoolean();
                if (firstRain) {
                    if (mutationChanceRain > 0.0 && rnd.nextDouble() <= mutationChanceRain) return ClimateMutation.RAIN;
                    if (mutationChanceSnow > 0.0 && rnd.nextDouble() <= mutationChanceSnow) return ClimateMutation.SNOW;
                } else {
                    if (mutationChanceSnow > 0.0 && rnd.nextDouble() <= mutationChanceSnow) return ClimateMutation.SNOW;
                    if (mutationChanceRain > 0.0 && rnd.nextDouble() <= mutationChanceRain) return ClimateMutation.RAIN;
                }
                return ClimateMutation.NONE;
            }
        }

        return current;
    }

    // ---------- StageSet sync + visual refresh ----------
    private boolean syncStageSetAndRefresh(
            CommandBuffer<ChunkStore> commandBuffer,
            Ref<ChunkStore> sectionRef,
            Ref<ChunkStore> blockRef,
            @Nullable Ref<ChunkStore> chunkColRef,
            @Nullable BlockChunk blockChunk,
            MghgCropData data,
            int x, int y, int z
    ) {
        FarmingBlock farmingBlock = commandBuffer.getComponent(blockRef, FarmingBlock.getComponentType());
        if (farmingBlock == null) return false;
        if (chunkColRef == null || blockChunk == null) return false;

        int blockId = blockChunk.getBlock(x, y, z);
        if (blockId == 0) return false;

        BlockType blockType = BlockType.getAssetMap().getAsset(blockId);
        if (blockType == null) return false;

        FarmingData farming = blockType.getFarming();
        if (farming == null || farming.getStages() == null) return false;

        String desiredStageSet = MghgCropVisualStateResolver.resolveBlockStageSet(data);
        if (desiredStageSet == null) return false;

        FarmingStageData[] stages = farming.getStages().get(desiredStageSet);
        if (stages == null || stages.length == 0) return false;

        String currentStageSet = farmingBlock.getCurrentStageSet();
        if (desiredStageSet.equals(currentStageSet)) return false;

        farmingBlock.setCurrentStageSet(desiredStageSet);

        int stageIndex = (int) farmingBlock.getGrowthProgress();
        if (stageIndex < 0) stageIndex = 0;
        if (stageIndex >= stages.length) stageIndex = stages.length - 1;

        stages[stageIndex].apply(commandBuffer, sectionRef, blockRef, x, y, z, null);
        return true;
    }
}
