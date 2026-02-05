package com.voidexiled.magichygarden.features.farming.logic;

import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.voidexiled.magichygarden.features.farming.components.MghgCropData;
import com.voidexiled.magichygarden.features.farming.modifiers.MghgCropGrowthModifierAsset;
import com.voidexiled.magichygarden.features.farming.registry.MghgCropDefinition;
import com.voidexiled.magichygarden.features.farming.registry.MghgCropRegistry;

import javax.annotation.Nullable;
import javax.annotation.Nonnull;

public final class MghgWeightUtil {
    private MghgWeightUtil() {}

    /**
     * Computes the weight at full maturity for the given size.
     * BaseWeightGrams is interpreted as the weight at SizeMin when fully mature.
     */
    public static double computeWeightAtMatureGrams(int size, int min, int max, double baseWeightGrams) {
        if (baseWeightGrams <= 0.0) {
            return 0.0;
        }
        if (max < min) {
            int tmp = min;
            min = max;
            max = tmp;
        }
        if (min <= 0) {
            return baseWeightGrams;
        }

        int clamped = size;
        if (clamped < min) clamped = min;
        if (max > 0 && clamped > max) clamped = max;

        double ratio = clamped / (double) min;
        if (ratio < 0.0) ratio = 0.0;
        return baseWeightGrams * ratio;
    }

    /**
     * Computes the current weight using a growth progress factor [0..1].
     */
    public static double computeWeightGrams(int size, int min, int max, double baseWeightGrams, double progress) {
        double mature = computeWeightAtMatureGrams(size, min, max, baseWeightGrams);
        if (mature <= 0.0) {
            return 0.0;
        }
        double p = progress;
        if (p < 0.0) p = 0.0;
        if (p > 1.0) p = 1.0;
        return mature * p;
    }

    public static double computeWeightAtMatureGrams(@Nullable MghgCropDefinition def, int size) {
        if (def == null) {
            return 0.0;
        }
        MghgCropGrowthModifierAsset cfg = MghgCropGrowthModifierAsset.getLastLoaded();
        int min = cfg != null ? cfg.getSizeMin() : MghgCropGrowthModifierAsset.DEFAULT_SIZE_MIN;
        int max = cfg != null ? cfg.getSizeMax() : MghgCropGrowthModifierAsset.DEFAULT_SIZE_MAX;
        return computeWeightAtMatureGrams(size, min, max, def.getBaseWeightGrams());
    }

    public static double computeWeightGrams(@Nullable MghgCropDefinition def, int size, double progress) {
        if (def == null) {
            return 0.0;
        }
        MghgCropGrowthModifierAsset cfg = MghgCropGrowthModifierAsset.getLastLoaded();
        int min = cfg != null ? cfg.getSizeMin() : MghgCropGrowthModifierAsset.DEFAULT_SIZE_MIN;
        int max = cfg != null ? cfg.getSizeMax() : MghgCropGrowthModifierAsset.DEFAULT_SIZE_MAX;
        return computeWeightGrams(size, min, max, def.getBaseWeightGrams(), progress);
    }

    public static double computeWeightAtMatureGrams(@Nullable BlockType blockType, int size) {
        if (blockType == null) {
            return 0.0;
        }
        return computeWeightAtMatureGrams(MghgCropRegistry.getDefinition(blockType), size);
    }

    public static double computeWeightGrams(@Nullable BlockType blockType, int size, double progress) {
        if (blockType == null) {
            return 0.0;
        }
        return computeWeightGrams(MghgCropRegistry.getDefinition(blockType), size, progress);
    }

    public static double computeWeightAtMatureGramsByItemId(@Nullable String itemId, int size) {
        if (itemId == null || itemId.isBlank()) {
            return 0.0;
        }
        return computeWeightAtMatureGrams(MghgCropRegistry.getDefinitionByItemId(itemId), size);
    }

    public static void applyWeight(@Nullable MghgCropData data, @Nullable MghgCropDefinition def) {
        if (data == null) {
            return;
        }
        double weight = computeWeightAtMatureGrams(def, data.getSize());
        data.setWeightGrams(weight);
    }

    public static void applyWeight(@Nullable MghgCropData data, @Nullable BlockType blockType) {
        if (data == null) {
            return;
        }
        double weight = computeWeightAtMatureGrams(blockType, data.getSize());
        data.setWeightGrams(weight);
    }

    public static void applyWeightFromBlockRef(
            @Nonnull Store<ChunkStore> store,
            @Nonnull Ref<ChunkStore> blockRef,
            @Nonnull MghgCropData data
    ) {
        BlockType blockType = resolveBlockType(store, blockRef);
        if (blockType == null) {
            return;
        }
        applyWeight(data, blockType);
    }

    @Nullable
    public static BlockType resolveBlockType(@Nonnull Store<ChunkStore> store, @Nonnull Ref<ChunkStore> blockRef) {
        BlockModule.BlockStateInfo info = store.getComponent(blockRef, BlockModule.BlockStateInfo.getComponentType());
        if (info == null || info.getChunkRef() == null) {
            return null;
        }
        BlockChunk blockChunk = store.getComponent(info.getChunkRef(), BlockChunk.getComponentType());
        if (blockChunk == null) {
            return null;
        }
        int lx = ChunkUtil.xFromBlockInColumn(info.getIndex());
        int ly = ChunkUtil.yFromBlockInColumn(info.getIndex());
        int lz = ChunkUtil.zFromBlockInColumn(info.getIndex());
        int blockId = blockChunk.getBlock(lx, ly, lz);
        return BlockType.getAssetMap().getAsset(blockId);
    }
}
