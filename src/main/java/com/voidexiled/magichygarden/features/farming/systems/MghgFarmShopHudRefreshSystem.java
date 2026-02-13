package com.voidexiled.magichygarden.features.farming.systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.voidexiled.magichygarden.commands.farm.subcommands.shop.FarmShopCommand;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MghgFarmShopHudRefreshSystem extends EntityTickingSystem<EntityStore> {
    private static final long REFRESH_COOLDOWN_MILLIS = 1000L;
    private final Query<EntityStore> query = Query.and(PlayerRef.getComponentType());
    private final Map<UUID, Long> lastRefreshAtMillis = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastRemainingRestockSeconds = new ConcurrentHashMap<>();

    @Override
    public Query<EntityStore> getQuery() {
        return query;
    }

    @Override
    public void tick(
            float dt,
            int index,
            ArchetypeChunk<EntityStore> chunk,
            Store<EntityStore> store,
            CommandBuffer<EntityStore> commandBuffer
    ) {
        PlayerRef playerRef = chunk.getComponent(index, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }

        long now = System.currentTimeMillis();
        UUID playerId = playerRef.getUuid();
        Long last = lastRefreshAtMillis.get(playerId);
        if (last != null && now - last < REFRESH_COOLDOWN_MILLIS) {
            return;
        }
        lastRefreshAtMillis.put(playerId, now);

        World world = store.getExternalData().getWorld();
        if (world == null) {
            return;
        }

        long currentRemaining = com.voidexiled.magichygarden.features.farming.shop.MghgShopStockManager.getRemainingRestockSeconds();
        Long previousRemaining = lastRemainingRestockSeconds.put(playerId, currentRemaining);
        boolean restockCycleRolledOver = previousRemaining != null && currentRemaining > previousRemaining;

        FarmShopCommand.refreshHudForPlayer(
                store,
                chunk.getReferenceTo(index),
                playerRef,
                world,
                restockCycleRolledOver
        );
    }
}
