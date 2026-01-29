package com.voidexiled.magichygarden.features.farming.systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.ItemUtils;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.voidexiled.magichygarden.features.farming.components.MghgCropData;
import com.voidexiled.magichygarden.features.farming.items.MghgCropMeta;
import com.voidexiled.magichygarden.features.farming.visuals.MghgCropVisualStateResolver;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class MghgPreserveCropMetaOnUseSystem extends EntityEventSystem<EntityStore, UseBlockEvent.Pre> {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final boolean DEBUG = true;

    private final ComponentType<ChunkStore, MghgCropData> cropDataType;

    public MghgPreserveCropMetaOnUseSystem(@Nonnull ComponentType<ChunkStore, MghgCropData> cropDataType) {
        super(UseBlockEvent.Pre.class);
        this.cropDataType = cropDataType;
    }

    @Override
    public @Nullable Query<EntityStore> getQuery() {
        return PlayerRef.getComponentType();
    }

    @Override
    public void handle(
            int index,
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull UseBlockEvent.Pre event
    ) {
        if (event.isCancelled()) return;

        InteractionType type = event.getInteractionType();
        BlockType blockType = event.getBlockType();
        Vector3i pos = event.getTargetBlock();

        if (DEBUG) {
            LOGGER.atInfo().log(
                    "[MGHG|USE] start type=%s blockType=%s pos=%d,%d,%d cancelled=%s",
                    type,
                    blockType != null ? blockType.getId() : "null",
                    pos.x, pos.y, pos.z,
                    event.isCancelled()
            );
        }

        if (type != InteractionType.Primary
                && type != InteractionType.Secondary
                && type != InteractionType.Use
                && type != InteractionType.Pick) {
            if (DEBUG) {
                LOGGER.atInfo().log(
                        "[MGHG|USE] skip: unsupported interaction type=%s pos=%d,%d,%d",
                        type, pos.x, pos.y, pos.z
                );
            }
            return;
        }

        if (blockType == null) {
            if (DEBUG) {
                LOGGER.atInfo().log(
                        "[MGHG|USE] skip: null blockType pos=%d,%d,%d type=%s",
                        pos.x, pos.y, pos.z, type
                );
            }
            return;
        }

        // Solo para bloques colocados (sin FarmingData)
        if (blockType.getFarming() != null) {
            if (DEBUG) {
                LOGGER.atInfo().log(
                        "[MGHG|USE] skip farming block pos=%d,%d,%d type=%s",
                        pos.x, pos.y, pos.z, type
                );
            }
            return;
        }

        World world = store.getExternalData().getWorld();
        MghgCropData cropData = tryGetCropData(world, pos);
        if (cropData == null) {
            if (DEBUG) {
                LOGGER.atInfo().log(
                        "[MGHG|USE] no MGHG data found pos=%d,%d,%d type=%s blockType=%s",
                        pos.x, pos.y, pos.z, type, blockType.getId()
                );
            }
            return;
        }

        InteractionContext context = event.getContext();
        Ref<EntityStore> playerRef = context.getEntity();
        if (playerRef == null || !playerRef.isValid()) {
            return;
        }

        // Cancel vanilla use and give our item with metadata
        event.setCancelled(true);

        ItemStack out = new ItemStack(blockType.getItem().getId(), 1);
        MghgCropMeta meta = MghgCropMeta.fromCropData(
                cropData.getSize(),
                cropData.getClimate().name(),
                cropData.getRarity().name()
        );
        out = out.withMetadata(MghgCropMeta.KEY, meta);

        String resolvedState = MghgCropVisualStateResolver.resolveItemState(cropData);
        if (resolvedState != null && out.getItem().getItemIdForState(resolvedState) != null) {
            out = out.withState(resolvedState);
        }

        Vector3d origin = new Vector3d(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5);
        ItemUtils.interactivelyPickupItem(playerRef, out, origin, commandBuffer);

        if (DEBUG) {
            LOGGER.atInfo().log(
                    "[MGHG|USE] cancel vanilla, give item=%s meta(size=%d climate=%s rarity=%s) pos=%d,%d,%d type=%s",
                    out.getItem().getId(),
                    cropData.getSize(),
                    cropData.getClimate(),
                    cropData.getRarity(),
                    pos.x, pos.y, pos.z,
                    type
            );
        }

        // Remove block on world thread
        long chunkIndex = ChunkUtil.indexChunkFromBlock(pos.x, pos.z);
        world.execute(() -> {
            WorldChunk chunk = world.getChunkIfInMemory(chunkIndex);
            if (chunk != null) {
                chunk.breakBlock(pos.x, pos.y, pos.z);
            }
        });
    }

    @Nullable
    private MghgCropData tryGetCropData(@Nonnull World world, @Nonnull Vector3i blockPosition) {
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

        // 1) entity reference
        Ref<ChunkStore> blockRef = blockComponentChunk.getEntityReference(blockIndexColumn);
        if (blockRef != null && blockRef.isValid()) {
            MghgCropData data = cs.getComponent(blockRef, this.cropDataType);
            if (data != null) {
                if (DEBUG) {
                    LOGGER.atInfo().log(
                            "[MGHG|USE] tryGetCropData: found on entity ref pos=%d,%d,%d",
                            blockPosition.x, blockPosition.y, blockPosition.z
                    );
                }
                return data;
            }
        }

        // 2) holder in BlockComponentChunk
        Holder<ChunkStore> holder = blockComponentChunk.getEntityHolder(blockIndexColumn);
        if (holder != null) {
            MghgCropData data = holder.getComponent(this.cropDataType);
            if (data != null) {
                if (DEBUG) {
                    LOGGER.atInfo().log(
                            "[MGHG|USE] tryGetCropData: found on BlockComponentChunk holder pos=%d,%d,%d",
                            blockPosition.x, blockPosition.y, blockPosition.z
                    );
                }
                return data;
            }
        }

        // 3) holder in WorldChunk state (set via worldChunk.setState)
        WorldChunk worldChunk = cs.getComponent(chunkRef, WorldChunk.getComponentType());
        if (worldChunk != null) {
            Holder<ChunkStore> stateHolder = worldChunk.getBlockComponentHolder(
                    blockPosition.x, blockPosition.y, blockPosition.z
            );
            if (stateHolder != null) {
                MghgCropData data = stateHolder.getComponent(this.cropDataType);
                if (data != null) {
                    if (DEBUG) {
                        LOGGER.atInfo().log(
                                "[MGHG|USE] tryGetCropData: found on WorldChunk holder pos=%d,%d,%d",
                                blockPosition.x, blockPosition.y, blockPosition.z
                        );
                    }
                    return data;
                }
            }
        }

        if (DEBUG) {
            LOGGER.atInfo().log(
                    "[MGHG|USE] tryGetCropData: none found pos=%d,%d,%d",
                    blockPosition.x, blockPosition.y, blockPosition.z
            );
        }

        return null;
    }
}
