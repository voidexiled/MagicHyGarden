package com.voidexiled.magichygarden.features.farming.systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcel;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcelAccess;
import com.voidexiled.magichygarden.features.farming.perks.MghgFarmPerkManager;
import com.voidexiled.magichygarden.features.farming.worlds.MghgFarmWorldManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class MghgFertileSoilUsePreSystem extends EntityEventSystem<EntityStore, UseBlockEvent.Pre> {
    private static final long DENIED_MESSAGE_COOLDOWN_MILLIS = 800L;
    private static final int TRACK_RETRY_ATTEMPTS = 6;
    private static final long TRACK_RETRY_DELAY_MILLIS = 80L;
    private final Map<UUID, Long> lastDeniedMessageAt = new ConcurrentHashMap<>();

    public MghgFertileSoilUsePreSystem() {
        super(UseBlockEvent.Pre.class);
    }

    @Override
    public Query<EntityStore> getQuery() {
        return PlayerRef.getComponentType();
    }

    @Override
    public void handle(
            int index,
            @Nonnull ArchetypeChunk<EntityStore> chunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull UseBlockEvent.Pre event
    ) {
        if (event.isCancelled()) {
            return;
        }

        World world = store.getExternalData().getWorld();
        if (world == null || !MghgFarmWorldManager.isFarmWorld(world)) {
            return;
        }

        String heldItemId = resolveHeldItemId(event);

        PlayerRef playerRef = chunk.getComponent(index, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }

        MghgParcel parcel = MghgParcelAccess.resolveParcel(world);
        if (parcel == null) {
            return;
        }

        UUID playerId = playerRef.getUuid();
        UUID ownerId = MghgFarmWorldManager.getOwnerFromFarmWorld(world);
        boolean isOwner = ownerId != null && ownerId.equals(playerId);
        if (!MghgParcelAccess.canBuild(parcel, playerId) && !isOwner) {
            return;
        }

        if (!MghgFarmPerkManager.isAnyHoeItem(heldItemId)) {
            return;
        }

        Vector3i target = event.getTargetBlock();
        List<Vector3i> tillCandidates = collectTillSourceCandidates(world, target, event.getBlockType());
        if (tillCandidates.isEmpty()) {
            return;
        }

        if (!MghgFarmPerkManager.isHoeItem(heldItemId)) {
            event.setCancelled(true);
            sendCustomHoeRequiredMessage(playerRef);
            return;
        }

        if (canTrackAnyCandidate(parcel, tillCandidates)) {
            // Fallback tracking pass for hoe interactions that may not emit UseBlockEvent.Post consistently
            // or that report a target offset from the actual transformed block.
            scheduleTrackRetry(world, parcel, List.copyOf(tillCandidates), TRACK_RETRY_ATTEMPTS);
            return;
        }

        event.setCancelled(true);
        sendDeniedMessage(playerRef, parcel);
    }

    private void sendDeniedMessage(@Nonnull PlayerRef playerRef, @Nonnull MghgParcel parcel) {
        UUID playerId = playerRef.getUuid();
        long now = System.currentTimeMillis();
        Long previous = lastDeniedMessageAt.get(playerId);
        if (previous != null && now - previous < DENIED_MESSAGE_COOLDOWN_MILLIS) {
            return;
        }
        lastDeniedMessageAt.put(playerId, now);

        int current = MghgFarmPerkManager.getTrackedFertileCount(parcel);
        int cap = MghgFarmPerkManager.getFertileSoilCap(parcel);
        playerRef.sendMessage(Message.raw(
                "Fertile soil limit reached.\n"
                        + "Current: " + current + " / " + cap + ".\n"
                        + "Upgrade with /farm perks upgrade fertile_soil."
        ));
    }

    private void sendCustomHoeRequiredMessage(@Nonnull PlayerRef playerRef) {
        UUID playerId = playerRef.getUuid();
        long now = System.currentTimeMillis();
        Long previous = lastDeniedMessageAt.get(playerId);
        if (previous != null && now - previous < DENIED_MESSAGE_COOLDOWN_MILLIS) {
            return;
        }
        lastDeniedMessageAt.put(playerId, now);
        playerRef.sendMessage(Message.raw(
                "You must use the custom farm hoe for tilling.\n"
                        + "Craft/use Tool_Hoe_Custom."
        ));
    }

    private void scheduleTrackRetry(
            @Nonnull World world,
            @Nonnull MghgParcel parcel,
            @Nonnull List<Vector3i> candidates,
            int attemptsLeft
    ) {
        if (attemptsLeft <= 0 || candidates.isEmpty()) {
            return;
        }
        HytaleServer.SCHEDULED_EXECUTOR.schedule(
                () -> world.execute(() -> {
                    if (tryTrackAnyConvertedCandidate(world, parcel, candidates)) {
                        return;
                    }
                    scheduleTrackRetry(world, parcel, candidates, attemptsLeft - 1);
                }),
                TRACK_RETRY_DELAY_MILLIS,
                TimeUnit.MILLISECONDS
        );
    }

    private boolean canTrackAnyCandidate(@Nonnull MghgParcel parcel, @Nonnull List<Vector3i> candidates) {
        for (Vector3i candidate : candidates) {
            if (MghgFarmPerkManager.canTrackFertileBlock(parcel, MghgFarmPerkManager.toBlockKey(candidate))) {
                return true;
            }
        }
        return false;
    }

    private boolean tryTrackAnyConvertedCandidate(
            @Nonnull World world,
            @Nonnull MghgParcel parcel,
            @Nonnull List<Vector3i> candidates
    ) {
        for (Vector3i candidate : candidates) {
            if (!MghgFarmPerkManager.isFertileBaseBlock(world.getBlockType(candidate.x, candidate.y, candidate.z))) {
                continue;
            }
            String key = MghgFarmPerkManager.toBlockKey(candidate);
            if (MghgFarmPerkManager.canTrackFertileBlock(parcel, key)) {
                MghgFarmPerkManager.trackFertileBlock(parcel, key);
            }
            return true;
        }
        return false;
    }

    private @Nonnull List<Vector3i> collectTillSourceCandidates(
            @Nonnull World world,
            @Nullable Vector3i target,
            @Nullable com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType eventBlockType
    ) {
        if (target == null) {
            return List.of();
        }
        Set<String> seen = new LinkedHashSet<>();
        List<Vector3i> out = new ArrayList<>();

        // Covers normal target block plus neighboring offsets in case the event target
        // differs from the block actually converted by the hoe interaction.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    int x = target.x + dx;
                    int y = target.y + dy;
                    int z = target.z + dz;
                    if (!MghgFarmPerkManager.isTillSourceBlock(world.getBlockType(x, y, z))) {
                        continue;
                    }
                    String key = MghgFarmPerkManager.toBlockKey(x, y, z);
                    if (seen.add(key)) {
                        out.add(new Vector3i(x, y, z));
                    }
                }
            }
        }

        if (out.isEmpty() && MghgFarmPerkManager.isTillSourceBlock(eventBlockType)) {
            out.add(new Vector3i(target.x, target.y, target.z));
        }
        return out;
    }

    private @Nullable String resolveHeldItemId(@Nonnull UseBlockEvent.Pre event) {
        String direct = toItemId(event.getContext() == null ? null : event.getContext().getHeldItem());
        if (direct != null) {
            return direct;
        }

        // API compatibility fallback: some versions expose the item directly on the event.
        try {
            Object value = event.getClass().getMethod("getItemInHand").invoke(event);
            if (value instanceof ItemStack stack) {
                return toItemId(stack);
            }
        } catch (ReflectiveOperationException ignored) {
            // noop
        }
        return null;
    }

    private @Nullable String toItemId(@Nullable ItemStack stack) {
        return stack == null || stack.getItem() == null ? null : stack.getItem().getId();
    }
}
