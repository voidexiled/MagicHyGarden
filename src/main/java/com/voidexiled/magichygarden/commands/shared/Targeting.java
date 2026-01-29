package com.voidexiled.magichygarden.commands.shared;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;

import javax.annotation.Nullable;

public class Targeting {
    private Targeting() {}

    public static @Nullable Vector3i getTargetBlock(
            Ref<EntityStore> executor,
            ComponentAccessor<EntityStore> accessor,
            double maxDistance
    ) {
        return TargetUtil.getTargetBlock(executor, maxDistance, accessor);
    }
}
