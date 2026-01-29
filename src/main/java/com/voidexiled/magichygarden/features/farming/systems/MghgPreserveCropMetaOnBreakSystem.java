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
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.HarvestingDropType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.ItemUtils;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.BlockHarvestUtils;
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

public final class MghgPreserveCropMetaOnBreakSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final boolean DEBUG = true;

    private final ComponentType<ChunkStore, MghgCropData> cropDataType;

    public MghgPreserveCropMetaOnBreakSystem(@Nonnull ComponentType<ChunkStore, MghgCropData> cropDataType) {
        super(BreakBlockEvent.class);
        this.cropDataType = cropDataType;
    }

    @Override
    public @Nullable Query<EntityStore> getQuery() {
        // Solo jugadores (evita índices inválidos y eventos no-player)
        return PlayerRef.getComponentType();
    }

    @Override
    public void handle(
            int index,
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull BreakBlockEvent event
    ) {
        if (event.isCancelled()) return;

        final BlockType blockType = event.getBlockType();
        final Vector3i pos = event.getTargetBlock();
        PlayerRef playerRef = archetypeChunk.getComponent(index, PlayerRef.getComponentType());
        String who = (playerRef != null ? playerRef.getUsername() : "?");

        if (DEBUG) {
            LOGGER.atInfo().log(
                    "[MGHG|BREAK] start by=%s blockType=%s pos=%d,%d,%d farming=%s",
                    who,
                    blockType != null ? blockType.getId() : "null",
                    pos.x, pos.y, pos.z,
                    (blockType != null && blockType.getFarming() != null)
            );
        }

        final World world = store.getExternalData().getWorld();

        // Si el jugador rompió el bloque de soporte, intenta preservar el bloque encima (decorativo).
        // Esto se ejecuta aunque el bloque roto NO tenga MGHG data.
        Ref<EntityStore> breakerRef = archetypeChunk.getReferenceTo(index);
        if (breakerRef != null && breakerRef.isValid()) {
            if (handleSupportBreak(world, breakerRef, commandBuffer, blockType, pos.x, pos.y, pos.z)) {
                event.setCancelled(true);
                return;
            }
        }

        // IMPORTANTE: esto es para el caso “bloque colocado” (sin FarmingData).
        // Si lo aplicas a farming blocks también, podrías cambiar drops vanilla (semillas, etc).
        if (blockType.getFarming() != null) {
            if (DEBUG) {
                LOGGER.atInfo().log(
                        "[MGHG|BREAK] skip farming block pos=%d,%d,%d",
                        pos.x, pos.y, pos.z
                );
            }
            return;
        }

        // 1) leer MGHG data del bloque (ref o holder)
        final MghgCropData cropData = tryGetCropData(world, pos);
        if (cropData == null) {
            if (DEBUG) {
                LOGGER.atInfo().log(
                        "[MGHG|BREAK] no MGHG data found pos=%d,%d,%d",
                        pos.x, pos.y, pos.z
                );
            }
            return; // no es un bloque MGHG con data -> deja vanilla
        }

        // 2) construir ítem a devolver
        final Item item = blockType.getItem();
        if (item == null) {
            return; // sin item asociado -> deja vanilla
        }

        ItemStack out = new ItemStack(item.getId(), 1);

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

        // 3) cancelar break vanilla + entregar nuestro item
        event.setCancelled(true);

        if (breakerRef == null || !breakerRef.isValid()) {
            return;
        }

        Vector3d origin = new Vector3d(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5);

        LOGGER.atInfo().log(
                "[MGHG|BREAK] cancel vanilla, give item={} meta(size={} climate={} rarity={}) pos={},{},{}",
                out.getItem().getId(),
                cropData.getSize(),
                cropData.getClimate(),
                cropData.getRarity(),
                pos.x, pos.y, pos.z
        );

        ItemUtils.interactivelyPickupItem(breakerRef, out, origin, commandBuffer);

        // 4) romper el bloque manualmente (en world thread)
        long chunkIndex = ChunkUtil.indexChunkFromBlock(pos.x, pos.z);
        world.execute(() -> {
            WorldChunk chunk = world.getChunkIfInMemory(chunkIndex);
            if (chunk == null) {
                LOGGER.atWarning().log(
                        "[MGHG|BREAK] chunk not in memory chunkIndex={} pos={},{},{}",
                        chunkIndex, pos.x, pos.y, pos.z
                );
                return;
            }

            chunk.breakBlock(pos.x, pos.y, pos.z);
        });

        // Nota: el bloque de soporte ya se manejó arriba; no repetir aquí.
    }

    private boolean handleSupportBreak(
            @Nonnull World world,
            @Nonnull Ref<EntityStore> breakerRef,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull BlockType supportBlockType,
            int x,
            int y,
            int z
    ) {
        // Si arriba no hay crop con data, no hacemos nada.
        long chunkIndex = ChunkUtil.indexChunkFromBlock(x, z);
        WorldChunk worldChunk = world.getChunkIfInMemory(chunkIndex);
        if (worldChunk == null) {
            return false;
        }

        BlockType aboveBlockType = worldChunk.getBlockType(x, y + 1, z);
        if (aboveBlockType == null) {
            return false;
        }

        MghgCropData cropData = tryGetCropData(world, new Vector3i(x, y + 1, z));
        if (cropData == null) {
            return false;
        }

        // 1) Drop del crop (con metadata)
        Vector3d cropOrigin = new Vector3d(x + 0.5, (y + 1) + 0.5, z + 0.5);
        HarvestingDropType harvest =
                aboveBlockType.getGathering() != null ? aboveBlockType.getGathering().getHarvest() : null;
        String itemId = harvest != null ? harvest.getItemId() : null;
        String dropListId = harvest != null ? harvest.getDropListId() : null;
        if (harvest != null && (itemId != null || dropListId != null)) {
            for (ItemStack stack : BlockHarvestUtils.getDrops(aboveBlockType, 1, itemId, dropListId)) {
                ItemStack out = stack;

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

                ItemUtils.interactivelyPickupItem(breakerRef, out, cropOrigin, commandBuffer);
            }
        } else {
            Item item = aboveBlockType.getItem();
            if (item == null) {
                return false;
            }

            ItemStack out = new ItemStack(item.getId(), 1);
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

            ItemUtils.interactivelyPickupItem(breakerRef, out, cropOrigin, commandBuffer);
        }

        // 2) Limpia holder/estado y elimina el crop sin drops vanilla
        ChunkStore chunkStore = world.getChunkStore();
        Store<ChunkStore> cs = chunkStore.getStore();
        Ref<ChunkStore> chunkRef = chunkStore.getChunkReference(chunkIndex);
        if (chunkRef != null && chunkRef.isValid()) {
            BlockComponentChunk componentChunk = cs.getComponent(chunkRef, BlockComponentChunk.getComponentType());
            if (componentChunk != null) {
                int blockIndexColumn = ChunkUtil.indexBlockInColumn(x, y + 1, z);
                componentChunk.removeEntityHolder(blockIndexColumn);
            }
        }

        world.execute(() -> {
            WorldChunk chunk = world.getChunkIfInMemory(chunkIndex);
            if (chunk == null) return;

            try {
                chunk.setState(
                        x, y + 1, z,
                        (Holder<ChunkStore>) null
                );
            } catch (Throwable ignored) {
                // ignore if not supported
            }

            BlockType emptyType = BlockType.getAssetMap().getAsset("Empty");
            int emptyId = emptyType != null ? BlockType.getAssetMap().getIndex(emptyType.getId()) : 0;
            try {
                chunk.setBlock(x, y + 1, z, emptyId, emptyType, 0, 0, 2);
            } catch (Throwable ignored) {
                chunk.breakBlock(x, y + 1, z);
            }

            // 3) Elimina el bloque de soporte manualmente (sin evento vanilla)
            try {
                chunk.setBlock(x, y, z, emptyId, emptyType, 0, 0, 2);
            } catch (Throwable ignored) {
                chunk.breakBlock(x, y, z);
            }
        });

        // 4) Drop del bloque de soporte (vanilla-ish)
        Vector3d supportOrigin = new Vector3d(x + 0.5, y + 0.5, z + 0.5);
        HarvestingDropType supportHarvest =
                supportBlockType.getGathering() != null ? supportBlockType.getGathering().getHarvest() : null;
        if (supportHarvest != null) {
            String supportItemId = supportHarvest.getItemId();
            String supportDropListId = supportHarvest.getDropListId();
            for (ItemStack stack : BlockHarvestUtils.getDrops(supportBlockType, 1, supportItemId, supportDropListId)) {
                ItemUtils.interactivelyPickupItem(breakerRef, stack, supportOrigin, commandBuffer);
            }
        } else {
            Item supportItem = supportBlockType.getItem();
            if (supportItem != null) {
                ItemUtils.interactivelyPickupItem(
                        breakerRef,
                        new ItemStack(supportItem.getId(), 1),
                        supportOrigin,
                        commandBuffer
                );
            }
        }

        return true;
    }

    @Nullable
    private MghgCropData tryGetCropData(@Nonnull World world, @Nonnull Vector3i blockPosition) {
        ChunkStore chunkStore = world.getChunkStore();
        Store<ChunkStore> cs = chunkStore.getStore();

        long chunkIndex = ChunkUtil.indexChunkFromBlock(blockPosition.x, blockPosition.z);
        Ref<ChunkStore> chunkRef = chunkStore.getChunkReference(chunkIndex);
        if (chunkRef == null || !chunkRef.isValid()) {
            if (DEBUG) {
                LOGGER.atInfo().log(
                        "[MGHG|BREAK] tryGetCropData: invalid chunkRef pos=%d,%d,%d",
                        blockPosition.x, blockPosition.y, blockPosition.z
                );
            }
            return null;
        }

        BlockComponentChunk blockComponentChunk = cs.getComponent(chunkRef, BlockComponentChunk.getComponentType());
        if (blockComponentChunk == null) {
            if (DEBUG) {
                LOGGER.atInfo().log(
                        "[MGHG|BREAK] tryGetCropData: no BlockComponentChunk pos=%d,%d,%d",
                        blockPosition.x, blockPosition.y, blockPosition.z
                );
            }
            return null;
        }

        int blockIndexColumn = ChunkUtil.indexBlockInColumn(blockPosition.x, blockPosition.y, blockPosition.z);

        // 1) entity reference (rehidratado)
        Ref<ChunkStore> blockRef = blockComponentChunk.getEntityReference(blockIndexColumn);
        if (blockRef != null && blockRef.isValid()) {
            MghgCropData data = cs.getComponent(blockRef, this.cropDataType);
            if (data != null) {
                if (DEBUG) {
                    LOGGER.atInfo().log(
                            "[MGHG|BREAK] tryGetCropData: found on entity ref pos=%d,%d,%d",
                            blockPosition.x, blockPosition.y, blockPosition.z
                    );
                }
                return data;
            }
        }

        // 2) holder persistente (aún no rehidratado)
        Holder<ChunkStore> holder = blockComponentChunk.getEntityHolder(blockIndexColumn);
        if (holder != null) {
            MghgCropData data = holder.getComponent(this.cropDataType);
            if (data != null) {
                if (DEBUG) {
                    LOGGER.atInfo().log(
                            "[MGHG|BREAK] tryGetCropData: found on BlockComponentChunk holder pos=%d,%d,%d",
                            blockPosition.x, blockPosition.y, blockPosition.z
                    );
                }
                return data;
            }
        }

        // 3) worldChunk holder (set via worldChunk.setState)
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
                                "[MGHG|BREAK] tryGetCropData: found on WorldChunk holder pos=%d,%d,%d",
                                blockPosition.x, blockPosition.y, blockPosition.z
                        );
                    }
                    return data;
                }
            }
        }

        if (DEBUG) {
            LOGGER.atInfo().log(
                    "[MGHG|BREAK] tryGetCropData: none found pos=%d,%d,%d",
                    blockPosition.x, blockPosition.y, blockPosition.z
            );
        }

        return null;
    }
}
