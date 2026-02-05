package com.voidexiled.magichygarden.features.farming.logic;

import com.hypixel.hytale.builtin.adventure.farming.states.FarmingBlock;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.HarvestingDropType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.farming.FarmingData;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.farming.FarmingStageData;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.entity.ItemUtils;
import com.hypixel.hytale.server.core.modules.interaction.BlockHarvestUtils;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.voidexiled.magichygarden.features.farming.components.MghgCropData;
import com.voidexiled.magichygarden.features.farming.items.MghgCropMeta;
import com.voidexiled.magichygarden.features.farming.registry.MghgCropRegistry;
import com.voidexiled.magichygarden.features.farming.visuals.MghgCropVisualStateResolver;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class MghgHarvestUtil {
    private MghgHarvestUtil() {
    }

    public static void harvest(
            @Nonnull World world,
            @Nonnull ComponentAccessor<EntityStore> store,
            @Nonnull Ref<EntityStore> playerRef,
            @Nonnull BlockType blockType,
            int rotationIndex,
            @Nonnull Vector3i blockPosition
    ) {
        if (world.getGameplayConfig().getWorldConfig().isBlockGatheringAllowed()) {
            harvest0(store, playerRef, blockType, rotationIndex, blockPosition);
        }
    }

    // Copia basada en FarmingUtil.harvest0(...) con cambio: giveDropsWithVisualAndMeta(...)
    private static boolean harvest0(
            @Nonnull ComponentAccessor<EntityStore> store,
            @Nonnull Ref<EntityStore> playerRef,
            @Nonnull BlockType blockType,
            int rotationIndex,
            @Nonnull Vector3i blockPosition
    ) {
        FarmingData farmingConfig = blockType.getFarming();
        if (farmingConfig == null || farmingConfig.getStages() == null) {
            return false;
        }
        if (blockType.getGathering().getHarvest() == null) {
            return false;
        }

        World world = store.getExternalData().getWorld();

        Vector3d centerPosition = new Vector3d();
        blockType.getBlockCenter(rotationIndex, centerPosition);
        centerPosition.add(blockPosition);

        // CASE A: NO replant (StageSetAfterHarvest == null) -> drop + break block
        if (farmingConfig.getStageSetAfterHarvest() == null) {
            giveDropsWithVisualAndMeta(store, playerRef, centerPosition, blockType, blockPosition);

            WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(blockPosition.x, blockPosition.z));
            if (chunk != null) {
                chunk.breakBlock(blockPosition.x, blockPosition.y, blockPosition.z);
            }
            return true;
        }

        // CASE B: replant -> drop + reset farming stage set
        giveDropsWithVisualAndMeta(store, playerRef, centerPosition, blockType, blockPosition);

        Map<String, FarmingStageData[]> stageSets = farmingConfig.getStages();
        FarmingStageData[] stages = stageSets.get(farmingConfig.getStartingStageSet());
        if (stages == null) {
            return false;
        }

        int currentStageIndex = stages.length - 1;
        FarmingStageData previousStage = stages[currentStageIndex];

        String newStageSet = farmingConfig.getStageSetAfterHarvest();
        FarmingStageData[] newStages = stageSets.get(newStageSet);
        if (newStages == null || newStages.length == 0) {
            WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(blockPosition.x, blockPosition.z));
            if (chunk != null) {
                chunk.breakBlock(blockPosition.x, blockPosition.y, blockPosition.z);
            }
            return false;
        }

        Store<ChunkStore> chunkStore = world.getChunkStore().getStore();
        Ref<ChunkStore> chunkRef =
                world.getChunkStore().getChunkReference(ChunkUtil.indexChunkFromBlock(blockPosition.x, blockPosition.z));
        if (chunkRef == null) {
            return false;
        }

        BlockComponentChunk blockComponentChunk =
                chunkStore.getComponent(chunkRef, BlockComponentChunk.getComponentType());
        if (blockComponentChunk == null) {
            return false;
        }

        Instant now = store.getExternalData()
                .getWorld()
                .getEntityStore()
                .getStore()
                .getResource(WorldTimeResource.getResourceType())
                .getGameTime();

        int blockIndexColumn = ChunkUtil.indexBlockInColumn(blockPosition.x, blockPosition.y, blockPosition.z);

        // Rehidrata holder persistido si existe, o crea entity si falta
        Ref<ChunkStore> blockRef = blockComponentChunk.getEntityReference(blockIndexColumn);
        if (blockRef == null) {
            Holder<ChunkStore> holder = blockComponentChunk.removeEntityHolder(blockIndexColumn);
            if (holder != null) {
                if (holder.getComponent(BlockModule.BlockStateInfo.getComponentType()) == null) {
                    holder.putComponent(
                            BlockModule.BlockStateInfo.getComponentType(),
                            new BlockModule.BlockStateInfo(blockIndexColumn, chunkRef)
                    );
                }
                blockRef = chunkStore.addEntity(holder, AddReason.SPAWN);
            }
        }

        FarmingBlock farmingBlock;
        if (blockRef == null) {
            Holder<ChunkStore> blockEntity = ChunkStore.REGISTRY.newHolder();
            blockEntity.putComponent(
                    BlockModule.BlockStateInfo.getComponentType(),
                    new BlockModule.BlockStateInfo(blockIndexColumn, chunkRef)
            );

            farmingBlock = new FarmingBlock();
            farmingBlock.setLastTickGameTime(now);
            farmingBlock.setCurrentStageSet(newStageSet);

            blockEntity.addComponent(FarmingBlock.getComponentType(), farmingBlock);
            blockRef = chunkStore.addEntity(blockEntity, AddReason.SPAWN);
        } else {
            farmingBlock = chunkStore.ensureAndGetComponent(blockRef, FarmingBlock.getComponentType());
        }

        // Reset farming state
        farmingBlock.setCurrentStageSet(newStageSet);
        farmingBlock.setGrowthProgress(0.0F);
        farmingBlock.setExecutions(0);
        farmingBlock.setGeneration(farmingBlock.getGeneration() + 1);

        // “Kick” inicial: evita quedarse en diff=0
        farmingBlock.setLastTickGameTime(now.minusSeconds(1));
        farmingBlock.setPreviousBlockType(null);

        // ✅ REROLL MGHG data (porque esto es “replant after harvest”)
        rerollOnReplant(chunkStore, blockRef);

        Ref<ChunkStore> sectionRef = world.getChunkStore().getChunkSectionReference(
                ChunkUtil.chunkCoordinate(blockPosition.x),
                ChunkUtil.chunkCoordinate(blockPosition.y),
                ChunkUtil.chunkCoordinate(blockPosition.z)
        );
        if (sectionRef == null) {
            return false;
        }

        BlockSection section = chunkStore.getComponent(sectionRef, BlockSection.getComponentType());
        if (section != null) {
            // Importantísimo: preTick usa isBefore(gameTime), así que “now.minusNanos(1)” garantiza entrar.
            section.scheduleTick(ChunkUtil.indexBlock(blockPosition.x, blockPosition.y, blockPosition.z), now.minusNanos(1));
        }

        newStages[0].apply(chunkStore, sectionRef, blockRef, blockPosition.x, blockPosition.y, blockPosition.z, previousStage);
        return true;
    }

    private static void giveDropsWithVisualAndMeta(
            @Nonnull ComponentAccessor<EntityStore> store,
            @Nonnull Ref<EntityStore> playerRef,
            @Nonnull Vector3d origin,
            @Nonnull BlockType blockType,
            @Nonnull Vector3i blockPosition
    ) {
        HarvestingDropType harvest = blockType.getGathering().getHarvest();
        String itemId = harvest.getItemId();
        String dropListId = MghgDropListResolver.resolveDropListId(harvest.getDropListId());

        World world = store.getExternalData().getWorld();
        @Nullable MghgCropData cropData = tryGetCropData(world, blockPosition);

        List<ItemStack> baseDrops = new ArrayList<>(
                BlockHarvestUtils.getDrops(blockType, 1, itemId, dropListId)
        );
        List<ItemStack> extraDrops = MghgDropListResolver.collectExtraDrops(blockType, cropData);

        for (ItemStack stack : baseDrops) {
            ItemStack out = stack;

            if (cropData != null && shouldApplyMghgMeta(out, cropData, itemId)) {
                // 1) metadata SOLO para el item del crop
                double weight = cropData.getWeightGrams();
                if (weight <= 0.0) {
                    weight = MghgWeightUtil.computeWeightAtMatureGrams(blockType, cropData.getSize());
                    if (weight > 0.0) {
                        cropData.setWeightGrams(weight);
                    }
                }
                MghgCropMeta meta = MghgCropMeta.fromCropData(
                        cropData.getSize(),
                        cropData.getClimate().name(),
                        cropData.getLunar().name(),
                        cropData.getRarity().name(),
                        weight
                );
                out = out.withMetadata(MghgCropMeta.KEY, meta);

                // 2) state SOLO si existe en el item
                String resolvedState = MghgCropVisualStateResolver.resolveItemState(cropData);
                if (resolvedState != null && out.getItem().getItemIdForState(resolvedState) != null) {
                    out = out.withState(resolvedState);
                }
            }

            ItemUtils.interactivelyPickupItem(playerRef, out, origin, store);
        }

        if (!extraDrops.isEmpty()) {
            for (ItemStack stack : extraDrops) {
                ItemStack out = stack;

                if (cropData != null && shouldApplyMghgMeta(out, cropData, itemId)) {
                    double weight = cropData.getWeightGrams();
                    if (weight <= 0.0) {
                        weight = MghgWeightUtil.computeWeightAtMatureGrams(blockType, cropData.getSize());
                        if (weight > 0.0) {
                            cropData.setWeightGrams(weight);
                        }
                    }
                    MghgCropMeta meta = MghgCropMeta.fromCropData(
                            cropData.getSize(),
                            cropData.getClimate().name(),
                            cropData.getLunar().name(),
                            cropData.getRarity().name(),
                            weight
                    );
                    out = out.withMetadata(MghgCropMeta.KEY, meta);

                    String resolvedState = MghgCropVisualStateResolver.resolveItemState(cropData);
                    if (resolvedState != null && out.getItem().getItemIdForState(resolvedState) != null) {
                        out = out.withState(resolvedState);
                    }
                }

                ItemUtils.interactivelyPickupItem(playerRef, out, origin, store);
            }
        }
    }

    /**
     * Decide si un drop debe recibir metadata MGHG.
     * Regla:
     * - Debe ser el item principal del harvest (itemId del Harvest config), o
     * - Debe soportar estados MGHG (mghg_*).
     */
    public static boolean shouldApplyMghgMeta(@Nonnull ItemStack stack,
                                              @Nonnull MghgCropData cropData,
                                              @Nullable String harvestItemId) {
        if (stack.getItem() != null && harvestItemId != null) {
            String baseId = stack.getItem().getId();
            if (harvestItemId.equals(baseId)) {
                return true;
            }
        }

        String resolvedState = MghgCropVisualStateResolver.resolveItemState(cropData);
        if (resolvedState != null && stack.getItem() != null) {
            return stack.getItem().getItemIdForState(resolvedState) != null;
        }

        return false;
    }

    @Nullable
    public static MghgCropData tryGetCropData(@Nonnull World world, @Nonnull Vector3i blockPosition) {
        return MghgCropDataAccess.tryGetCropData(world, blockPosition);
    }

    /**
     * Reroll básico para “auto-replant”.
     * Usa el seeder unificado para mantener el mismo comportamiento que en plantado inicial.
     */
    private static void rerollOnReplant(
            @Nonnull Store<ChunkStore> chunkStore,
            @Nonnull Ref<ChunkStore> blockRef
    ) {
        MghgCropData data = chunkStore.ensureAndGetComponent(blockRef, MghgCropData.getComponentType());
        BlockType blockType = MghgWeightUtil.resolveBlockType(chunkStore, blockRef);
        double baseWeight = MghgCropRegistry.getBaseWeightGrams(blockType);
        MghgCropDataSeeder.seedReplant(data, baseWeight);
    }
}
