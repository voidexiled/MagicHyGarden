package com.voidexiled.magichygarden.features.farming.systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.voidexiled.magichygarden.features.farming.components.MghgCropData;
import com.voidexiled.magichygarden.features.farming.state.ClimateMutation;
import com.voidexiled.magichygarden.features.farming.state.RarityMutation;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Instant;
import java.util.logging.Level;

public final class MghgRehydrateCropDataOnPlaceSystem extends EntityEventSystem<EntityStore, PlaceBlockEvent> {
    private static final HytaleLogger LOGGER = HytaleLogger.getLogger();

    // Cámbialo a false cuando termines de debuguear.
    private static final boolean DEBUG = true;

    private final ComponentType<ChunkStore, MghgCropData> cropDataType;

    public MghgRehydrateCropDataOnPlaceSystem(@Nonnull ComponentType<ChunkStore, MghgCropData> cropDataType) {
        super(PlaceBlockEvent.class);
        this.cropDataType = cropDataType;
    }

    @Override
    public Query<EntityStore> getQuery() {
        // Solo jugadores (evita ruido).
        return PlayerRef.getComponentType();
    }

    @Override
    public void handle(
            int index,
            @Nonnull ArchetypeChunk<EntityStore> chunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull PlaceBlockEvent event
    ) {
        ItemStack item = event.getItemInHand();
        if (item == null) {
            return;
        }

        BsonDocument metadata = item.getMetadata();
        if (metadata == null) {
            return;
        }

        // Tu metadata está bajo la key del KeyedCodec: "MGHG_Crop"
        @Nullable BsonDocument mghgDoc = getDoc(metadata, "MGHG_Crop");
        if (mghgDoc == null) {
            return; // no es uno de tus crops mutados
        }

        int size = getInt(mghgDoc, "Size", 0);
        String climateStr = getString(mghgDoc, "Climate", "NONE");
        String rarityStr = getString(mghgDoc, "Rarity", "NONE");

        ClimateMutation climate = parseClimate(climateStr);
        RarityMutation rarity = parseRarity(rarityStr);

        Vector3i pos = event.getTargetBlock().clone();
        String expectedBlockTypeKey = event.getItemInHand().getBlockKey();

        PlayerRef player = chunk.getComponent(index, PlayerRef.getComponentType());
        String who = (player != null ? player.getUsername() : "?");
        String itemId = item.getItem().getId();

        if (DEBUG) {
            LOGGER.at(Level.INFO).log(
                    "[MGHG|PLACE] pre-place by=%s item=%s blockTypeKey=%s pos=%d,%d,%d meta(size=%d climate=%s rarity=%s)",
                    who, itemId, expectedBlockTypeKey, pos.x, pos.y, pos.z, size, climate, rarity
            );
        }

        World world = store.getExternalData().getWorld();

        // IMPORTANTE:
        // PlaceBlockEvent se dispara ANTES de colocar. Para no pelear con vanilla,
        // aplicamos en el siguiente tick del World thread.
        world.execute(() -> applyToPlacedBlock(world, who, itemId, expectedBlockTypeKey, pos, size, climate, rarity));
    }

