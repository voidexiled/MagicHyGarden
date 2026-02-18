package com.voidexiled.magichygarden.features.farming.parcels;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;

import javax.annotation.Nullable;
import java.util.LinkedHashSet;
import java.util.Set;

public final class MghgParcelPerks {
    public static final BuilderCodec<MghgParcelPerks> CODEC =
            BuilderCodec.builder(MghgParcelPerks.class, MghgParcelPerks::new)
                    .append(new KeyedCodec<>("FertileSoilLevel", Codec.INTEGER, true),
                            (o, v) -> o.fertileSoilLevel = v == null ? 1 : v,
                            o -> o.fertileSoilLevel)
                    .documentation("Current fertile soil perk level for this parcel owner.")
                    .add()
                    .append(new KeyedCodec<>("SellMultiplierLevel", Codec.INTEGER, true),
                            (o, v) -> o.sellMultiplierLevel = v == null ? 1 : v,
                            o -> o.sellMultiplierLevel)
                    .documentation("Current sell multiplier perk level for this parcel owner.")
                    .add()
                    .append(new KeyedCodec<>("TrackedFertileBlocks", new ArrayCodec<>(Codec.STRING, String[]::new), true),
                            (o, v) -> o.trackedFertileBlocks = v == null ? new String[0] : v,
                            o -> o.trackedFertileBlocks)
                    .documentation("Tracked fertile block positions encoded as x:y:z.")
                    .add()
                    .build();

    private int fertileSoilLevel = 1;
    private int sellMultiplierLevel = 1;
    private String[] trackedFertileBlocks = new String[0];

    public int getFertileSoilLevel() {
        return Math.max(1, fertileSoilLevel);
    }

    public void setFertileSoilLevel(int level) {
        this.fertileSoilLevel = Math.max(1, level);
    }

    public int getSellMultiplierLevel() {
        return Math.max(1, sellMultiplierLevel);
    }

    public void setSellMultiplierLevel(int level) {
        this.sellMultiplierLevel = Math.max(1, level);
    }

    public int getTrackedFertileBlockCount() {
        return snapshotTrackedFertileBlocks().size();
    }

    public boolean containsTrackedFertileBlock(@Nullable String key) {
        String normalized = normalizeKey(key);
        if (normalized == null) {
            return false;
        }
        for (String entry : trackedFertileBlocks) {
            if (normalized.equals(entry)) {
                return true;
            }
        }
        return false;
    }

    public boolean addTrackedFertileBlock(@Nullable String key) {
        String normalized = normalizeKey(key);
        if (normalized == null) {
            return false;
        }
        Set<String> values = snapshotTrackedFertileBlocks();
        boolean changed = values.add(normalized);
        if (changed) {
            trackedFertileBlocks = values.toArray(String[]::new);
        }
        return changed;
    }

    public boolean removeTrackedFertileBlock(@Nullable String key) {
        String normalized = normalizeKey(key);
        if (normalized == null) {
            return false;
        }
        Set<String> values = snapshotTrackedFertileBlocks();
        boolean changed = values.remove(normalized);
        if (changed) {
            trackedFertileBlocks = values.toArray(String[]::new);
        }
        return changed;
    }

    public void replaceTrackedFertileBlocks(@Nullable Set<String> values) {
        if (values == null || values.isEmpty()) {
            trackedFertileBlocks = new String[0];
            return;
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            String key = normalizeKey(value);
            if (key != null) {
                normalized.add(key);
            }
        }
        trackedFertileBlocks = normalized.toArray(String[]::new);
    }

    public Set<String> snapshotTrackedFertileBlocks() {
        Set<String> result = new LinkedHashSet<>();
        if (trackedFertileBlocks == null) {
            return result;
        }
        for (String entry : trackedFertileBlocks) {
            String normalized = normalizeKey(entry);
            if (normalized != null) {
                result.add(normalized);
            }
        }
        return result;
    }

    private static @Nullable String normalizeKey(@Nullable String key) {
        if (key == null) {
            return null;
        }
        String normalized = key.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
