package com.voidexiled.magichygarden.features.farming.shop;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.voidexiled.magichygarden.features.farming.events.MghgFarmEventScheduler;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcel;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcelAccess;
import com.voidexiled.magichygarden.features.farming.state.MghgBlockIdUtil;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class MghgShopAccessPolicy {
    private static final int MIN_RADIUS = 1;
    private static final int MAX_RADIUS = 16;
    private static final int VERTICAL_SCAN = 2;

    private MghgShopAccessPolicy() {
    }

    public static boolean isConfiguredBenchBlock(@Nullable BlockType blockType) {
        if (blockType == null) {
            return false;
        }
        MghgShopConfig cfg = MghgShopStockManager.getConfig();
        if (cfg == null) {
            return false;
        }
        Set<String> benchIds = normalizeIds(cfg.getBenchBlockIds());
        if (benchIds.isEmpty()) {
            return false;
        }
        return matchBenchId(blockType, benchIds) != null;
    }

    public static @Nullable String validateTransactionContext(
            Store<EntityStore> store,
            Ref<EntityStore> playerEntityRef,
            PlayerRef playerRef,
            World world
    ) {
        MghgShopConfig cfg = MghgShopStockManager.getConfig();
        if (cfg == null) {
            return null;
        }

        if (cfg.isRequireFarmWorldForTransactions() && !MghgFarmEventScheduler.isFarmWorld(world)) {
            return "La shop solo se puede usar en mundos de granja.";
        }

        if (cfg.isRequireParcelAccessForTransactions()) {
            MghgParcel parcel = MghgParcelAccess.resolveParcel(world);
            if (parcel == null) {
                return "No se encontro parcela para este mundo de granja.";
            }

            UUID playerId = playerRef.getUuid();
            if (!MghgParcelAccess.canBuild(parcel, playerId)) {
                return "No tienes permisos de parcela para usar la shop aqui.";
            }

            Vector3d playerPos = resolvePlayerPos(store, playerEntityRef);
            if (playerPos == null) {
                return "No pude resolver posicion del jugador para validar parcela.";
            }
            Vector3i playerBlock = toBlockPos(playerPos);
            if (!MghgParcelAccess.isInsideHorizontal(parcel.getBounds(), playerBlock)) {
                return "Debes estar dentro de tu parcela para usar la shop.";
            }
        }

        if (cfg.isRequireBenchProximityForTransactions()) {
            Set<String> benchIds = normalizeIds(cfg.getBenchBlockIds());
            if (benchIds.isEmpty()) {
                return "Shop mal configurada: BenchBlockIds esta vacio.";
            }

            Vector3d playerPos = resolvePlayerPos(store, playerEntityRef);
            if (playerPos == null) {
                return "No pude resolver posicion del jugador para validar bench.";
            }

            int radius = clamp(cfg.getBenchSearchRadius(), MIN_RADIUS, MAX_RADIUS);
            BenchProximity proximity = findNearestBench(world, playerPos, benchIds, radius);
            if (!proximity.found()) {
                return String.format(
                        Locale.ROOT,
                        "Debes estar cerca de un bench valido. Radio=%d | benches=%s",
                        radius,
                        summarizeBenchIds(benchIds)
                );
            }
        }

        return null;
    }

    private static @Nullable Vector3d resolvePlayerPos(Store<EntityStore> store, Ref<EntityStore> playerEntityRef) {
        TransformComponent transform = store.getComponent(playerEntityRef, TransformComponent.getComponentType());
        if (transform == null) {
            return null;
        }
        return transform.getPosition();
    }

    private static Vector3i toBlockPos(Vector3d pos) {
        return new Vector3i(
                (int) Math.floor(pos.x),
                (int) Math.floor(pos.y),
                (int) Math.floor(pos.z)
        );
    }

    private static BenchProximity findNearestBench(World world, Vector3d playerPos, Set<String> benchIds, int radius) {
        ChunkStore chunkStore = world.getChunkStore();
        if (chunkStore == null) {
            return BenchProximity.notFound();
        }
        Store<ChunkStore> store = chunkStore.getStore();
        if (store == null) {
            return BenchProximity.notFound();
        }

        int px = (int) Math.floor(playerPos.x);
        int py = (int) Math.floor(playerPos.y);
        int pz = (int) Math.floor(playerPos.z);

        int minX = px - radius;
        int maxX = px + radius;
        int minY = Math.max(0, py - VERTICAL_SCAN);
        int maxY = Math.min(319, py + VERTICAL_SCAN);
        int minZ = pz - radius;
        int maxZ = pz + radius;

        HashMap<Long, Ref<ChunkStore>> chunkRefs = new HashMap<>();
        HashMap<Long, BlockChunk> chunks = new HashMap<>();
        String bestBenchId = null;

        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    BlockChunk chunk = resolveChunk(store, chunkStore, chunkRefs, chunks, ChunkUtil.indexChunkFromBlock(x, z));
                    if (chunk == null) {
                        continue;
                    }

                    int blockId = chunk.getBlock(x, y, z);
                    if (blockId == 0) {
                        continue;
                    }

                    BlockType type = BlockType.getAssetMap().getAsset(blockId);
                    String matchedBenchId = matchBenchId(type, benchIds);
                    if (matchedBenchId != null) {
                        bestBenchId = matchedBenchId;
                    }
                }
            }
        }

        if (bestBenchId == null) {
            return BenchProximity.notFound();
        }
        return BenchProximity.present();
    }

    private static @Nullable BlockChunk resolveChunk(
            Store<ChunkStore> store,
            ChunkStore chunkStore,
            HashMap<Long, Ref<ChunkStore>> chunkRefs,
            HashMap<Long, BlockChunk> chunks,
            long chunkIndex
    ) {
        BlockChunk cached = chunks.get(chunkIndex);
        if (cached != null) {
            return cached;
        }

        Ref<ChunkStore> ref = chunkRefs.get(chunkIndex);
        if (ref == null) {
            ref = chunkStore.getChunkReference(chunkIndex);
            if (ref != null) {
                chunkRefs.put(chunkIndex, ref);
            }
        }
        if (ref == null || !ref.isValid()) {
            return null;
        }

        BlockChunk chunk = store.getComponent(ref, BlockChunk.getComponentType());
        if (chunk != null) {
            chunks.put(chunkIndex, chunk);
        }
        return chunk;
    }

    private static @Nullable String matchBenchId(@Nullable BlockType type, Set<String> benchIds) {
        if (type == null) {
            return null;
        }

        String raw = MghgBlockIdUtil.normalizeId(type.getId());
        if (raw != null && benchIds.contains(raw)) {
            return raw;
        }

        BlockType base = MghgBlockIdUtil.resolveBaseBlockType(type);
        if (base != null && base != type) {
            String baseId = MghgBlockIdUtil.normalizeId(base.getId());
            if (baseId != null && benchIds.contains(baseId)) {
                return baseId;
            }
        }

        return null;
    }

    private static Set<String> normalizeIds(@Nullable String[] values) {
        HashSet<String> ids = new HashSet<>();
        if (values == null) {
            return ids;
        }
        for (String value : values) {
            String normalized = MghgBlockIdUtil.normalizeId(value);
            if (normalized != null) {
                ids.add(normalized);
            }
        }
        return ids;
    }

    private static int clamp(int value, int min, int max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    private static String summarizeBenchIds(Set<String> benchIds) {
        if (benchIds == null || benchIds.isEmpty()) {
            return "-";
        }
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (String id : benchIds) {
            if (id == null || id.isBlank()) {
                continue;
            }
            if (count > 0) {
                sb.append(", ");
            }
            sb.append(id);
            count++;
            if (count >= 5) {
                if (benchIds.size() > count) {
                    sb.append(", ...");
                }
                break;
            }
        }
        return sb.length() == 0 ? "-" : sb.toString();
    }

    private static final class BenchProximity {
        private final boolean found;

        private BenchProximity(boolean found) {
            this.found = found;
        }

        private static BenchProximity notFound() {
            return new BenchProximity(false);
        }

        private static BenchProximity present() {
            return new BenchProximity(true);
        }

        private boolean found() {
            return found;
        }
    }
}
