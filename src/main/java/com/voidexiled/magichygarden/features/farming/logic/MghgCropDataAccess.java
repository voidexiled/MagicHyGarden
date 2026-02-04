package com.voidexiled.magichygarden.features.farming.logic;

import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.voidexiled.magichygarden.features.farming.components.MghgCropData;

import javax.annotation.Nullable;

public final class MghgCropDataAccess {
    private MghgCropDataAccess() {
    }

    public record CropDataHandle(MghgCropData data, boolean fromStateHolder, @Nullable Holder<ChunkStore> stateHolder) {
    }

    public static @Nullable MghgCropData tryGetCropData(@Nullable World world, @Nullable Vector3i blockPosition) {
        if (world == null || blockPosition == null) return null;

        ChunkStore chunkStore = world.getChunkStore();
        Store<ChunkStore> cs = chunkStore.getStore();

        long chunkIndex = ChunkUtil.indexChunkFromBlock(blockPosition.x, blockPosition.z);
        Ref<ChunkStore> chunkRef = chunkStore.getChunkReference(chunkIndex);
        if (chunkRef == null || !chunkRef.isValid()) {
            return null;
        }

        BlockComponentChunk blockComponentChunk = cs.getComponent(chunkRef, BlockComponentChunk.getComponentType());
        if (blockComponentChunk == null) {
            return null;
        }

        int blockIndexColumn = ChunkUtil.indexBlockInColumn(blockPosition.x, blockPosition.y, blockPosition.z);

        // 1) Normal: entity reference
        Ref<ChunkStore> blockRef = blockComponentChunk.getEntityReference(blockIndexColumn);
        if (blockRef != null && blockRef.isValid()) {
            MghgCropData data = cs.getComponent(blockRef, MghgCropData.getComponentType());
            if (data != null) {
                return data;
            }
        }

        // 2) Persistido: entity holder (aun no rehidratado)
        Holder<ChunkStore> holder = blockComponentChunk.getEntityHolder(blockIndexColumn);
        if (holder != null) {
            MghgCropData data = holder.getComponent(MghgCropData.getComponentType());
            if (data != null) {
                return data;
            }
        }

        // 3) Holder en WorldChunk (set via worldChunk.setState)
        WorldChunk worldChunk = cs.getComponent(chunkRef, WorldChunk.getComponentType());
        if (worldChunk != null) {
            Holder<ChunkStore> stateHolder = worldChunk.getBlockComponentHolder(
                    blockPosition.x, blockPosition.y, blockPosition.z
            );
            if (stateHolder != null) {
                return stateHolder.getComponent(MghgCropData.getComponentType());
            }
        }

        return null;
    }

    public static @Nullable CropDataHandle getOrCreateCropData(
            Store<ChunkStore> cs,
            WorldChunk worldChunk,
            @Nullable BlockComponentChunk blockComponentChunk,
            int x, int y, int z
    ) {
        if (cs == null || worldChunk == null) return null;

        int blockIndex = ChunkUtil.indexBlockInColumn(x, y, z);
        Ref<ChunkStore> blockRef = blockComponentChunk != null ? blockComponentChunk.getEntityReference(blockIndex) : null;
        Holder<ChunkStore> blockHolder = blockComponentChunk != null ? blockComponentChunk.getEntityHolder(blockIndex) : null;
        Holder<ChunkStore> stateHolder = worldChunk.getBlockComponentHolder(x, y, z);

        MghgCropData data = null;
        boolean fromStateHolder = false;

        if (blockRef != null && blockRef.isValid()) {
            data = cs.ensureAndGetComponent(blockRef, MghgCropData.getComponentType());
        } else if (blockHolder != null) {
            data = blockHolder.getComponent(MghgCropData.getComponentType());
            if (data == null) {
                data = new MghgCropData();
                blockHolder.putComponent(MghgCropData.getComponentType(), data);
            }
        } else if (stateHolder != null) {
            data = stateHolder.getComponent(MghgCropData.getComponentType());
            if (data == null) {
                data = new MghgCropData();
                stateHolder.putComponent(MghgCropData.getComponentType(), data);
            }
            fromStateHolder = true;
        }

        if (data == null) {
            return null;
        }

        return new CropDataHandle(data, fromStateHolder, stateHolder);
    }
}
