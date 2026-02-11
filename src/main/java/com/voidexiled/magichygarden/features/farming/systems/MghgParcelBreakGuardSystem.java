package com.voidexiled.magichygarden.features.farming.systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.voidexiled.magichygarden.features.farming.events.MghgFarmEventScheduler;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcel;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcelAccess;
import com.voidexiled.magichygarden.features.farming.worlds.MghgFarmWorldManager;

import javax.annotation.Nonnull;
import java.util.UUID;

public final class MghgParcelBreakGuardSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {
    public MghgParcelBreakGuardSystem() {
        super(BreakBlockEvent.class);
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
            @Nonnull BreakBlockEvent event
    ) {
        World world = store.getExternalData().getWorld();
        if (world == null || !MghgFarmEventScheduler.isFarmWorld(world)) {
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
        UUID worldOwner = MghgFarmWorldManager.getOwnerFromFarmWorld(world);
        boolean isWorldOwner = worldOwner != null && worldOwner.equals(playerId);
        if (!MghgParcelAccess.canBuild(parcel, playerId) && !isWorldOwner) {
            event.setCancelled(true);
        }
    }
}
