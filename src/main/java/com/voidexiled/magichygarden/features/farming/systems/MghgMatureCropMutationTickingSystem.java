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
import com.voidexiled.magichygarden.features.farming.state.MghgAdjacencyScanner;
import com.voidexiled.magichygarden.features.farming.state.MghgBlockIdUtil;
import com.voidexiled.magichygarden.features.farming.state.CooldownClock;
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

        var world = commandBuffer.getExternalData().getWorld();
        Store<EntityStore> entityStore = world.getEntityStore().getStore();

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

        Instant now = resolveNow(entityStore, rules.getCooldownClock());

        boolean needsAdjacency = rules.needsAdjacentItems() || rules.needsAdjacentParticles();
        boolean ignoreSkyCheck = false;
        boolean hasNonWeatherRule = false;
        if (!needsAdjacency || !ignoreSkyCheck || !hasNonWeatherRule) {
            for (var rule : rules.getRules()) {
                if (rule == null) continue;
                if (!needsAdjacency) {
                    var reqs = rule.getRequiresAdjacentItems();
                    if (reqs != null && reqs.length > 0) {
                        needsAdjacency = true;
                    }
                    var particleReqs = rule.getRequiresAdjacentParticles();
                    if (!needsAdjacency && particleReqs != null && particleReqs.length > 0) {
                        needsAdjacency = true;
                    }
                }
                if (!ignoreSkyCheck && rule.isIgnoreSkyCheck()) {
                    ignoreSkyCheck = true;
                }
                if (!hasNonWeatherRule && rule.getEventType() != MutationEventType.WEATHER) {
                    hasNonWeatherRule = true;
                }
                if (needsAdjacency && ignoreSkyCheck && hasNonWeatherRule) break;
            }
        }

        int weatherId = MghgWeatherResolver.resolveWeatherId(entityStore, blockChunk, x, y, z, false);
        int weatherIdIgnoreSky = ignoreSkyCheck
                ? MghgWeatherResolver.resolveWeatherId(entityStore, blockChunk, x, y, z, true)
                : weatherId;
        if (weatherId == Weather.UNKNOWN_ID && !ignoreSkyCheck && !hasNonWeatherRule) {
            applyVisuals(store, commandBuffer, chunkRef, blockChunk, ref, farmingBlock, x, y, z, data);
            return;
        }

        boolean mature = isMature(blockType, farmingBlock);
        Set<String> adjacent = resolveAdjacentIds(rules, blockChunk, x, y, z);
        MghgAdjacencyScanner adjacencyScanner = needsAdjacency
                ? new MghgAdjacencyScanner(store, commandBuffer.getExternalData().getWorld().getChunkStore())
                : null;
        int worldX = ChunkUtil.worldCoordFromLocalCoord(worldChunk.getX(), x);
        int worldZ = ChunkUtil.worldCoordFromLocalCoord(worldChunk.getZ(), z);
        int lightSky = Byte.toUnsignedInt(blockChunk.getSkyLight(x, y, z));
        int lightBlock = Short.toUnsignedInt(blockChunk.getBlockLight(x, y, z));
        int lightBlockIntensity = Byte.toUnsignedInt(blockChunk.getBlockLightIntensity(x, y, z));
        int lightRed = Byte.toUnsignedInt(blockChunk.getRedBlockLight(x, y, z));
        int lightGreen = Byte.toUnsignedInt(blockChunk.getGreenBlockLight(x, y, z));
        int lightBlue = Byte.toUnsignedInt(blockChunk.getBlueBlockLight(x, y, z));

        WorldTimeResource time = entityStore.getResource(WorldTimeResource.getResourceType());
        int currentHour = time != null ? time.getCurrentHour() : -1;
        double sunlightFactor = time != null ? time.getSunlightFactor() : -1.0;

        StageInfo stageInfo = resolveStageInfo(blockType, farmingBlock);
        String soilBlockId = resolveSoilBlockId(blockChunk, x, y, z);

        MghgMutationContext ctx = new MghgMutationContext(
                now,
                MutationEventType.WEATHER,
                weatherId,
                weatherIdIgnoreSky,
                mature,
                true,
                adjacent,
                adjacencyScanner,
                world.getWorldConfig().getUuid(),
                worldX, y, worldZ,
                lightSky,
                lightBlock,
                lightBlockIntensity,
                lightRed,
                lightGreen,
                lightBlue,
                currentHour,
                sunlightFactor,
                stageInfo.stageIndex,
                stageInfo.stageCount,
                stageInfo.stageSet,
                soilBlockId
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
        if (blockType == null) return false;

        if (farmingBlock != null) {
            BlockType base = MghgBlockIdUtil.resolveBaseBlockType(blockType);
            FarmingData farming = base != null ? base.getFarming() : null;
            if (farming == null || farming.getStages() == null) return false;
            String stageSet = farmingBlock.getCurrentStageSet();
            if (stageSet == null) return false;
            FarmingStageData[] stages = farming.getStages().get(stageSet);
            if (stages == null || stages.length == 0) return false;
            int stageIndex = (int) farmingBlock.getGrowthProgress();
            return stageIndex >= stages.length - 1;
        }

        String id = blockType.getId();
        if (id == null || id.isBlank()) return false;
        return id.toLowerCase().contains("_stagefinal");
    }

    private static StageInfo resolveStageInfo(BlockType blockType, FarmingBlock farmingBlock) {
        if (blockType == null) {
            return new StageInfo(-1, -1, null);
        }

        BlockType base = MghgBlockIdUtil.resolveBaseBlockType(blockType);
        FarmingData farming = base != null ? base.getFarming() : null;

        if (farmingBlock != null && farming != null && farming.getStages() != null) {
            String stageSet = farmingBlock.getCurrentStageSet();
            FarmingStageData[] stages = stageSet != null ? farming.getStages().get(stageSet) : null;
            int stageCount = stages == null ? -1 : stages.length;
            int stageIndex = (int) farmingBlock.getGrowthProgress();
            return new StageInfo(stageIndex, stageCount, stageSet);
        }

        if (farming != null && farming.getStages() != null) {
            String stageSet = "Default";
            FarmingStageData[] stages = farming.getStages().get(stageSet);
            int stageCount = stages == null ? -1 : stages.length;
            int stageIndex = -1;
            String id = blockType.getId();
            if (id != null && id.toLowerCase().contains("_stagefinal") && stageCount > 0) {
                stageIndex = stageCount - 1;
            }
            return new StageInfo(stageIndex, stageCount, stageSet);
        }

        return new StageInfo(-1, -1, null);
    }

    private static String resolveSoilBlockId(BlockChunk blockChunk, int x, int y, int z) {
        if (blockChunk == null || y <= 0) return null;
        int soilId = blockChunk.getBlock(x, y - 1, z);
        if (soilId == 0) return null;
        BlockType soilType = BlockType.getAssetMap().getAsset(soilId);
        if (soilType == null) return null;
        BlockType base = MghgBlockIdUtil.resolveBaseBlockType(soilType);
        String id = base != null ? base.getId() : soilType.getId();
        return MghgBlockIdUtil.normalizeId(id);
    }

    private static final class StageInfo {
        final int stageIndex;
        final int stageCount;
        final String stageSet;

        private StageInfo(int stageIndex, int stageCount, String stageSet) {
            this.stageIndex = stageIndex;
            this.stageCount = stageCount;
            this.stageSet = stageSet;
        }
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
        BlockType base = MghgBlockIdUtil.resolveBaseBlockType(type);
        String blockId = base != null ? base.getId() : type.getId();
        if (blockId == null) return;
        String normalized = MghgBlockIdUtil.normalizeId(blockId);
        if (normalized != null && required.contains(normalized)) {
            found.add(normalized);
        }
    }

    private static Instant resolveNow(Store<EntityStore> entityStore, CooldownClock clock) {
        if (clock == CooldownClock.GAME_TIME) {
            return entityStore.getResource(WorldTimeResource.getResourceType()).getGameTime();
        }
        return Instant.now();
    }
}
