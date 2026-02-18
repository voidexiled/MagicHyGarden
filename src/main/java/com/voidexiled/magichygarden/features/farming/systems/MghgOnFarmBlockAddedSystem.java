package com.voidexiled.magichygarden.features.farming.systems;

import com.hypixel.hytale.builtin.adventure.farming.states.FarmingBlock;
import com.hypixel.hytale.builtin.asseteditor.util.AssetStoreUtil;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.ItemUtility;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.farming.FarmingData;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.util.AssetUtil;
import com.voidexiled.magichygarden.features.farming.components.MghgCropData;
import com.voidexiled.magichygarden.features.farming.events.MghgFarmEventScheduler;
import com.voidexiled.magichygarden.features.farming.logic.MghgCropDataSeeder;
import com.voidexiled.magichygarden.features.farming.logic.MghgGrowthTimeUtil;
import com.voidexiled.magichygarden.features.farming.registry.MghgCropRegistry;
import org.jspecify.annotations.NonNull;

import javax.annotation.Nonnull;
import java.time.Instant;

public class MghgOnFarmBlockAddedSystem extends RefSystem<ChunkStore> {

    private final Query<ChunkStore> query;

    private final ComponentType<ChunkStore, FarmingBlock> farmingBlockType;
    private final ComponentType<ChunkStore, MghgCropData> cropDataType;

    public MghgOnFarmBlockAddedSystem(
            ComponentType<ChunkStore, FarmingBlock> farmingBlockType,
            ComponentType<ChunkStore, MghgCropData> cropDataType
    ) {
        this.farmingBlockType = farmingBlockType;
        this.cropDataType = cropDataType;

        this.query = Query.and(
                BlockModule.BlockStateInfo.getComponentType(),
                this.farmingBlockType
        );
    }

    @Override
    public Query<ChunkStore> getQuery() {
        return query;
    }

    @Override
    public void onEntityAdded(
            @Nonnull Ref<ChunkStore> ref,
            @Nonnull AddReason reason,
            @Nonnull Store<ChunkStore> store,
            @Nonnull CommandBuffer<ChunkStore> commandBuffer
    ) {
        if (commandBuffer.getExternalData() == null || commandBuffer.getExternalData().getWorld() == null) {
            return;
        }
        var world = commandBuffer.getExternalData().getWorld();
        if (!MghgFarmEventScheduler.isFarmWorld(world)) {
            return;
        }

        // Se ejecuta cuando la entidad FarmingBlock aparece (crop nuevo o rehidratado).
        FarmingBlock farmingBlock = commandBuffer.getComponent(ref, farmingBlockType);
        if (farmingBlock == null) return;

        BlockModule.BlockStateInfo info = commandBuffer.getComponent(ref, BlockModule.BlockStateInfo.getComponentType());
        if (info == null || info.getChunkRef() == null) return;

        BlockChunk blockChunk = commandBuffer.getComponent(info.getChunkRef(), BlockChunk.getComponentType());
        if (blockChunk == null) return;

        int lx = ChunkUtil.xFromBlockInColumn(info.getIndex());
        int ly = ChunkUtil.yFromBlockInColumn(info.getIndex());
        int lz = ChunkUtil.zFromBlockInColumn(info.getIndex());

        BlockType blockType = BlockType.getAssetMap().getAsset(blockChunk.getBlock(lx, ly, lz));
        if (blockType == null || blockType.getFarming() == null) return;
        if (!MghgCropRegistry.isMghgCropBlock(blockType)) return;

        int worldX = ChunkUtil.worldCoordFromLocalCoord(blockChunk.getX(), lx);
        int worldY = ly;
        int worldZ = ChunkUtil.worldCoordFromLocalCoord(blockChunk.getZ(), lz);
        Vector3i worldPos = new Vector3i(worldX, worldY, worldZ);

        Instant now = Instant.now();
        if (commandBuffer.getExternalData() != null && commandBuffer.getExternalData().getWorld() != null) {
            //var world = commandBuffer.getExternalData().getWorld();
            var timeRes = world.getEntityStore().getStore().getResource(WorldTimeResource.getResourceType());
            if (timeRes != null) {
                now = timeRes.getGameTime();
            }
        }

        // ✅ asegura tu data SOLO una vez
        MghgCropData data = commandBuffer.ensureAndGetComponent(ref, cropDataType);
        if (data.getSize() != 0) {
            if (data.getPlantTime() == null && now != null) {
                long elapsed = MghgGrowthTimeUtil.computeElapsedSecondsFromProgress(blockType, worldPos, farmingBlock, data);
                data.setPlantTime(elapsed > 0 ? now.minusSeconds(elapsed) : now);
            }
            return;
        }

        // Seed unificado (size/rarity/climate) desde GrowthModifier config.
        double baseWeight = MghgCropRegistry.getBaseWeightGrams(blockType);
        MghgCropDataSeeder.seedNew(data, baseWeight);
        if (now != null) {
            data.setPlantTime(now);
        }

    }

