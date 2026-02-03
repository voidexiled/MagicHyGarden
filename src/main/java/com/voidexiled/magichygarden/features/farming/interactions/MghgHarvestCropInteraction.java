package com.voidexiled.magichygarden.features.farming.interactions;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.ItemUtils;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.voidexiled.magichygarden.features.farming.components.MghgCropData;
import com.voidexiled.magichygarden.features.farming.logic.MghgHarvestUtil;
import com.voidexiled.magichygarden.features.farming.items.MghgCropMeta;
import com.voidexiled.magichygarden.features.farming.registry.MghgCropRegistry;
import com.voidexiled.magichygarden.features.farming.visuals.MghgCropVisualStateResolver;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class MghgHarvestCropInteraction extends SimpleBlockInteraction {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public static final BuilderCodec<MghgHarvestCropInteraction> CODEC =
            BuilderCodec.builder(MghgHarvestCropInteraction.class, MghgHarvestCropInteraction::new, SimpleBlockInteraction.CODEC)
                    .documentation("Harvest crop but attach MagicHyGarden crop state + metadata to resulting items.")
                    .build();

    @Override
    protected void interactWithBlock(
            @Nonnull World world,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull InteractionType type,
            @Nonnull InteractionContext context,
            @Nullable ItemStack itemInHand,
            @Nonnull Vector3i targetBlock,
            @Nonnull CooldownHandler cooldownHandler
    ) {
        LOGGER.atWarning().log(
                "111MGHG harvest interaction fired: type=%s pos=%d,%d,%d",
                type, targetBlock.x, targetBlock.y, targetBlock.z
        );
        Ref<EntityStore> playerRef = context.getEntity();

        ChunkStore chunkStore = world.getChunkStore();
        long chunkIndex = ChunkUtil.indexChunkFromBlock(targetBlock.x, targetBlock.z);
        Ref<ChunkStore> chunkRef = chunkStore.getChunkReference(chunkIndex);

        if (chunkRef == null || !chunkRef.isValid()) {
            return;
        }

        BlockChunk blockChunk = chunkStore.getStore().getComponent(chunkRef, BlockChunk.getComponentType());
        if (blockChunk == null) {
            return;
        }

        BlockSection section = blockChunk.getSectionAtBlockY(targetBlock.y);
        if (section == null) {
            return;
        }

        WorldChunk worldChunk = chunkStore.getStore().getComponent(chunkRef, WorldChunk.getComponentType());
        if (worldChunk == null) {
            return;
        }

        BlockType blockType = worldChunk.getBlockType(targetBlock);
        if (blockType == null) {
            return;
        }
        if (!MghgCropRegistry.isMghgCropBlock(blockType)) {
            return;
        }
        // Decorative placed blocks (no farming data) -> pickup with metadata
        if (blockType.getFarming() == null) {
            if (playerRef == null || !playerRef.isValid()) {
                return;
            }

            String itemId = null;
            var def = MghgCropRegistry.getDefinition(blockType);
            if (def != null && def.getItemId() != null && !def.getItemId().isBlank()) {
                itemId = def.getItemId();
            } else if (blockType.getItem() != null) {
                itemId = blockType.getItem().getId();
            }
            if (itemId == null) {
                return;
            }

            ItemStack out = new ItemStack(itemId, 1);
            MghgCropData cropData = MghgHarvestUtil.tryGetCropData(world, targetBlock);
            if (cropData != null) {
                MghgCropMeta meta = MghgCropMeta.fromCropData(
                        cropData.getSize(),
                        cropData.getClimate().name(),
                        cropData.getLunar().name(),
                        cropData.getRarity().name()
                );
                out = out.withMetadata(MghgCropMeta.KEY, meta);

                String resolvedState = MghgCropVisualStateResolver.resolveItemState(cropData);
                if (resolvedState != null && out.getItem().getItemIdForState(resolvedState) != null) {
                    out = out.withState(resolvedState);
                }
            }

            Vector3d origin = new Vector3d(targetBlock.x + 0.5, targetBlock.y + 0.5, targetBlock.z + 0.5);
            ItemUtils.interactivelyPickupItem(playerRef, out, origin, commandBuffer);

            final int blockIndexColumn = ChunkUtil.indexBlockInColumn(targetBlock.x, targetBlock.y, targetBlock.z);
            world.execute(() -> {
                WorldChunk chunk = world.getChunkIfInMemory(chunkIndex);
                if (chunk == null) {
                    return;
                }

                // Limpia estado/holder para evitar re-apariciones sin data
                try {
                    chunk.setState(
                            targetBlock.x,
                            targetBlock.y,
                            targetBlock.z,
                            (com.hypixel.hytale.component.Holder<com.hypixel.hytale.server.core.universe.world.storage.ChunkStore>) null
                    );
                } catch (Throwable ignored) {
                    // Si setState no acepta null en esta versión, simplemente ignoramos
                }

                BlockComponentChunk componentChunk =
                        chunkStore.getStore().getComponent(chunkRef, BlockComponentChunk.getComponentType());
                if (componentChunk != null) {
                    componentChunk.removeEntityHolder(blockIndexColumn);
                }

                chunk.breakBlock(targetBlock.x, targetBlock.y, targetBlock.z);
            });
            return;
        }

        int rotationIndex = section.getRotationIndex(targetBlock.x, targetBlock.y, targetBlock.z);

        LOGGER.atWarning().log(
                "MGHG harvest interaction fired: blockType=%s pos=%d,%d,%d",
                blockType.getId(), targetBlock.x, targetBlock.y, targetBlock.z
        );
        MghgHarvestUtil.harvest(world, commandBuffer, playerRef, blockType, rotationIndex, targetBlock);
    }

    @Override
    protected void simulateInteractWithBlock(
            @Nonnull InteractionType type,
            @Nonnull InteractionContext context,
            @Nullable ItemStack itemInHand,
            @Nonnull World world,
            @Nonnull Vector3i targetBlock
    ) {
        LOGGER.atWarning().log(
                "222MGHG harvest interaction fired: type=%s pos=%d,%d,%d",
                type, targetBlock.x, targetBlock.y, targetBlock.z
        );
        // No client simulation
    }

    @Nonnull
    @Override
    public String toString() {
        return "MghgHarvestCropInteraction{} " + super.toString();
    }
}
