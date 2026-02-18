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
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcel;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcelAccess;
import com.voidexiled.magichygarden.features.farming.perks.MghgFarmPerkManager;
import com.voidexiled.magichygarden.features.farming.worlds.MghgFarmWorldManager;

import javax.annotation.Nonnull;

public final class MghgFertileSoilBreakSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {
    public MghgFertileSoilBreakSystem() {
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
        if (event.isCancelled()) {
            return;
        }

        if (!MghgFarmPerkManager.isFertileBaseBlock(event.getBlockType())) {
            return;
        }

        World world = store.getExternalData().getWorld();
        if (world == null || !MghgFarmWorldManager.isFarmWorld(world)) {
            return;
        }

        MghgParcel parcel = MghgParcelAccess.resolveParcel(world);
        if (parcel == null) {
            return;
        }

        String key = MghgFarmPerkManager.toBlockKey(event.getTargetBlock());
        MghgFarmPerkManager.untrackFertileBlock(parcel, key);
    }
}