    @Override
    public void onEntityRemove(@NonNull Ref<ChunkStore> ref,
                               @NonNull RemoveReason removeReason,
                               @NonNull Store<ChunkStore> store,
                               @NonNull CommandBuffer<ChunkStore> commandBuffer) {
        if (commandBuffer.getExternalData() == null || commandBuffer.getExternalData().getWorld() == null) {
            return;
        }
        var world = commandBuffer.getExternalData().getWorld();
        if (!MghgFarmEventScheduler.isFarmWorld(world)) {
            return;
        }
        // Solo nos interesa cuando el farming system REMUEVE la entidad (madurez / stop)
        if (removeReason != RemoveReason.REMOVE) {
            return;
        }

        // Si no hay MGHG data, no hacemos nada.
        MghgCropData cropData = commandBuffer.getComponent(ref, cropDataType);
        if (cropData == null) {
            return;
        }

        // Necesitamos saber a qué bloque pertenece esta entidad.
        BlockModule.BlockStateInfo info =
                commandBuffer.getComponent(ref, BlockModule.BlockStateInfo.getComponentType());
        if (info == null) {
            return;
        }

        Ref<ChunkStore> chunkRef = info.getChunkRef();
        if (chunkRef == null || !chunkRef.isValid()) {
            return;
        }

        BlockChunk blockChunk = store.getComponent(chunkRef, BlockChunk.getComponentType());
        if (blockChunk == null) {
            return;
        }

        int idx = info.getIndex();
        int lx = ChunkUtil.xFromBlockInColumn(idx);
        int ly = ChunkUtil.yFromBlockInColumn(idx);
        int lz = ChunkUtil.zFromBlockInColumn(idx);

        int blockId = blockChunk.getBlock(lx, ly, lz);
        BlockType blockType = BlockType.getAssetMap().getAsset(blockId);
        if (blockType == null) {
            return;
        }
        if (!MghgCropRegistry.isMghgCropBlock(blockType)) {
            return;
        }

        // Seguridad anti-leaks:
        // si el bloque ya no es farming (por ejemplo lo rompieron y ahora es aire), NO recreamos nada.
        FarmingData farming = blockType.getFarming();
        if (farming == null) {
            return;
        }

        // Creamos una entidad "persistente" para el bloque (solo BlockStateInfo + MGHG_CropData).
        // NO agregamos FarmingBlock, así el farming tick no la vuelve a borrar.
        Holder<ChunkStore> persistent = ChunkStore.REGISTRY.newHolder();

        persistent.putComponent(
                BlockModule.BlockStateInfo.getComponentType(),
                new BlockModule.BlockStateInfo(idx, chunkRef)
        );

        // Copia profunda (no reusar la misma instancia)
        MghgCropData copy = new MghgCropData();
        copy.setSize(cropData.getSize());
        copy.setClimate(cropData.getClimate());
        copy.setLunar(cropData.getLunar());
        copy.setRarity(cropData.getRarity());
        copy.setWeightGrams(cropData.getWeightGrams());
        copy.setPlantTime(cropData.getPlantTime());
        copy.setLastRegularRoll(cropData.getLastRegularRoll());
        copy.setLastLunarRoll(cropData.getLastLunarRoll());
        copy.setLastSpecialRoll(cropData.getLastSpecialRoll());
        copy.setLastMutationRoll(cropData.getLastMutationRoll());

        persistent.addComponent(cropDataType, copy);


        //persistent.replaceComponent(ref, BlockComponentChunk.getComponentType(), new BlockComponentChunk());
        commandBuffer.addEntity(persistent, AddReason.SPAWN);
    }

}