    private void applyToPlacedBlock(
            @Nonnull World world,
            @Nonnull String who,
            @Nonnull String itemId,
            @Nonnull String expectedBlockTypeKey,
            @Nonnull Vector3i pos,
            int size,
            @Nonnull ClimateMutation climate,
            @Nonnull RarityMutation rarity
    ) {
        long chunkIndex = ChunkUtil.indexChunkFromBlock(pos.x, pos.z);
        WorldChunk worldChunk = world.getChunkIfInMemory(chunkIndex);

        if (worldChunk == null) {
            if (DEBUG) {
                LOGGER.at(Level.WARNING).log(
                        "[MGHG|PLACE] chunk not in memory pos=%d,%d,%d chunkIndex=%d (by=%s item=%s)",
                        pos.x, pos.y, pos.z, chunkIndex, who, itemId
                );
            }
            return;
        }

        // Debug + validation: qué bloque hay realmente ya colocado
        String actualBlockId = null;
        BlockType actualBlockType = null;
        try {
            actualBlockType = worldChunk.getBlockType(pos.x, pos.y, pos.z);
            actualBlockId = (actualBlockType != null ? actualBlockType.getId() : null);
        } catch (Throwable ignored) {
            // getBlockType existe pero está deprecado; si algo revienta, no tumbamos el sistema.
        }

        if (DEBUG) {
            LOGGER.at(Level.INFO).log(
                    "[MGHG|PLACE] post-place check expected=%s actual=%s pos=%d,%d,%d (by=%s item=%s)",
                    expectedBlockTypeKey, actualBlockId, pos.x, pos.y, pos.z, who, itemId
            );
        }

        if (actualBlockType == null) {
            return;
        }

        boolean matchesExpected =
                expectedBlockTypeKey != null && expectedBlockTypeKey.equals(actualBlockType.getId());
        boolean matchesItem =
                actualBlockType.getItem() != null && itemId.equals(actualBlockType.getItem().getId());

        if (!matchesExpected && !matchesItem) {
            if (DEBUG) {
                LOGGER.at(Level.WARNING).log(
                        "[MGHG|PLACE] skip: expected=%s actual=%s item=%s pos=%d,%d,%d (by=%s)",
                        expectedBlockTypeKey, actualBlockType.getId(), itemId, pos.x, pos.y, pos.z, who
                );
            }
            return;
        }

        ChunkStore chunkStore = world.getChunkStore();
        Store<ChunkStore> chunkStoreStore = chunkStore.getStore();
        Ref<ChunkStore> chunkRef = chunkStore.getChunkReference(chunkIndex);
        BlockComponentChunk blockComponentChunk =
                (chunkRef != null) ? chunkStoreStore.getComponent(chunkRef, BlockComponentChunk.getComponentType()) : null;

        int blockIndex = ChunkUtil.indexBlockInColumn(pos.x, pos.y, pos.z);
        Ref<ChunkStore> blockRef =
                (blockComponentChunk != null) ? blockComponentChunk.getEntityReference(blockIndex) : null;

        if (blockRef != null && blockRef.isValid()) {
            // If entity exists, write directly onto it
            MghgCropData data = chunkStoreStore.ensureAndGetComponent(blockRef, cropDataType);
            data.setSize(size);
            data.setClimate(climate);
            data.setRarity(rarity);
            data.setLastMutationRoll(null);

            if (DEBUG) {
                LOGGER.at(Level.INFO).log(
                        "[MGHG|PLACE] applied MghgCropData to entity pos=%d,%d,%d (by=%s item=%s size=%d climate=%s rarity=%s)",
                        pos.x, pos.y, pos.z, who, itemId, size, climate, rarity
                );
            }
            return;
        }

        // Fallback: holder on block state
        var holder = worldChunk.getBlockComponentHolder(pos.x, pos.y, pos.z);
        boolean hadHolder = (holder != null);

        if (holder == null) {
            holder = ChunkStore.REGISTRY.newHolder();
        }

        if (chunkRef == null) {
            if (DEBUG) {
                LOGGER.at(Level.WARNING).log(
                        "[MGHG|PLACE] missing chunkRef for pos=%d,%d,%d (by=%s item=%s) - cannot persist holder",
                        pos.x, pos.y, pos.z, who, itemId
                );
            }
            return;
        }

        if (holder.getComponent(BlockModule.BlockStateInfo.getComponentType()) == null) {
            holder.putComponent(
                    BlockModule.BlockStateInfo.getComponentType(),
                    new BlockModule.BlockStateInfo(blockIndex, chunkRef)
            );
        }

        MghgCropData data = holder.getComponent(cropDataType);
        if (data == null) {
            data = new MghgCropData();
            holder.putComponent(cropDataType, data);
        }

        data.setSize(size);
        data.setClimate(climate);
        data.setRarity(rarity);
        data.setLastMutationRoll(null);

        // Attach the holder to the block
        worldChunk.setState(pos.x, pos.y, pos.z, holder);

        if (DEBUG) {
            LOGGER.at(Level.INFO).log(
                    "[MGHG|PLACE] applied MghgCropData ok hadHolder=%s pos=%d,%d,%d (by=%s item=%s size=%d climate=%s rarity=%s)",
                    hadHolder, pos.x, pos.y, pos.z, who, itemId, size, climate, rarity
            );
        }
    }

    private static ClimateMutation parseClimate(@Nullable String s) {
        if (s == null) return ClimateMutation.NONE;
        try {
            return ClimateMutation.valueOf(s);
        } catch (IllegalArgumentException ignored) {
            return ClimateMutation.NONE;
        }
    }

    private static RarityMutation parseRarity(@Nullable String s) {
        if (s == null) return RarityMutation.NONE;
        try {
            return RarityMutation.valueOf(s);
        } catch (IllegalArgumentException ignored) {
            return RarityMutation.NONE;
        }
    }

    private static @Nullable BsonDocument getDoc(@Nonnull BsonDocument doc, @Nonnull String key) {
        BsonValue v = doc.get(key);
        return (v != null && v.isDocument()) ? v.asDocument() : null;
    }

    private static int getInt(@Nonnull BsonDocument doc, @Nonnull String key, int def) {
        BsonValue v = doc.get(key);
        if (v == null) return def;
        if (v.isInt32()) return v.asInt32().getValue();
        if (v.isInt64()) return (int) v.asInt64().getValue();
        return def;
    }

    private static @Nonnull String getString(@Nonnull BsonDocument doc, @Nonnull String key, @Nonnull String def) {
        BsonValue v = doc.get(key);
        return (v != null && v.isString()) ? v.asString().getValue() : def;
    }
}
