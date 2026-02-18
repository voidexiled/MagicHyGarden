package com.voidexiled.magichygarden.features.farming.systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MghgFertileSoilPlaceGuardSystem extends EntityEventSystem<EntityStore, PlaceBlockEvent> {
    private static final long DENIED_MESSAGE_COOLDOWN_MILLIS = 800L;
    private final Map<UUID, Long> lastDeniedMessageAt = new ConcurrentHashMap<>();

    public MghgFertileSoilPlaceGuardSystem() {
        super(PlaceBlockEvent.class);
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
            @Nonnull PlaceBlockEvent event
    ) {
        if (event.isCancelled()) {
            return;
        }

        World world = store.getExternalData().getWorld();
        if (world == null || !MghgFarmWorldManager.isFarmWorld(world)) {
            return;
        }

        ItemStack held = event.getItemInHand();
        String heldItemId = held == null || held.getItem() == null ? null : held.getItem().getId();
        BlockType placedType = resolvePlacedBlockType(event, held);
        if (!MghgFarmPerkManager.isFertileBaseBlock(placedType)) {
            return;
        }

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

        // If this placement originated from hoe tilling, enforce custom hoe only.
        if (MghgFarmPerkManager.isAnyHoeItem(heldItemId) && !MghgFarmPerkManager.isHoeItem(heldItemId)) {
            event.setCancelled(true);
            sendCustomHoeRequiredMessage(playerRef);
            return;
        }

        Vector3i target = event.getTargetBlock();
        String key = MghgFarmPerkManager.toBlockKey(target);
        if (!MghgFarmPerkManager.canTrackFertileBlock(parcel, key)) {
            event.setCancelled(true);
            sendDeniedMessage(playerRef, parcel);
            return;
        }

        // Track placed fertile blocks so they are included in perk cap accounting.
        MghgFarmPerkManager.trackFertileBlock(parcel, key);
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

    private @Nullable BlockType resolvePlacedBlockType(@Nonnull PlaceBlockEvent event, @Nullable ItemStack held) {
        if (held != null) {
            String blockKey = held.getBlockKey();
            if (blockKey != null && !blockKey.isBlank()) {
                return BlockType.getAssetMap().getAsset(blockKey);
            }
        }

        // API compatibility fallback: detect transformed block type for interaction-driven placements (e.g. hoe till).
        for (String method : new String[]{"getBlockType", "getNewBlockType", "getPlacedBlockType"}) {
            try {
                Object value = event.getClass().getMethod(method).invoke(event);
                if (value instanceof BlockType blockType) {
                    return blockType;
                }
                if (value instanceof String blockKey && !blockKey.isBlank()) {
                    return BlockType.getAssetMap().getAsset(blockKey);
                }
            } catch (ReflectiveOperationException ignored) {
                // noop
            }
        }

        return null;
    }
}
