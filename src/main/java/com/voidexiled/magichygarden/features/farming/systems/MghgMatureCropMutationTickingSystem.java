package com.voidexiled.magichygarden.features.farming.systems;

import com.hypixel.hytale.builtin.adventure.farming.states.FarmingBlock;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.meta.BlockState;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.voidexiled.magichygarden.features.farming.components.MghgCropData;
import com.voidexiled.magichygarden.features.farming.modifiers.MghgCropGrowthModifierAsset;
import com.voidexiled.magichygarden.features.farming.registry.MghgCropRegistry;
import com.voidexiled.magichygarden.features.farming.state.MghgMutationEngine;
import com.voidexiled.magichygarden.features.farming.state.MghgWeatherResolver;
import com.voidexiled.magichygarden.features.farming.visuals.MghgCropStageSync;
import com.voidexiled.magichygarden.features.farming.visuals.MghgCropVisualStateResolver;
import it.unimi.dsi.fastutil.ints.IntSet;

import java.time.Duration;
import java.time.Instant;

public class MghgMatureCropMutationTickingSystem extends EntityTickingSystem<ChunkStore> {

        private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final Query<ChunkStore> query;

    private final ComponentType<ChunkStore, FarmingBlock> farmingBlockType;
    private final ComponentType<ChunkStore, MghgCropData> cropDataType;

    public MghgMatureCropMutationTickingSystem(
            ComponentType<ChunkStore, FarmingBlock> farmingBlockType,
            ComponentType<ChunkStore, MghgCropData> cropDataType
    ) {
        this.farmingBlockType = farmingBlockType;
        this.cropDataType = cropDataType;

        this.query = Query.and(
                BlockModule.BlockStateInfo.getComponentType(),
                this.cropDataType
        );
    }

    @Override
    public Query<ChunkStore> getQuery() {
        return query;
    }

    @Override
    public void tick(float dt, int index, ArchetypeChunk<ChunkStore> archetypeChunk, Store<ChunkStore> store, CommandBuffer<ChunkStore> commandBuffer) {
        Ref<ChunkStore> ref = archetypeChunk.getReferenceTo(index);

        MghgCropData data = archetypeChunk.getComponent(index, cropDataType);
        BlockModule.BlockStateInfo info = archetypeChunk.getComponent(index, BlockModule.BlockStateInfo.getComponentType());
        if (data == null || info == null) return;

        Ref<ChunkStore> chunkRef = info.getChunkRef();
        if (chunkRef == null || !chunkRef.isValid()) return;

        BlockChunk blockChunk = store.getComponent(chunkRef, BlockChunk.getComponentType());
        if (blockChunk == null) return;

        BlockComponentChunk blockComponentChunk = store.getComponent(chunkRef, BlockComponentChunk.getComponentType());

        int idx = info.getIndex();
        int x = com.hypixel.hytale.math.util.ChunkUtil.xFromBlockInColumn(idx);
        int y = com.hypixel.hytale.math.util.ChunkUtil.yFromBlockInColumn(idx);
        int z = com.hypixel.hytale.math.util.ChunkUtil.zFromBlockInColumn(idx);

        WorldChunk worldChunk = store.getComponent(chunkRef, WorldChunk.getComponentType());
        if (worldChunk == null) return;

        BlockType blockType = BlockType.getAssetMap().getAsset(blockChunk.getBlock(x, y, z));
        if (!MghgCropRegistry.isMghgCropBlock(blockType)) {
            return;
        }

        Store<EntityStore> entityStore = commandBuffer.getExternalData().getWorld().getEntityStore().getStore();
        Instant now = entityStore.getResource(WorldTimeResource.getResourceType()).getGameTime();

        FarmingBlock farmingBlock = commandBuffer.getComponent(ref, farmingBlockType);

        // Config dinámica: el GrowthModifier decodificado es la fuente de verdad.
        MghgCropGrowthModifierAsset cfg = MghgCropGrowthModifierAsset.getLastLoaded();
        if (cfg == null) {
            applyVisuals(store, commandBuffer, chunkRef, blockChunk, ref, farmingBlock, x, y, z, data);
            return;
        }

        int cooldownSeconds = cfg.getMutationRollCooldownSeconds();
        double chanceRain = cfg.getMutationChanceRain();
        double chanceSnow = cfg.getMutationChanceSnow();
        double chanceFrozen = cfg.getMutationChanceFrozen();
        IntSet rainIds = cfg.getRainWeatherIds();
        IntSet snowIds = cfg.getSnowWeatherIds();
        IntSet frozenIds = cfg.getFrozenWeatherIds();

        // Sin weathers configurados => no se muta, pero sí se asegura visual.
        if (rainIds == null && snowIds == null && frozenIds == null) {
            applyVisuals(store, commandBuffer, chunkRef, blockChunk, ref, farmingBlock, x, y, z, data);
            return;
        }

        // Chances en 0 => no hay mutación, solo visual.
        if (chanceRain <= 0.0 && chanceSnow <= 0.0 && chanceFrozen <= 0.0) {
            applyVisuals(store, commandBuffer, chunkRef, blockChunk, ref, farmingBlock, x, y, z, data);
            return;
        }

        // Siempre “sync visual” (corrige mismatch aunque no haya cooldown).
        Instant last = data.getLastMutationRoll();
        if (last != null && cooldownSeconds > 0) {
            Duration since = Duration.between(last, now);
            if (since.getSeconds() < cooldownSeconds) {
                applyVisuals(store, commandBuffer, chunkRef, blockChunk, ref, farmingBlock, x, y, z, data);
                return;
            }
        }

        MghgWeatherResolver.WeatherSnapshot weather = MghgWeatherResolver.resolveSnapshot(
                entityStore, blockChunk, x, y, z, rainIds, snowIds, frozenIds
        );

        // Si no llueve ni nieva, no “gastamos” cooldown, pero sí aseguramos visual.
        if (!weather.hasAny()) {
            applyVisuals(store, commandBuffer, chunkRef, blockChunk, ref, farmingBlock, x, y, z, data);
            return;
        }

        boolean dirty = MghgMutationEngine.tryMutate(
                data,
                now,
                weather,
                cooldownSeconds,
                chanceRain,
                chanceSnow,
                chanceFrozen
        );

        boolean visualsChanged = applyVisuals(store, commandBuffer, chunkRef, blockChunk, ref, farmingBlock, x, y, z, data);

        if (dirty || visualsChanged) {
            if (blockComponentChunk != null) blockComponentChunk.markNeedsSaving();
            blockChunk.markNeedsSaving();
        }
    }

