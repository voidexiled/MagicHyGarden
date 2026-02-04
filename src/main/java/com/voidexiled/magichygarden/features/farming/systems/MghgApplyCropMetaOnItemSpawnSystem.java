package com.voidexiled.magichygarden.features.farming.systems;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.voidexiled.magichygarden.features.farming.items.MghgCropMeta;
import com.voidexiled.magichygarden.features.farming.logic.MghgHarvestUtil;
import com.voidexiled.magichygarden.features.farming.logic.MghgSupportDropMetaCache;
import com.voidexiled.magichygarden.features.farming.registry.MghgCropRegistry;
import com.voidexiled.magichygarden.features.farming.visuals.MghgCropVisualStateResolver;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class MghgApplyCropMetaOnItemSpawnSystem extends RefSystem<EntityStore> {
    @Override
    public void onEntityAdded(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull AddReason reason,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        ItemComponent itemComponent = store.getComponent(ref, ItemComponent.getComponentType());
        if (itemComponent == null) {
            return;
        }

        ItemStack stack = itemComponent.getItemStack();
        if (stack == null || stack.isEmpty()) {
            return;
        }
        if (stack.getItem() == null || !MghgCropRegistry.isMghgCropItem(stack.getItem().getId())) {
            return;
        }

        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            return;
        }

        Vector3d pos = transform.getPosition();
        int bx = (int) Math.floor(pos.x);
        int by = (int) Math.floor(pos.y);
        int bz = (int) Math.floor(pos.z);

        MghgSupportDropMetaCache.Pending pending = MghgSupportDropMetaCache.peek(bx, by, bz);
        if (pending == null) {
            MghgCropMeta meta = stack.getFromMetadataOrNull(MghgCropMeta.KEY);
            return;
        }

        if (!MghgHarvestUtil.shouldApplyMghgMeta(stack, pending.data, pending.expectedItemId)) {
            return;
        }

        ItemStack out = stack.withMetadata(
                MghgCropMeta.KEY,
                MghgCropMeta.fromCropData(
                        pending.data.getSize(),
                        pending.data.getClimate().name(),
                        pending.data.getLunar().name(),
                        pending.data.getRarity().name()
                )
        );

        String resolvedState = MghgCropVisualStateResolver.resolveItemState(pending.data);
        if (resolvedState != null && out.getItem() != null
                && out.getItem().getItemIdForState(resolvedState) != null) {
            out = out.withState(resolvedState);
        }

        itemComponent.setItemStack(out);
        MghgSupportDropMetaCache.consume(bx, by, bz, pending);
    }

    @Override
    public void onEntityRemove(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull com.hypixel.hytale.component.RemoveReason reason,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        // no-op
    }

    @Override
    public @Nullable Query<EntityStore> getQuery() {
        return ItemComponent.getComponentType();
    }
}
