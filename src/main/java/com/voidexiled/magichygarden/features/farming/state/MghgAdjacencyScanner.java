package com.voidexiled.magichygarden.features.farming.state;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelParticle;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.component.Store;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import com.voidexiled.magichygarden.features.farming.state.MghgParticleTracker;

import javax.annotation.Nullable;
import java.util.Set;

public final class MghgAdjacencyScanner {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    // Cambia a false cuando termines de debuguear.
    private static final boolean DEBUG = false;

    private final Store<ChunkStore> store;
    private final ChunkStore chunkStore;
    private final Long2ObjectOpenHashMap<BlockChunk> blockChunks = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectOpenHashMap<Ref<ChunkStore>> chunkRefs = new Long2ObjectOpenHashMap<>();

    public MghgAdjacencyScanner(Store<ChunkStore> store, ChunkStore chunkStore) {
        this.store = store;
        this.chunkStore = chunkStore;
    }

    public boolean matchesRequirement(MghgAdjacentItemRequirement req, int originX, int originY, int originZ) {
        if (req == null) return false;
        Set<String> matchIds = req.getResolvedIds();
        if (matchIds == null || matchIds.isEmpty()) return false;

        int radiusX = Math.max(0, req.getRadiusX());
        int radiusY = Math.max(0, req.getRadiusY());
        int radiusZ = Math.max(0, req.getRadiusZ());
        boolean useCount = req.hasCountConstraint();
        int minCount = req.getMinCount();
        int maxCount = req.getMaxCount();
        int found = 0;

        int centerX = originX + req.getOffsetX();
        int centerY = originY + req.getOffsetY();
        int centerZ = originZ + req.getOffsetZ();

        int minX = centerX - radiusX;
        int maxX = centerX + radiusX;
        int minY = Math.max(0, centerY - radiusY);
        int maxY = Math.min(319, centerY + radiusY);
        int minZ = centerZ - radiusZ;
        int maxZ = centerZ + radiusZ;

        if (DEBUG) {
            LOGGER.atInfo().log(
                    "[MGHG|ADJ] scan ids=%s center=%d,%d,%d radius=%d,%d,%d",
                    matchIds, centerX, centerY, centerZ, radiusX, radiusY, radiusZ
            );
        }

        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    int blockId = getBlockIdAt(x, y, z);
                    if (blockId == 0) continue;
                    BlockType type = BlockType.getAssetMap().getAsset(blockId);
                    if (type == null) continue;

                    String rawId = MghgBlockIdUtil.normalizeId(type.getId());
                    if (rawId != null && matchIds.contains(rawId)) {
                        if (DEBUG) {
                            LOGGER.atInfo().log(
                                    "[MGHG|ADJ] match raw=%s at %d,%d,%d",
                                    rawId, x, y, z
                            );
                        }
                        if (!useCount) return true;
                        found++;
                        if (maxCount >= 0 && found > maxCount) return false;
                        if (maxCount < 0 && found >= minCount) return true;
                        continue;
                    }

                    BlockType base = MghgBlockIdUtil.resolveBaseBlockType(type);
                    if (base != null) {
                        String baseId = MghgBlockIdUtil.normalizeId(base.getId());
                        if (baseId != null && matchIds.contains(baseId)) {
                            if (DEBUG) {
                                LOGGER.atInfo().log(
                                        "[MGHG|ADJ] match base=%s at %d,%d,%d",
                                        baseId, x, y, z
                                );
                            }
                            if (!useCount) return true;
                            found++;
                            if (maxCount >= 0 && found > maxCount) return false;
                            if (maxCount < 0 && found >= minCount) return true;
                        }
                    }
                }
            }
        }

        if (DEBUG) {
            LOGGER.atInfo().log("[MGHG|ADJ] no match found");
        }
        if (!useCount) return false;
        return found >= minCount && (maxCount < 0 || found <= maxCount);
    }

    public boolean matchesParticleRequirement(MghgAdjacentParticleRequirement req, int originX, int originY, int originZ, @Nullable java.util.UUID worldUuid) {
        if (req == null) return false;
        boolean useRuntime = req.isUseRuntimeParticles();
        boolean useBlock = req.isUseBlockParticles();

        if (useRuntime) {
            boolean runtimeMatch = MghgParticleTracker.matches(req, worldUuid, originX, originY, originZ);
            if (runtimeMatch) {
                return true;
            }
        }

        if (!useBlock) return false;
        Set<String> matchIds = req.getResolvedIds();
        // Block-asset scanning only makes sense with explicit ids.
        if (matchIds == null || matchIds.isEmpty()) return false;

        int radiusX = Math.max(0, req.getRadiusX());
        int radiusY = Math.max(0, req.getRadiusY());
        int radiusZ = Math.max(0, req.getRadiusZ());
        boolean useCount = req.hasCountConstraint();
        int minCount = req.getMinCount();
        int maxCount = req.getMaxCount();
        int found = 0;

        int centerX = originX + req.getOffsetX();
        int centerY = originY + req.getOffsetY();
        int centerZ = originZ + req.getOffsetZ();

        int minX = centerX - radiusX;
        int maxX = centerX + radiusX;
        int minY = Math.max(0, centerY - radiusY);
        int maxY = Math.min(319, centerY + radiusY);
        int minZ = centerZ - radiusZ;
        int maxZ = centerZ + radiusZ;

        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    int blockId = getBlockIdAt(x, y, z);
                    if (blockId == 0) continue;
                    BlockType type = BlockType.getAssetMap().getAsset(blockId);
                    if (type == null) continue;

                    if (matchesParticleIds(type, req)) {
                        if (!useCount) return true;
                        found++;
                        if (maxCount >= 0 && found > maxCount) return false;
                        if (maxCount < 0 && found >= minCount) return true;
                    }

                    BlockType base = MghgBlockIdUtil.resolveBaseBlockType(type);
                    if (base != null && base != type && matchesParticleIds(base, req)) {
                        if (!useCount) return true;
                        found++;
                        if (maxCount >= 0 && found > maxCount) return false;
                        if (maxCount < 0 && found >= minCount) return true;
                    }
                }
            }
        }

        if (!useCount) return false;
        return found >= minCount && (maxCount < 0 || found <= maxCount);
    }

    private int getBlockIdAt(int x, int y, int z) {
        if (y < 0 || y >= 320) return 0;
        long chunkIndex = ChunkUtil.indexChunkFromBlock(x, z);
        BlockChunk chunk = getBlockChunk(chunkIndex);
        if (chunk == null) return 0;
        return chunk.getBlock(x, y, z);
    }

    private @Nullable BlockChunk getBlockChunk(long chunkIndex) {
        BlockChunk cached = blockChunks.get(chunkIndex);
        if (cached != null) return cached;

        Ref<ChunkStore> ref = chunkRefs.get(chunkIndex);
        if (ref == null) {
            ref = chunkStore.getChunkReference(chunkIndex);
            if (ref != null) {
                chunkRefs.put(chunkIndex, ref);
            }
        }
        if (ref == null || !ref.isValid()) return null;

        BlockChunk chunk = store.getComponent(ref, BlockChunk.getComponentType());
        if (chunk != null) {
            blockChunks.put(chunkIndex, chunk);
        }
        return chunk;
    }

    private static boolean matchesParticleIds(BlockType type, MghgAdjacentParticleRequirement req) {
        String setId = type.getBlockParticleSetId();
        if (setId != null && req.matchesId(setId)) {
            return true;
        }

        ModelParticle[] particles = type.getParticles();
        if (particles != null) {
            for (ModelParticle particle : particles) {
                if (particle == null) continue;
                String systemId = particle.getSystemId();
                if (systemId != null && req.matchesId(systemId)) {
                    return true;
                }
            }
        }

        return false;
    }
}
