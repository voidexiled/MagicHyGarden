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
import com.hypixel.hytale.server.core.asset.type.blocktype.config.farming.FarmingData;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.farming.FarmingStageData;
import com.hypixel.hytale.server.core.asset.type.weather.config.Weather;
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
import com.voidexiled.magichygarden.features.farming.state.MghgMutationContext;
import com.voidexiled.magichygarden.features.farming.state.MghgMutationEngine;
import com.voidexiled.magichygarden.features.farming.state.MghgMutationRuleSet;
import com.voidexiled.magichygarden.features.farming.state.MghgMutationRules;
import com.voidexiled.magichygarden.features.farming.state.MghgWeatherResolver;
import com.voidexiled.magichygarden.features.farming.state.MutationEventType;
import com.voidexiled.magichygarden.features.farming.visuals.MghgCropStageSync;
import com.voidexiled.magichygarden.features.farming.visuals.MghgCropVisualStateResolver;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

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
        MghgMutationRuleSet rules = MghgMutationRules.getActive(cfg);
        if (rules == null || rules.isEmpty()) {
            applyVisuals(store, commandBuffer, chunkRef, blockChunk, ref, farmingBlock, x, y, z, data);
            return;
        }

        int weatherId = MghgWeatherResolver.resolveWeatherId(entityStore, blockChunk, x, y, z);
        if (weatherId == Weather.UNKNOWN_ID) {
            applyVisuals(store, commandBuffer, chunkRef, blockChunk, ref, farmingBlock, x, y, z, data);
            return;
        }

        boolean mature = isMature(blockType, farmingBlock);
        Set<String> adjacent = resolveAdjacentIds(rules, blockChunk, x, y, z);

        MghgMutationContext ctx = new MghgMutationContext(
                now,
                MutationEventType.WEATHER,
                weatherId,
                mature,
                true,
                adjacent
        );

        boolean dirty = MghgMutationEngine.applyRules(
                data,
                ctx,
                rules,
                cooldownSeconds
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

    private static boolean isMature(BlockType blockType, FarmingBlock farmingBlock) {
        if (blockType == null || farmingBlock == null) return false;
        BlockType base = resolveBaseBlockType(blockType);
        FarmingData farming = base != null ? base.getFarming() : null;
        if (farming == null || farming.getStages() == null) return false;
        String stageSet = farmingBlock.getCurrentStageSet();
        if (stageSet == null) return false;
        FarmingStageData[] stages = farming.getStages().get(stageSet);
        if (stages == null || stages.length == 0) return false;
        int stageIndex = (int) farmingBlock.getGrowthProgress();
        return stageIndex >= stages.length - 1;
    }

    private static BlockType resolveBaseBlockType(BlockType current) {
        if (current == null) return null;
        String id = current.getId();
        if (id == null || id.isEmpty()) return current;
        if (id.charAt(0) == '*') {
            int idx = id.indexOf("_State_");
            if (idx > 1) {
                String baseId = id.substring(1, idx);
                BlockType base = BlockType.getAssetMap().getAsset(baseId);
                if (base != null) return base;
            }
        }
        return current;
    }

    private static Set<String> resolveAdjacentIds(MghgMutationRuleSet rules, BlockChunk blockChunk, int x, int y, int z) {
        if (rules == null || rules.getRequiredAdjacentBlockIds().isEmpty()) {
            return Collections.emptySet();
        }

        Set<String> required = rules.getRequiredAdjacentBlockIds();
        Set<String> found = new HashSet<>();
        checkNeighbor(blockChunk, x + 1, y, z, required, found);
        checkNeighbor(blockChunk, x - 1, y, z, required, found);
        checkNeighbor(blockChunk, x, y, z + 1, required, found);
        checkNeighbor(blockChunk, x, y, z - 1, required, found);
        checkNeighbor(blockChunk, x, y + 1, z, required, found);
        checkNeighbor(blockChunk, x, y - 1, z, required, found);
        return found.isEmpty() ? Collections.emptySet() : found;
    }

    private static void checkNeighbor(
            BlockChunk blockChunk,
            int x, int y, int z,
            Set<String> required,
            Set<String> found
    ) {
        int id = blockChunk.getBlock(x, y, z);
        if (id == 0) return;
        BlockType type = BlockType.getAssetMap().getAsset(id);
        if (type == null) return;
        BlockType base = resolveBaseBlockType(type);
        String blockId = base != null ? base.getId() : type.getId();
        if (blockId == null) return;
        if (required.contains(blockId)) {
            found.add(blockId);
        }
    }
}
