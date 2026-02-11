package com.voidexiled.magichygarden.commands.farm;

import com.hypixel.hytale.server.core.universe.world.World;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcel;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcelAccess;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcelManager;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcelRole;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

public final class FarmParcelCommandUtil {
    private FarmParcelCommandUtil() {
    }

    public static @Nullable MghgParcel resolveManagedParcel(@Nullable UUID playerId, @Nullable World world) {
        if (playerId == null) {
            return null;
        }

        MghgParcel worldParcel = MghgParcelAccess.resolveParcel(world);
        if (isManagerOrOwner(worldParcel, playerId)) {
            return worldParcel;
        }

        MghgParcel ownParcel = MghgParcelManager.getByOwner(playerId);
        if (isManagerOrOwner(ownParcel, playerId)) {
            return ownParcel;
        }

        return null;
    }

    private static boolean isManagerOrOwner(@Nullable MghgParcel parcel, @Nullable UUID playerId) {
        if (parcel == null || playerId == null) {
            return false;
        }
        MghgParcelRole role = MghgParcelAccess.resolveRole(parcel, playerId);
        return role == MghgParcelRole.OWNER || role == MghgParcelRole.MANAGER;
    }
}
