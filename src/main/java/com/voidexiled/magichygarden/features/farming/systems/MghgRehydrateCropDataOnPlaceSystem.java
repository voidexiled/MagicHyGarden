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
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.voidexiled.magichygarden.features.farming.components.MghgCropData;
import com.voidexiled.magichygarden.features.farming.events.MghgFarmEventScheduler;
import com.voidexiled.magichygarden.features.farming.registry.MghgCropRegistry;
import com.voidexiled.magichygarden.features.farming.state.ClimateMutation;
import com.voidexiled.magichygarden.features.farming.state.LunarMutation;
import com.voidexiled.magichygarden.features.farming.state.RarityMutation;
import com.voidexiled.magichygarden.features.farming.logic.MghgWeightUtil;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Instant;
import java.util.logging.Level;

public final class MghgRehydrateCropDataOnPlaceSystem extends EntityEventSystem<EntityStore, PlaceBlockEvent> {
    private static final HytaleLogger LOGGER = HytaleLogger.getLogger();

    // Cámbialo a true si necesitas trazas de depuración.
    private static final boolean DEBUG = false;

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
        if (event.isCancelled()) {
            return;
        }
        ItemStack item = event.getItemInHand();
        if (item == null) {
            return;
        }

        String itemId = item.getItem() != null ? item.getItem().getId() : null;
        String rawItemId = itemId;
        String baseItemId = normalizeStateAssetId(itemId);
        if (baseItemId != null) {
            itemId = baseItemId;
        }
        if (itemId == null || !MghgCropRegistry.isMghgCropItem(itemId)) {
            return;
        }

        World world = store.getExternalData().getWorld();
        if (world == null) {
            return;
        }
        if (!MghgFarmEventScheduler.isFarmWorld(world)) {
            event.setCancelled(true);
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
        String lunarStr = getString(mghgDoc, "Lunar", "NONE");
        String rarityStr = getString(mghgDoc, "Rarity", "NONE");
        double weight = getDouble(mghgDoc, "WeightGrams", 0.0);

        ClimateMutation climate = parseClimate(climateStr);
        LunarMutation lunar = parseLunar(lunarStr);
        RarityMutation rarity = parseRarity(rarityStr);

        Vector3i pos = event.getTargetBlock().clone();
        String expectedBlockTypeKey = event.getItemInHand().getBlockKey();

        PlayerRef player = chunk.getComponent(index, PlayerRef.getComponentType());
        String who = (player != null ? player.getUsername() : "?");

        if (DEBUG) {
            LOGGER.at(Level.INFO).log(
                    "[MGHG|PLACE] pre-place by=%s item=%s rawItem=%s blockTypeKey=%s pos=%d,%d,%d meta(size=%d climate=%s lunar=%s rarity=%s)",
                    who, itemId, rawItemId, expectedBlockTypeKey, pos.x, pos.y, pos.z, size, climate, lunar, rarity
            );
        }

        // IMPORTANTE:
        // PlaceBlockEvent se dispara ANTES de colocar. Para no pelear con vanilla,
        // aplicamos en el siguiente tick del World thread.
        final String finalItemId = itemId;
        final String finalExpectedBlockTypeKey = expectedBlockTypeKey;
        final Vector3i finalPos = pos;
        world.execute(() -> applyToPlacedBlock(world, who, finalItemId, finalExpectedBlockTypeKey, finalPos, size, climate, lunar, rarity, weight));
    }

