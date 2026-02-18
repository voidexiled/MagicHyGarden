package com.voidexiled.magichygarden.features.farming.systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.voidexiled.magichygarden.features.farming.events.MghgFarmEventScheduler;
import com.voidexiled.magichygarden.features.farming.shop.MghgShopAccessPolicy;
import com.voidexiled.magichygarden.features.farming.ui.MghgFarmShopPage;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MghgShopBenchUseSystem extends EntityEventSystem<EntityStore, UseBlockEvent.Pre> {
    private static final long OPEN_COOLDOWN_MILLIS = 250L;
    private final Map<UUID, Long> lastOpenAtMillis = new ConcurrentHashMap<>();

    public MghgShopBenchUseSystem() {
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
        if (world == null || !MghgFarmEventScheduler.isFarmWorld(world)) {
            return;
        }

        InteractionType interactionType = event.getInteractionType();
        if (interactionType != InteractionType.Secondary && interactionType != InteractionType.Use) {
            return;
        }

        if (!MghgShopAccessPolicy.isConfiguredBenchBlock(event.getBlockType())) {
            return;
        }

        PlayerRef playerRef = chunk.getComponent(index, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }

        UUID playerId = playerRef.getUuid();
        long now = System.currentTimeMillis();
        Long last = lastOpenAtMillis.get(playerId);
        if (last != null && now - last < OPEN_COOLDOWN_MILLIS) {
            event.setCancelled(true);
            return;
        }
        lastOpenAtMillis.put(playerId, now);

        Ref<EntityStore> playerEntityRef = chunk.getReferenceTo(index);
        String openError = MghgFarmShopPage.openForPlayer(store, playerEntityRef, playerRef, world);
        event.setCancelled(true);
        if (openError != null) {
            playerRef.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text(openError));
        }
    }
}