    /**
     * Aplica visual inmediato:
     * - Si el crop tiene FarmingBlock: usa stageSet MGHG y aplica state actual.
     * - Si es bloque persistido sin FarmingBlock: usa variante stagefinal.
     */
    private boolean applyVisuals(Store<ChunkStore> store,
                                 CommandBuffer<ChunkStore> commandBuffer,
                                 Ref<ChunkStore> chunkRef,
                                 BlockChunk blockChunk,
                                 Ref<ChunkStore> blockRef,
                                 FarmingBlock farmingBlock,
                                 int x, int y, int z,
                                 MghgCropData data) {
        if (farmingBlock != null) {

            //LOGGER.atInfo().log("not farming block");
            Ref<ChunkStore> sectionRef = commandBuffer.getExternalData().getWorld()
                    .getChunkStore()
                    .getChunkSectionReference(
                            ChunkUtil.chunkCoordinate(x),
                            ChunkUtil.chunkCoordinate(y),
                            ChunkUtil.chunkCoordinate(z)
                    );
            if (sectionRef == null) return false;

            return MghgCropStageSync.syncStageSetAndRefresh(
                    commandBuffer,
                    sectionRef,
                    blockRef,
                    chunkRef,
                    blockChunk,
                    data,
                    farmingBlock,
                    x, y, z
            );
        }

        applyFinalVisual(store, commandBuffer, chunkRef, blockChunk, x, y, z, data);
        return false;
    }

    private void applyFinalVisual(Store<ChunkStore> store,
                                  CommandBuffer<ChunkStore> commandBuffer,
                                  Ref<ChunkStore> chunkRef,
                                  BlockChunk blockChunk,
                                  int x, int y, int z,
                                  MghgCropData data) {

        WorldChunk worldChunk = store.getComponent(chunkRef, WorldChunk.getComponentType());
        if (worldChunk == null) return;

        int currentId = blockChunk.getBlock(x, y, z);
        if (currentId == 0) return;

        BlockType currentType = BlockType.getAssetMap().getAsset(currentId);
        if (currentType == null) return;

        // siempre final stage
        String targetState = MghgCropVisualStateResolver.resolveVariantKey(data) + "_stagefinal";
        BlockType targetType = currentType.getBlockForState(targetState);
        if (targetType == null) return;

        int targetId = BlockType.getAssetMap().getIndex(targetType.getId());
        if (targetId == BlockType.UNKNOWN_ID || targetId == currentId) return;

        // ✅ evitar deprecated getRotationIndex(x,y,z)
        //int indexBlock = ChunkUtil.indexBlock(x, y, z);

        BlockState state = worldChunk.getState(x, y, z);
        int rotation = state != null ? state.getRotationIndex() : 0;

        blockChunk.markNeedsSaving();

        commandBuffer.getExternalData().getWorld().execute(() ->
                worldChunk.setBlock(x, y, z, targetId, targetType, rotation, 0, 2)
        );
    }
}
