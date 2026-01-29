package com.voidexiled.magichygarden.features.farming.logic;

import com.hypixel.hytale.builtin.adventure.farming.states.FarmingBlock;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.HarvestingDropType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.farming.FarmingData;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.farming.FarmingStageData;
import com.hypixel.hytale.server.core.entity.ItemUtils;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
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
import com.voidexiled.magichygarden.features.farming.state.ClimateMutation;
import com.voidexiled.magichygarden.features.farming.state.RarityMutation;
import com.voidexiled.magichygarden.features.farming.visuals.MghgCropVisualStateResolver;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

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
        rerollOnReplant(chunkStore, blockRef, now);

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
        String dropListId = harvest.getDropListId();

        World world = store.getExternalData().getWorld();
        @Nullable MghgCropData cropData = tryGetCropData(world, blockPosition);

        for (ItemStack stack : BlockHarvestUtils.getDrops(blockType, 1, itemId, dropListId)) {
            ItemStack out = stack;


            if (cropData != null) {
                // 1) metadata SIEMPRE
                MghgCropMeta meta = MghgCropMeta.fromCropData(
                        cropData.getSize(),
                        cropData.getClimate().name(),
                        cropData.getRarity().name()
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
    }

    @Nullable
    public static MghgCropData tryGetCropData(@Nonnull World world, @Nonnull Vector3i blockPosition) {
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

        // 2) Persistido: entity holder (aún no rehidratado)
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

    /**
     * Reroll básico para “auto-replant”.
     * (Luego aquí mismo enchufas clima por WeatherResource y rarity por tus reglas reales)
     */
    private static void rerollOnReplant(
            @Nonnull Store<ChunkStore> chunkStore,
            @Nonnull Ref<ChunkStore> blockRef,
            @Nonnull Instant now
    ) {
        MghgCropData data = chunkStore.ensureAndGetComponent(blockRef, MghgCropData.getComponentType());

        // Size 50..100
        data.setSize(ThreadLocalRandom.current().nextInt(50, 101));

        // Climate default (por ahora; luego lo ligas a weather)
        data.setClimate(ClimateMutation.NONE);

        // Rarity (mutuamente excluyente)
        float r = ThreadLocalRandom.current().nextFloat();
        float rainbowChance = 0.0005f;
        float goldChance = 0.005f;

        if (r < rainbowChance) data.setRarity(RarityMutation.RAINBOW);
        else if (r < rainbowChance + goldChance) data.setRarity(RarityMutation.GOLD);
        else data.setRarity(RarityMutation.NONE);

        data.setLastMutationRoll(now);
    }
}
