package com.voidexiled.magichygarden.commands.farm;

import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;

import javax.annotation.Nullable;

public final class FarmTeleportUtil {
    private FarmTeleportUtil() {
    }

    public static Transform defaultSpawnTransform() {
        return new Transform(new Vector3d(0.0, 80.0, 0.0), new Vector3f(0.0f, 0.0f, 0.0f));
    }

    public static Transform createTransform(double x, double y, double z, @Nullable Vector3f rotation) {
        return new Transform(new Vector3d(x, y, z), sanitizeRotation(rotation));
    }

    public static Vector3f sanitizeRotation(@Nullable Vector3f rotation) {
        if (rotation == null) {
            return new Vector3f(0.0f, 0.0f, 0.0f);
        }
        float pitch = Float.isFinite(rotation.x) ? rotation.x : 0.0f;
        float yaw = Float.isFinite(rotation.y) ? rotation.y : 0.0f;
        float roll = Float.isFinite(rotation.z) ? rotation.z : 0.0f;
        return new Vector3f(pitch, yaw, roll);
    }
}
