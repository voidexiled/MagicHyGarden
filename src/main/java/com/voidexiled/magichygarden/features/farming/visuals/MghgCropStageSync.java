package com.voidexiled.magichygarden.features.farming.visuals;

import com.hypixel.hytale.builtin.adventure.farming.states.FarmingBlock;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.farming.FarmingData;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.farming.FarmingStageData;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.meta.BlockState;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.voidexiled.magichygarden.features.farming.components.MghgCropData;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class MghgCropStageSync {
    private MghgCropStageSync() {}

    /**
     * Sincroniza el stageSet del FarmingBlock con la variante MGHG (mghg_*).
     * Aplica el estado correcto del stage actual inmediatamente, sin esperar al tick de growth.
     *
     * Estrategia:
     * 1) Resolver BlockType base (evita NPE con BlockType state assets).
     * 2) Resolver state key del stage (via reflexión).
     * 3) setBlock directo si es posible.
     * 4) Fallback: stage.apply(...) o scheduleTick si falla.
     */
    public static boolean syncStageSetAndRefresh(
            CommandBuffer<ChunkStore> commandBuffer,
            Ref<ChunkStore> sectionRef,
            Ref<ChunkStore> blockRef,
            @Nullable Ref<ChunkStore> chunkColRef,
            @Nullable BlockChunk blockChunk,
            MghgCropData data,
            @Nullable FarmingBlock farmingBlock,
            int x, int y, int z
    ) {
        if (farmingBlock == null) return false;
        if (chunkColRef == null || blockChunk == null) return false;

        int blockId = blockChunk.getBlock(x, y, z);
        if (blockId == 0) return false;

        BlockType rawType = BlockType.getAssetMap().getAsset(blockId);
        if (rawType == null) return false;

        BlockType blockType = resolveBaseBlockType(rawType);
        if (blockType == null) return false;

        FarmingData farming = blockType.getFarming();
        if (farming == null || farming.getStages() == null) return false;

        // StageSet objetivo según rarity/climate
        String desiredStageSet = MghgCropVisualStateResolver.resolveBlockStageSet(data);
        if (desiredStageSet == null) return false;

        FarmingStageData[] stages = farming.getStages().get(desiredStageSet);
        if (stages == null || stages.length == 0) return false;

        String currentStageSet = farmingBlock.getCurrentStageSet();
        boolean stageSetChanged = !desiredStageSet.equals(currentStageSet);
        if (stageSetChanged) {
            farmingBlock.setCurrentStageSet(desiredStageSet);
        }

        int stageIndex = (int) farmingBlock.getGrowthProgress();
        if (stageIndex < 0) stageIndex = 0;
        if (stageIndex >= stages.length) stageIndex = stages.length - 1;

        boolean refreshed = false;

        // Resolver el state key del stage (ej: "Stage1")
        String stateKey = tryGetStageStateKey(stages[stageIndex]);
        if (stateKey != null) {
            if (applyBlockState(commandBuffer, chunkColRef, blockChunk, blockType, stateKey, x, y, z)) {
                refreshed = true;
            }
        }

        if (!refreshed) {
            try {
                stages[stageIndex].apply(commandBuffer, sectionRef, blockRef, x, y, z, null);
                refreshed = true;
            } catch (Throwable ignored) {
                // Fallback seguro: programar tick para que el farming system refresque en el siguiente tick
                scheduleTick(commandBuffer, sectionRef, x, y, z);
                refreshed = true;
            }
        }

        return stageSetChanged || refreshed;
    }

    /**
     * Si el BlockType es un "state asset" (id empieza con '*'), intenta resolver el BlockType base.
     * Esto evita NPEs en BlockType.getDefaultStateKey() cuando el state asset no tiene AssetExtraInfo.
     */
    private static @Nullable BlockType resolveBaseBlockType(@Nonnull BlockType current) {
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

    /**
     * Intenta obtener el state key del stage.
     * Se usa reflexión porque FarmingStageData no expone siempre getter público.
     */
    private static @Nullable String tryGetStageStateKey(@Nonnull FarmingStageData stage) {
        // 1) método público getState()
        try {
            var method = stage.getClass().getMethod("getState");
            Object value = method.invoke(stage);
            if (value instanceof String s && !s.isEmpty()) return s;
        } catch (Throwable ignored) {
            // ignore
        }

        // 2) campo "state" privado/protegido
        try {
            var field = stage.getClass().getDeclaredField("state");
            field.setAccessible(true);
            Object value = field.get(stage);
            if (value instanceof String s && !s.isEmpty()) return s;
        } catch (Throwable ignored) {
            // ignore
        }

        return null;
    }

    /**
     * Aplica el state directamente usando el BlockType base.
     * Respeta rotación actual y evita esperar al tick de growth.
     */
    private static boolean applyBlockState(
            CommandBuffer<ChunkStore> commandBuffer,
            Ref<ChunkStore> chunkColRef,
            BlockChunk blockChunk,
            BlockType baseType,
            String stateKey,
            int x, int y, int z
    ) {
        BlockType targetType = baseType.getBlockForState(stateKey);
        if (targetType == null) return false;

        int targetId = BlockType.getAssetMap().getIndex(targetType.getId());
        if (targetId == BlockType.UNKNOWN_ID) return false;

        int currentId = blockChunk.getBlock(x, y, z);
        if (currentId == targetId) return true;

        WorldChunk worldChunk = commandBuffer.getComponent(chunkColRef, WorldChunk.getComponentType());
        if (worldChunk == null) return false;

        BlockState state = worldChunk.getState(x, y, z);
        int rotation = state != null ? state.getRotationIndex() : 0;

        commandBuffer.getExternalData().getWorld().execute(() ->
                worldChunk.setBlock(x, y, z, targetId, targetType, rotation, 0, 2)
        );

        return true;
    }

    /**
     * Programa un tick de farming para que el motor refresque el estado en el siguiente tick.
     */
    private static void scheduleTick(
            CommandBuffer<ChunkStore> commandBuffer,
            Ref<ChunkStore> sectionRef,
            int x, int y, int z
    ) {
        BlockSection section = commandBuffer.getComponent(sectionRef, BlockSection.getComponentType());
        if (section == null) return;

        Store<EntityStore> entityStore = commandBuffer.getExternalData().getWorld().getEntityStore().getStore();
        WorldTimeResource time = entityStore.getResource(WorldTimeResource.getResourceType());
        if (time == null) return;

        section.scheduleTick(ChunkUtil.indexBlock(x, y, z), time.getGameTime());
    }
}
