package com.voidexiled.magichygarden.features.farming.state;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.BlockParticleEvent;
import com.hypixel.hytale.protocol.Position;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.world.SpawnBlockParticleSystem;
import com.hypixel.hytale.protocol.packets.world.SpawnParticleSystem;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.io.adapter.PacketAdapters;
import com.hypixel.hytale.server.core.io.adapter.PacketFilter;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks runtime particle packets sent to clients. This captures all particle
 * systems visible to players (including vanilla), without needing core hooks.
 */
public final class MghgParticleTracker {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final boolean DEBUG = false;

    private static final long GLOBAL_MAX_AGE_MS = 30_000L;
    private static final long DEFAULT_MAX_AGE_MS = 2_000L;
    private static final int MAX_EVENTS_PER_WORLD = 4000;

    private static final Map<UUID, ArrayDeque<ParticleEvent>> EVENTS = new ConcurrentHashMap<>();
    private static PacketFilter outboundFilter;

    private MghgParticleTracker() {}

    public static synchronized void start() {
        if (outboundFilter != null) return;
        outboundFilter = PacketAdapters.registerOutbound((PlayerRef playerRef, Packet packet) -> {
            handleOutbound(playerRef, packet);
        });
    }

    public static synchronized void stop() {
        if (outboundFilter != null) {
            PacketAdapters.deregisterOutbound(outboundFilter);
            outboundFilter = null;
        }
        EVENTS.clear();
    }

    public static boolean matches(
            MghgAdjacentParticleRequirement req,
            @Nullable UUID worldUuid,
            int originX,
            int originY,
            int originZ
    ) {
        if (req == null || worldUuid == null) return false;
        if (!req.isUseRuntimeParticles()) return false;

        ArrayDeque<ParticleEvent> deque = EVENTS.get(worldUuid);
        if (deque == null || deque.isEmpty()) return false;

        int radiusX = Math.max(0, req.getRadiusX());
        int radiusY = Math.max(0, req.getRadiusY());
        int radiusZ = Math.max(0, req.getRadiusZ());
        boolean useCount = req.hasCountConstraint();
        int minCount = req.getMinCount();
        int maxCount = req.getMaxCount();
        boolean anyParticle = req.isAnyParticle();
        long maxAgeMs = Math.min(req.getMaxAgeMillis(DEFAULT_MAX_AGE_MS), GLOBAL_MAX_AGE_MS);
        long now = System.currentTimeMillis();

        int centerX = originX + req.getOffsetX();
        int centerY = originY + req.getOffsetY();
        int centerZ = originZ + req.getOffsetZ();

        double minX = centerX - radiusX;
        double maxX = centerX + radiusX;
        double minY = Math.max(0, centerY - radiusY);
        double maxY = Math.min(319, centerY + radiusY);
        double minZ = centerZ - radiusZ;
        double maxZ = centerZ + radiusZ;

        int found = 0;
        synchronized (deque) {
            for (ParticleEvent event : deque) {
                if (event == null) continue;
                if (now - event.timeMs > maxAgeMs) {
                    continue;
                }
                if (event.x < minX || event.x > maxX) continue;
                if (event.y < minY || event.y > maxY) continue;
                if (event.z < minZ || event.z > maxZ) continue;
                if (!anyParticle && !req.matchesId(event.id)) {
                    continue;
                }

                if (!useCount) return true;
                found++;
                if (maxCount >= 0 && found > maxCount) return false;
                if (maxCount < 0 && found >= minCount) return true;
            }
        }

        if (!useCount) return false;
        return found >= minCount && (maxCount < 0 || found <= maxCount);
    }

    private static void handleOutbound(@Nullable PlayerRef playerRef, @Nullable Packet packet) {
        if (playerRef == null || packet == null) return;
        UUID worldUuid = playerRef.getWorldUuid();
        if (worldUuid == null) return;

        if (packet instanceof SpawnParticleSystem particle) {
            recordParticleSystem(worldUuid, particle);
            return;
        }
        if (packet instanceof SpawnBlockParticleSystem blockParticle) {
            recordBlockParticle(worldUuid, blockParticle);
        }
    }

    private static void recordParticleSystem(UUID worldUuid, SpawnParticleSystem packet) {
        Position pos = packet.position;
        String id = packet.particleSystemId;
        if (pos == null || id == null || id.isBlank()) return;
        recordEvent(worldUuid, normalizeId(id), pos.x, pos.y, pos.z);
    }

    private static void recordBlockParticle(UUID worldUuid, SpawnBlockParticleSystem packet) {
        Position pos = packet.position;
        if (pos == null) return;

        BlockType blockType = BlockType.getAssetMap().getAsset(packet.blockId);
        String blockId = blockType != null ? blockType.getId() : "blockid:" + packet.blockId;
        String normalizedBlock = normalizeId(blockId);
        String eventType = packet.particleType != null ? packet.particleType.name() : BlockParticleEvent.Walk.name();
        String normalizedEvent = normalizeId(eventType);

        recordEvent(worldUuid, "block:" + normalizedBlock, pos.x, pos.y, pos.z);
        recordEvent(worldUuid, "blockevent:" + normalizedEvent, pos.x, pos.y, pos.z);
        recordEvent(worldUuid, "blockevent:" + normalizedBlock + ":" + normalizedEvent, pos.x, pos.y, pos.z);
    }

    private static void recordEvent(UUID worldUuid, String id, double x, double y, double z) {
        long now = System.currentTimeMillis();
        ArrayDeque<ParticleEvent> deque = EVENTS.computeIfAbsent(worldUuid, k -> new ArrayDeque<>());
        synchronized (deque) {
            pruneOld(deque, now);
            deque.addLast(new ParticleEvent(id, x, y, z, now));
            while (deque.size() > MAX_EVENTS_PER_WORLD) {
                deque.removeFirst();
            }
        }
        if (DEBUG) {
            LOGGER.atInfo().log("[MGHG|PARTICLE] id=%s pos=%.2f,%.2f,%.2f", id, x, y, z);
        }
    }

    private static void pruneOld(ArrayDeque<ParticleEvent> deque, long now) {
        while (!deque.isEmpty()) {
            ParticleEvent peek = deque.peekFirst();
            if (peek == null || now - peek.timeMs > GLOBAL_MAX_AGE_MS) {
                deque.removeFirst();
            } else {
                break;
            }
        }
    }

    private static String normalizeId(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }

    private record ParticleEvent(String id, double x, double y, double z, long timeMs) {}
}
