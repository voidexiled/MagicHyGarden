package com.voidexiled.magichygarden.features.farming.systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
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
import java.util.UUID;

public final class MghgFertileSoilUsePostSystem extends EntityEventSystem<EntityStore, UseBlockEvent.Post> {
    public MghgFertileSoilUsePostSystem() {
        super(UseBlockEvent.Post.class);
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
            @Nonnull UseBlockEvent.Post event
    ) {
        World world = store.getExternalData().getWorld();
        if (world == null || !MghgFarmWorldManager.isFarmWorld(world)) {
            return;
        }

        PlayerRef playerRef = chunk.getComponent(index, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }

        String heldItemId = resolveHeldItemId(event);
        if (!MghgFarmPerkManager.isHoeItem(heldItemId)) {
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

        Vector3i target = event.getTargetBlock();
        if (target == null) {
            return;
        }

        // Some hoe interactions report a target offset from the transformed block.
        // Scan around the interaction target to find newly converted fertile soil.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    int x = target.x + dx;
                    int y = target.y + dy;
                    int z = target.z + dz;

                    BlockType blockAfter = world.getBlockType(x, y, z);
                    if (!MghgFarmPerkManager.isFertileBaseBlock(blockAfter)) {
                        continue;
                    }

                    String key = MghgFarmPerkManager.toBlockKey(x, y, z);
                    if (MghgFarmPerkManager.canTrackFertileBlock(parcel, key)) {
                        MghgFarmPerkManager.trackFertileBlock(parcel, key);
                    }
                    return;
                }
            }
        }
    }

    private @Nullable String resolveHeldItemId(@Nonnull UseBlockEvent.Post event) {
        String direct = toItemId(event.getContext() == null ? null : event.getContext().getHeldItem());
        if (direct != null) {
            return direct;
        }

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
