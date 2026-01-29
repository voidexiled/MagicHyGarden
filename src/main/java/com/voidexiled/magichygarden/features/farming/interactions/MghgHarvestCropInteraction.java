package com.voidexiled.magichygarden.features.farming.interactions;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.voidexiled.magichygarden.features.farming.logic.MghgHarvestUtil;

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
        LOGGER.atWarning().log("111MGHG harvest interaction fired: blockType={} pos=({}, {}, {})");
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

        int rotationIndex = section.getRotationIndex(targetBlock.x, targetBlock.y, targetBlock.z);

        LOGGER.atWarning().log("MGHG harvest interaction fired: blockType={} pos=({}, {}, {})",
                blockType.getId(), targetBlock.x, targetBlock.y, targetBlock.z);
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
        LOGGER.atWarning().log("222MGHG harvest interaction fired: blockType={} pos=({}, {}, {})");
        // No client simulation
    }

    @Nonnull
    @Override
    public String toString() {
        return "MghgHarvestCropInteraction{} " + super.toString();
    }
}
