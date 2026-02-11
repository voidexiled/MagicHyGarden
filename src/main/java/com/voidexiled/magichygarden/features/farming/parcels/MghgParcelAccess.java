package com.voidexiled.magichygarden.features.farming.parcels;

import com.hypixel.hytale.math.vector.Vector3i;
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

    public static boolean isInside(@Nullable MghgParcelBounds bounds, @Nullable Vector3i pos) {
        if (bounds == null || pos == null) {
            return false;
        }
        int minX = bounds.getOriginX();
        int minY = bounds.getOriginY();
        int minZ = bounds.getOriginZ();
        int maxX = minX + bounds.getSizeX();
        int maxY = minY + bounds.getSizeY();
        int maxZ = minZ + bounds.getSizeZ();

        return pos.x >= minX && pos.x < maxX
                && pos.y >= minY && pos.y < maxY
                && pos.z >= minZ && pos.z < maxZ;
    }

    public static boolean isInsideHorizontal(@Nullable MghgParcelBounds bounds, @Nullable Vector3i pos) {
        if (bounds == null || pos == null) {
            return false;
        }
        int minX = bounds.getOriginX();
        int minZ = bounds.getOriginZ();
        int maxX = minX + bounds.getSizeX();
        int maxZ = minZ + bounds.getSizeZ();
        return pos.x >= minX && pos.x < maxX
                && pos.z >= minZ && pos.z < maxZ;
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
