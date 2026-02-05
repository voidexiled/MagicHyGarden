package com.voidexiled.magichygarden.features.farming.systems;

import com.hypixel.hytale.assetstore.AssetRegistry;
import com.hypixel.hytale.builtin.buildertools.tooloperations.transform.Translate;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.packets.interface_.Notification;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.AssetRegistryLoader;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.i18n.generator.TranslationMap;
import com.hypixel.hytale.server.core.modules.i18n.parser.LangFileParser;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.AssetUtil;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import com.voidexiled.magichygarden.features.farming.components.MghgCropData;
import com.voidexiled.magichygarden.features.farming.items.MghgCropMeta;
import com.voidexiled.magichygarden.features.farming.logic.MghgCropDataAccess;
import com.voidexiled.magichygarden.features.farming.logic.MghgItemDropUtil;
import com.voidexiled.magichygarden.features.farming.logic.MghgSupportDropMetaCache;
import com.voidexiled.magichygarden.features.farming.logic.MghgWeightUtil;
import com.voidexiled.magichygarden.features.farming.registry.MghgCropRegistry;
import com.voidexiled.magichygarden.features.farming.visuals.MghgCropVisualStateResolver;
import com.voidexiled.magichygarden.utils.NotificationUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class MghgPreserveCropMetaOnBreakSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final boolean DEBUG = false;

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
        // Always clear any visual overlay for this position to avoid ghost visuals.
        PlayerRef playerRef = archetypeChunk.getComponent(index, PlayerRef.getComponentType());
        String who = (playerRef != null ? playerRef.getUsername() : "?");
        boolean isMghg = blockType != null && MghgCropRegistry.isMghgCropBlock(blockType);

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

        // Si el jugador rompió el bloque de soporte, intentamos preservar el crop encima y mantener vanilla feel.
        Ref<EntityStore> breakerRef = archetypeChunk.getReferenceTo(index);
        if (breakerRef != null && breakerRef.isValid()) {
            handleSupportBreak(playerRef, world, pos.x, pos.y, pos.z);
        }

        if (!isMghg) {
            return;
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
        final MghgCropData cropData = MghgCropDataAccess.tryGetCropData(world, pos);
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
        String itemId = null;
        var def = MghgCropRegistry.getDefinition(blockType);
        if (def != null && def.getItemId() != null && !def.getItemId().isBlank()) {
            itemId = def.getItemId();
        } else if (blockType.getItem() != null) {
            itemId = blockType.getItem().getId();
        }
        if (itemId == null) {
            return; // sin item asociado -> deja vanilla
        }

        ItemStack out = new ItemStack(itemId, 1);

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

        // 3) cancelar break vanilla + entregar nuestro item
        event.setCancelled(true);

        if (breakerRef == null || !breakerRef.isValid()) {
            return;
        }

        LOGGER.atInfo().log(
                "[MGHG|BREAK] cancel vanilla, give item={} meta(size={} climate={} rarity={}) pos={},{},{}",
                out.getItem().getId(),
                cropData.getSize(),
                cropData.getClimate(),
                cropData.getRarity(),
                pos.x, pos.y, pos.z
        );

        Vector3d origin = new Vector3d(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5);
        MghgItemDropUtil.dropAt(breakerRef, out, origin, commandBuffer);

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
            @Nonnull PlayerRef playerRef,
            @Nonnull World world,
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

        BlockType cropBlockType = worldChunk.getBlockType(x, y + 1, z);
        if (cropBlockType == null) {
            return false;
        }
        if (!MghgCropRegistry.isMghgCropBlock(cropBlockType)) {
            return false;
        }

        MghgCropData cropData = MghgCropDataAccess.tryGetCropData(world, new Vector3i(x, y + 1, z));
        if (cropData == null) {
            return false;
        }

        String expectedItemId = resolvePhysicsDropItemId(cropBlockType);
        MghgSupportDropMetaCache.queue(x, y + 1, z, cropData, expectedItemId);
        // Resolve Notifications

        LOGGER.atInfo().log("MghgPreserveCropMetaOnBreakSystem: sending support break notification for cropBlockType=%s at pos=%d,%d,%d, itemId=%s",cropBlockType.getId(), x, y + 1, z, expectedItemId);

        NotificationUtils.sendNotification(playerRef,
                cropBlockType.getId(),
                cropData,
                new ItemStack(cropBlockType.getId(), 1).toPacket());
        return true;
    }

    @Nullable
    private String resolvePhysicsDropItemId(@Nonnull BlockType cropBlockType) {
        if (cropBlockType.getGathering() == null) {
            return null;
        }

        if (cropBlockType.getGathering().getPhysics() != null) {
            String itemId = cropBlockType.getGathering().getPhysics().getItemId();
            if (itemId != null) {
                return itemId;
            }
        }
        if (cropBlockType.getGathering().getBreaking() != null) {
            String itemId = cropBlockType.getGathering().getBreaking().getItemId();
            if (itemId != null) {
                return itemId;
            }
        }
        if (cropBlockType.getGathering().getSoft() != null) {
            String itemId = cropBlockType.getGathering().getSoft().getItemId();
            if (itemId != null) {
                return itemId;
            }
        }
        if (cropBlockType.getGathering().getHarvest() != null) {
            return cropBlockType.getGathering().getHarvest().getItemId();
        }

        return null;
    }

    // tryGetCropData moved to MghgCropDataAccess
}
