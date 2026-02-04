package com.voidexiled.magichygarden.features.farming.state;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;

import javax.annotation.Nullable;
import java.util.Locale;

public final class MghgBlockIdUtil {
    private MghgBlockIdUtil() {}

    public static @Nullable BlockType resolveBaseBlockType(@Nullable BlockType current) {
        if (current == null) return null;
        String id = current.getId();
        if (id == null || id.isEmpty()) return current;
        if (id.charAt(0) == '*') {
            int idx = id.indexOf("_State_");
            if (idx > 1) {
                String baseId = id.substring(1, idx);
                BlockType base = BlockType.getAssetMap().getAsset(baseId);
                if (base != null) return base;
            }
        }
        return current;
    }

    public static @Nullable String resolveBaseIdFromStateId(@Nullable String id) {
        if (id == null || id.isEmpty()) return null;
        if (id.charAt(0) != '*') return null;
        int idx = id.indexOf("_State_");
        if (idx > 1) {
            return id.substring(1, idx);
        }
        return null;
    }

    public static @Nullable String normalizeId(@Nullable String id) {
        if (id == null) return null;
        String trimmed = id.trim();
        return trimmed.isEmpty() ? null : trimmed.toLowerCase(Locale.ROOT);
    }
}