    private void applyToPlacedBlock(
            @Nonnull World world,
            @Nonnull String who,
            @Nonnull String itemId,
            @Nonnull String expectedBlockTypeKey,
            @Nonnull Vector3i pos,
            int size,
            @Nonnull ClimateMutation climate,
            @Nonnull LunarMutation lunar,
            @Nonnull RarityMutation rarity,
            double weightGrams
    ) {
        Instant now = Instant.now();
        var timeRes = world.getEntityStore().getStore().getResource(WorldTimeResource.getResourceType());
        if (timeRes != null) {
            now = timeRes.getGameTime();
        }

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
        if (!MghgCropRegistry.isMghgCropBlock(actualBlockType)) {
            if (DEBUG) {
                LOGGER.at(Level.WARNING).log(
                        "[MGHG|PLACE] skip: blockType not MGHG actual=%s pos=%d,%d,%d (by=%s item=%s)",
                        actualBlockType.getId(), pos.x, pos.y, pos.z, who, itemId
                );
            }
            return;
        }

        boolean matchesExpected =
                expectedBlockTypeKey != null && expectedBlockTypeKey.equals(actualBlockType.getId());
        boolean matchesItem =
                actualBlockType.getItem() != null && itemId.equals(actualBlockType.getItem().getId());
        String actualBaseItemId = normalizeStateAssetId(actualBlockType.getId());
        boolean matchesBaseItem =
                actualBaseItemId != null && actualBaseItemId.equals(itemId);

        if (!matchesExpected && !matchesItem && !matchesBaseItem) {
            if (DEBUG) {
                LOGGER.at(Level.WARNING).log(
                        "[MGHG|PLACE] skip: expected=%s actual=%s item=%s base=%s pos=%d,%d,%d (by=%s)",
                        expectedBlockTypeKey, actualBlockType.getId(), itemId, actualBaseItemId, pos.x, pos.y, pos.z, who
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
            data.setLunar(lunar);
            data.setRarity(rarity);
            double finalWeight = weightGrams;
            if (finalWeight <= 0.0) {
                finalWeight = MghgWeightUtil.computeWeightAtMatureGrams(actualBlockType, size);
            }
            data.setWeightGrams(finalWeight);
            if (now != null) {
                data.setPlantTime(now);
            }
            data.setLastRegularRoll(null);
            data.setLastLunarRoll(null);
            data.setLastSpecialRoll(null);
            data.setLastMutationRoll(null);

            if (DEBUG) {
                LOGGER.at(Level.INFO).log(
                        "[MGHG|PLACE] applied MghgCropData to entity pos=%d,%d,%d (by=%s item=%s size=%d climate=%s lunar=%s rarity=%s)",
                        pos.x, pos.y, pos.z, who, itemId, size, climate, lunar, rarity
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
        data.setLunar(lunar);
        data.setRarity(rarity);
        double finalWeight = weightGrams;
        if (finalWeight <= 0.0) {
            finalWeight = MghgWeightUtil.computeWeightAtMatureGrams(actualBlockType, size);
        }
        data.setWeightGrams(finalWeight);
        if (now != null) {
            data.setPlantTime(now);
        }
        data.setLastRegularRoll(null);
        data.setLastLunarRoll(null);
        data.setLastSpecialRoll(null);
        data.setLastMutationRoll(null);

        // Attach the holder to the block
        worldChunk.setState(pos.x, pos.y, pos.z, holder);

        if (DEBUG) {
            LOGGER.at(Level.INFO).log(
                    "[MGHG|PLACE] applied MghgCropData ok hadHolder=%s pos=%d,%d,%d (by=%s item=%s size=%d climate=%s lunar=%s rarity=%s)",
                    hadHolder, pos.x, pos.y, pos.z, who, itemId, size, climate, lunar, rarity
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

    private static LunarMutation parseLunar(@Nullable String s) {
        if (s == null) return LunarMutation.NONE;
        try {
            return LunarMutation.valueOf(s);
        } catch (IllegalArgumentException ignored) {
            return LunarMutation.NONE;
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

    private static double getDouble(@Nonnull BsonDocument doc, @Nonnull String key, double def) {
        BsonValue v = doc.get(key);
        if (v == null) return def;
        if (v.isDouble()) return v.asDouble().getValue();
        if (v.isInt32()) return v.asInt32().getValue();
        if (v.isInt64()) return v.asInt64().getValue();
        return def;
    }

    private static @Nonnull String getString(@Nonnull BsonDocument doc, @Nonnull String key, @Nonnull String def) {
        BsonValue v = doc.get(key);
        return (v != null && v.isString()) ? v.asString().getValue() : def;
    }

    @Nullable
    private static String normalizeStateAssetId(@Nullable String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        int idx = id.indexOf("_State_");
        if (idx <= 0) {
            return null;
        }
        if (id.charAt(0) == '*') {
            return idx > 1 ? id.substring(1, idx) : null;
        }
        return id.substring(0, idx);
    }
}
