package com.voidexiled.magichygarden.features.farming.state;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class MghgMutationContext {
    // Cambia a false cuando termines de debuguear.
    private static final boolean DEBUG = false;

    private final Instant now;
    private final MutationEventType eventType;
    private final int weatherId;
    private final int weatherIdIgnoreSky;
    private final boolean mature;
    private final boolean playerOnline;
    private final Set<String> adjacentBlockIds;
    private final MghgAdjacencyScanner adjacencyScanner;
    private final UUID worldUuid;
    private final int x;
    private final int y;
    private final int z;
    private final int lightSky;
    private final int lightBlock;
    private final int lightBlockIntensity;
    private final int lightRed;
    private final int lightGreen;
    private final int lightBlue;
    private final int currentHour;
    private final double sunlightFactor;
    private final int stageIndex;
    private final int stageCount;
    private final String stageSet;
    private final String soilBlockId;
    private final Map<MghgAdjacentItemRequirement, Boolean> adjacentItemCache = new IdentityHashMap<>();
    private final Map<MghgAdjacentParticleRequirement, Boolean> adjacentParticleCache = new IdentityHashMap<>();

    public MghgMutationContext(
            Instant now,
            MutationEventType eventType,
            int weatherId,
            int weatherIdIgnoreSky,
            boolean mature,
            boolean playerOnline,
            Set<String> adjacentBlockIds,
            MghgAdjacencyScanner adjacencyScanner,
            UUID worldUuid,
            int x, int y, int z,
            int lightSky,
            int lightBlock,
            int lightBlockIntensity,
            int lightRed,
            int lightGreen,
            int lightBlue,
            int currentHour,
            double sunlightFactor,
            int stageIndex,
            int stageCount,
            String stageSet,
            String soilBlockId
    ) {
        this.now = now;
        this.eventType = eventType == null ? MutationEventType.ANY : eventType;
        this.weatherId = weatherId;
        this.weatherIdIgnoreSky = weatherIdIgnoreSky;
        this.mature = mature;
        this.playerOnline = playerOnline;
        if (adjacentBlockIds == null || adjacentBlockIds.isEmpty()) {
            this.adjacentBlockIds = Collections.emptySet();
        } else {
        this.adjacentBlockIds = Collections.unmodifiableSet(new HashSet<>(adjacentBlockIds));
        }
        this.adjacencyScanner = adjacencyScanner;
        this.worldUuid = worldUuid;
        this.x = x;
        this.y = y;
        this.z = z;
        this.lightSky = lightSky;
        this.lightBlock = lightBlock;
        this.lightBlockIntensity = lightBlockIntensity;
        this.lightRed = lightRed;
        this.lightGreen = lightGreen;
        this.lightBlue = lightBlue;
        this.currentHour = currentHour;
        this.sunlightFactor = sunlightFactor;
        this.stageIndex = stageIndex;
        this.stageCount = stageCount;
        this.stageSet = stageSet;
        this.soilBlockId = soilBlockId;
    }

    public Instant getNow() {
        return now;
    }

    public MutationEventType getEventType() {
        return eventType;
    }

    public int getWeatherId() {
        return weatherId;
    }

    public int getWeatherIdIgnoreSky() {
        return weatherIdIgnoreSky;
    }

    public boolean isMature() {
        return mature;
    }

    public boolean isPlayerOnline() {
        return playerOnline;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public UUID getWorldUuid() {
        return worldUuid;
    }

    public int getLightSky() {
        return lightSky;
    }

    public int getLightBlock() {
        return lightBlock;
    }

    public int getLightBlockIntensity() {
        return lightBlockIntensity;
    }

    public int getLightRed() {
        return lightRed;
    }

    public int getLightGreen() {
        return lightGreen;
    }

    public int getLightBlue() {
        return lightBlue;
    }

    public int getCurrentHour() {
        return currentHour;
    }

    public double getSunlightFactor() {
        return sunlightFactor;
    }

    public int getStageIndex() {
        return stageIndex;
    }

    public int getStageCount() {
        return stageCount;
    }

    public String getStageSet() {
        return stageSet;
    }

    public String getSoilBlockId() {
        return soilBlockId;
    }

    public boolean hasAdjacentBlockId(String blockId) {
        if (blockId == null) return false;
        String normalized = MghgBlockIdUtil.normalizeId(blockId);
        return normalized != null && adjacentBlockIds.contains(normalized);
    }

    public boolean hasAnyAdjacent() {
        return !adjacentBlockIds.isEmpty();
    }

    public boolean matchesAdjacentBlocks(String[] ids, AdjacentMatchMode mode) {
        if (ids == null || ids.length == 0) return true;
        if (adjacentBlockIds.isEmpty()) return false;
        AdjacentMatchMode useMode = mode == null ? AdjacentMatchMode.ANY : mode;
        if (useMode == AdjacentMatchMode.ALL) {
            for (String id : ids) {
                if (id == null || id.isBlank()) continue;
                String normalized = MghgBlockIdUtil.normalizeId(id);
                if (normalized != null && !adjacentBlockIds.contains(normalized)) {
                    return false;
                }
            }
            return true;
        }

        for (String id : ids) {
            if (id == null || id.isBlank()) continue;
            String normalized = MghgBlockIdUtil.normalizeId(id);
            if (normalized != null && adjacentBlockIds.contains(normalized)) {
                return true;
            }
        }
        return false;
    }

    public boolean matchesAdjacentItems(MghgAdjacentItemRequirement[] requirements, AdjacentMatchMode mode) {
        if (requirements == null || requirements.length == 0) return true;
        if (adjacencyScanner == null) {
            if (DEBUG) {
                System.out.println("[MGHG|ADJ_CTX] adjacencyScanner=null for pos=" + x + "," + y + "," + z);
            }
            return false;
        }

        AdjacentMatchMode useMode = mode == null ? AdjacentMatchMode.ANY : mode;
        boolean any = false;
        for (MghgAdjacentItemRequirement req : requirements) {
            if (req == null) continue;
            boolean ok = adjacentItemCache.computeIfAbsent(req,
                    key -> adjacencyScanner.matchesRequirement(key, x, y, z));
            if (useMode == AdjacentMatchMode.ALL && !ok) {
                return false;
            }
            if (useMode == AdjacentMatchMode.ANY && ok) {
                return true;
            }
            any |= ok;
        }
        return useMode == AdjacentMatchMode.ALL ? true : any;
    }

    public boolean matchesAdjacentParticles(MghgAdjacentParticleRequirement[] requirements, AdjacentMatchMode mode) {
        if (requirements == null || requirements.length == 0) return true;
        if (adjacencyScanner == null) {
            if (DEBUG) {
                System.out.println("[MGHG|ADJ_CTX] adjacencyScanner=null for pos=" + x + "," + y + "," + z);
            }
            return false;
        }

        AdjacentMatchMode useMode = mode == null ? AdjacentMatchMode.ANY : mode;
        boolean any = false;
        for (MghgAdjacentParticleRequirement req : requirements) {
            if (req == null) continue;
            boolean ok = adjacentParticleCache.computeIfAbsent(req,
                    key -> adjacencyScanner.matchesParticleRequirement(key, x, y, z, worldUuid));
            if (useMode == AdjacentMatchMode.ALL && !ok) {
                return false;
            }
            if (useMode == AdjacentMatchMode.ANY && ok) {
                return true;
            }
            any |= ok;
        }
        return useMode == AdjacentMatchMode.ALL ? true : any;
    }
}
