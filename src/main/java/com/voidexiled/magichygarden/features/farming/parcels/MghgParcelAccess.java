package com.voidexiled.magichygarden.features.farming.parcels;

import com.hypixel.hytale.server.core.universe.world.World;
import com.voidexiled.magichygarden.features.farming.worlds.MghgFarmWorldManager;

import javax.annotation.Nullable;
import java.util.UUID;

public final class MghgParcelAccess {
    private MghgParcelAccess() {
    }

    @Nullable
    public static MghgParcel resolveParcel(@Nullable World world) {
        UUID owner = MghgFarmWorldManager.getOwnerFromFarmWorld(world);
        if (owner == null) {
            return null;
        }
        return MghgParcelManager.getByOwner(owner);
    }

    public static MghgParcelRole resolveRole(@Nullable MghgParcel parcel, @Nullable UUID playerId) {
        if (parcel == null || playerId == null) {
            return MghgParcelRole.VISITOR;
        }
        return parcel.getRole(playerId);
    }

    public static boolean canBuild(@Nullable MghgParcel parcel, @Nullable UUID playerId) {
        MghgParcelRole role = resolveRole(parcel, playerId);
        return role == MghgParcelRole.OWNER
                || role == MghgParcelRole.MANAGER
                || role == MghgParcelRole.MEMBER;
    }

    public static boolean canVisit(@Nullable MghgParcel parcel, @Nullable UUID playerId) {
        MghgParcelRole role = resolveRole(parcel, playerId);
        return role == MghgParcelRole.OWNER
                || role == MghgParcelRole.MANAGER
                || role == MghgParcelRole.MEMBER;
    }
}
