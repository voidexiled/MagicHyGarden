package com.voidexiled.magichygarden.features.farming.logic;

import com.hypixel.hytale.builtin.adventure.farming.states.FarmingBlock;
import com.hypixel.hytale.math.util.HashUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.Rangef;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.farming.FarmingData;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.farming.FarmingStageData;
import com.voidexiled.magichygarden.features.farming.components.MghgCropData;
import com.voidexiled.magichygarden.features.farming.modifiers.MghgCropGrowthModifierAsset;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Instant;

public final class MghgGrowthTimeUtil {
    private MghgGrowthTimeUtil() {}

    public static long computeRemainingSecondsFromPlantTime(
            @Nullable Instant now,
            @Nullable BlockType blockType,
            @Nullable Vector3i pos,
            @Nullable FarmingBlock farmingBlock,
            @Nullable MghgCropData data,
            double secondsPerTick
    ) {
        if (now == null || blockType == null || pos == null || data == null) {
            return -1L;
        }
        Instant plantTime = data.getPlantTime();
        if (plantTime == null) {
            return -1L;
        }
        long total = computeTotalGrowthSeconds(blockType, pos, farmingBlock, data);
        if (total <= 0L) {
            return 0L;
        }
        long elapsed = Math.max(0L, now.getEpochSecond() - plantTime.getEpochSecond());
        long remaining = Math.max(0L, total - elapsed);
        return scaleGameSecondsToReal(remaining, secondsPerTick);
    }

    public static long computeRemainingSecondsFromProgress(
            @Nullable BlockType blockType,
            @Nullable Vector3i pos,
            @Nullable FarmingBlock farmingBlock,
            @Nullable MghgCropData data,
            double secondsPerTick
    ) {
        if (blockType == null || pos == null || farmingBlock == null) {
            return -1L;
        }
        long total = computeTotalGrowthSeconds(blockType, pos, farmingBlock, data);
        if (total <= 0L) {
            return 0L;
        }
        long elapsed = computeElapsedSecondsFromProgress(blockType, pos, farmingBlock, data);
        long remaining = Math.max(0L, total - elapsed);
        return scaleGameSecondsToReal(remaining, secondsPerTick);
    }

    public static long computeTotalGrowthSeconds(
            @Nullable BlockType blockType,
            @Nullable Vector3i pos,
            @Nullable FarmingBlock farmingBlock,
            @Nullable MghgCropData data
    ) {
        if (blockType == null || pos == null) {
            return -1L;
        }
        FarmingStageData[] stages = resolveStages(blockType, farmingBlock);
        if (stages == null || stages.length == 0) {
            return -1L;
        }
        int growthStages = Math.max(0, stages.length - 1);
        if (growthStages == 0) {
            return 0L;
        }
        double growthMultiplier = resolveSizeMultiplier(data);
        if (growthMultiplier <= 0.0) {
            growthMultiplier = 1.0;
        }

        long baseGen = resolveBaseGeneration(farmingBlock);
        long total = 0L;
        for (int i = 0; i < growthStages; i++) {
            Rangef range = stages[i] != null ? stages[i].getDuration() : null;
            if (range == null) {
                continue;
            }
            double baseDuration = resolveBaseDurationSeconds(range, baseGen + i, pos);
            long stageSeconds = Math.round(baseDuration / growthMultiplier);
            if (stageSeconds > 0L) {
                total += stageSeconds;
            }
        }
        return total;
    }

    public static long computeElapsedSecondsFromProgress(
            @Nullable BlockType blockType,
            @Nullable Vector3i pos,
            @Nullable FarmingBlock farmingBlock,
            @Nullable MghgCropData data
    ) {
        if (blockType == null || pos == null || farmingBlock == null) {
            return 0L;
        }
        FarmingStageData[] stages = resolveStages(blockType, farmingBlock);
        if (stages == null || stages.length == 0) {
            return 0L;
        }
        int growthStages = Math.max(0, stages.length - 1);
        if (growthStages == 0) {
            return 0L;
        }
        double growthMultiplier = resolveSizeMultiplier(data);
        if (growthMultiplier <= 0.0) {
            growthMultiplier = 1.0;
        }

        float progress = farmingBlock.getGrowthProgress();
        if (progress < 0.0f) {
            progress = 0.0f;
        }
        int currentStage = (int) progress;
        if (currentStage < 0) currentStage = 0;
        if (currentStage > stages.length) currentStage = stages.length;
        if (currentStage >= growthStages) {
            return computeTotalGrowthSeconds(blockType, pos, farmingBlock, data);
        }
        double stageFraction = Math.max(0.0, Math.min(1.0, progress - currentStage));

        long baseGen = resolveBaseGeneration(farmingBlock);
        double elapsed = 0.0;

        for (int i = 0; i < Math.min(currentStage, growthStages); i++) {
            Rangef range = stages[i] != null ? stages[i].getDuration() : null;
            if (range == null) {
                continue;
            }
            double baseDuration = resolveBaseDurationSeconds(range, baseGen + i, pos);
            elapsed += (baseDuration / growthMultiplier);
        }

        if (currentStage < stages.length) {
            Rangef range = stages[currentStage] != null ? stages[currentStage].getDuration() : null;
            if (range != null) {
                double baseDuration = resolveBaseDurationSeconds(range, baseGen + currentStage, pos);
                elapsed += (baseDuration * stageFraction / growthMultiplier);
            }
        }

        return Math.max(0L, Math.round(elapsed));
    }

    private static @Nullable FarmingStageData[] resolveStages(
            @Nullable BlockType blockType,
            @Nullable FarmingBlock farmingBlock
    ) {
        if (blockType == null) {
            return null;
        }
        FarmingData farming = blockType.getFarming();
        if (farming == null || farming.getStages() == null) {
            return null;
        }
        String stageSet = null;
        if (farmingBlock != null) {
            stageSet = farmingBlock.getCurrentStageSet();
        }
        if (stageSet == null || stageSet.isBlank()) {
            stageSet = farming.getStartingStageSet();
        }
        if (stageSet == null || stageSet.isBlank()) {
            stageSet = FarmingBlock.DEFAULT_STAGE_SET;
        }
        return farming.getStages().get(stageSet);
    }

    private static long resolveBaseGeneration(@Nullable FarmingBlock farmingBlock) {
        if (farmingBlock == null) {
            return 0L;
        }
        int currentStage = (int) farmingBlock.getGrowthProgress();
        long base = (long) farmingBlock.getGeneration() - (long) currentStage;
        return Math.max(0L, base);
    }

    private static double resolveBaseDurationSeconds(@Nonnull Rangef range, long seed, @Nonnull Vector3i pos) {
        double rand = HashUtil.random(seed, pos.x, pos.y, pos.z);
        return range.min + (range.max - range.min) * rand;
    }

    private static double resolveSizeMultiplier(@Nullable MghgCropData data) {
        if (data == null) {
            return 1.0;
        }
        MghgCropGrowthModifierAsset cfg = MghgCropGrowthModifierAsset.getLastLoaded();
        if (cfg == null) {
            return 1.0;
        }
        return cfg.getSizeMultiplierFor(data.getSize());
    }

    private static long scaleGameSecondsToReal(long gameSeconds, double secondsPerTick) {
        if (secondsPerTick > 0.0) {
            return Math.max(0L, Math.round(gameSeconds / secondsPerTick));
        }
        return Math.max(0L, gameSeconds);
    }
}
