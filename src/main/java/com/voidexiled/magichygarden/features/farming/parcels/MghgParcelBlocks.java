package com.voidexiled.magichygarden.features.farming.parcels;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;
import com.voidexiled.magichygarden.features.farming.state.MghgBlockIdUtil;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

public final class MghgParcelBlocks {
    public static final BuilderCodec<MghgParcelBlocks> CODEC = BuilderCodec.builder(MghgParcelBlocks.class, MghgParcelBlocks::new)
            // Legacy dense snapshot fields (kept optional for migration).
            .<Integer>append(new KeyedCodec<>("SizeX", Codec.INTEGER, true), (o, v) -> o.sizeX = v, o -> o.sizeX)
            .add()
            .<Integer>append(new KeyedCodec<>("SizeY", Codec.INTEGER, true), (o, v) -> o.sizeY = v, o -> o.sizeY)
            .add()
            .<Integer>append(new KeyedCodec<>("SizeZ", Codec.INTEGER, true), (o, v) -> o.sizeZ = v, o -> o.sizeZ)
            .add()
            .<String[]>append(new KeyedCodec<>("Palette", Codec.STRING_ARRAY, true), (o, v) -> o.palette = v, o -> o.palette)
            .add()
            .<int[]>append(new KeyedCodec<>("Indices", Codec.INT_ARRAY, true), (o, v) -> o.indices = v, o -> o.indices)
            .add()
            // New sparse delta format.
            .<MghgParcelBlockEntry[]>append(
                    new KeyedCodec<>("Entries", ArrayCodec.ofBuilderCodec(MghgParcelBlockEntry.CODEC, MghgParcelBlockEntry[]::new), true),
                    (o, v) -> o.entries = v,
                    o -> o.entries
            )
            .add()
            .afterDecode(MghgParcelBlocks::normalize)
            .build();

    private int sizeX = MghgParcelBounds.DEFAULT_SIZE_X;
    private int sizeY = MghgParcelBounds.DEFAULT_SIZE_Y;
    private int sizeZ = MghgParcelBounds.DEFAULT_SIZE_Z;
    private String[] palette = new String[0];
    private int[] indices = new int[0];
    private MghgParcelBlockEntry[] entries = new MghgParcelBlockEntry[0];

    // Runtime map for quick updates: key = "x,y,z", value = block id.
    private transient Map<String, String> sparseByLocal;

    public MghgParcelBlocks() {
    }

    public boolean isEmpty() {
        return sparse().isEmpty();
    }

    public boolean isEffectivelyEmpty() {
        return sparse().isEmpty();
    }

    public int getSizeX() {
        return sizeX;
    }

    public int getSizeY() {
        return sizeY;
    }

    public int getSizeZ() {
        return sizeZ;
    }

    public String[] getPalette() {
        return palette;
    }

    public int[] getIndices() {
        return indices;
    }

    public MghgParcelBlockEntry[] getEntries() {
        return entries;
    }

    public boolean isCompatible(@Nullable MghgParcelBounds bounds) {
        return bounds != null;
    }

    public boolean setWorldBlock(@Nullable MghgParcelBounds bounds, int worldX, int worldY, int worldZ, @Nullable String rawBlockId) {
        if (bounds == null) {
            return false;
        }
        int rx = worldX - bounds.getOriginX();
        int ry = worldY - bounds.getOriginY();
        int rz = worldZ - bounds.getOriginZ();
        String blockId = sanitizeStoredId(rawBlockId);
        Map<String, String> sparse = sparse();
        String key = key(rx, ry, rz);
        String previous = sparse.get(key);

        boolean changed;
        if (isEmptyLike(blockId)) {
            changed = sparse.remove(key) != null;
        } else {
            changed = !Objects.equals(previous, blockId);
            if (changed) {
                sparse.put(key, blockId);
            }
        }
        if (changed) {
            syncEntriesFromSparse();
            clearLegacyDense();
        }
        return changed;
    }

    private void normalize() {
        if (sizeX <= 0) sizeX = MghgParcelBounds.DEFAULT_SIZE_X;
        if (sizeY <= 0) sizeY = MghgParcelBounds.DEFAULT_SIZE_Y;
        if (sizeZ <= 0) sizeZ = MghgParcelBounds.DEFAULT_SIZE_Z;
        if (palette == null) palette = new String[0];
        if (indices == null) indices = new int[0];
        if (entries == null) entries = new MghgParcelBlockEntry[0];

        Map<String, String> sparse = sparse();
        if (sparse.isEmpty() && indices.length > 0 && palette.length > 0) {
            migrateDenseIntoSparse(sparse);
            clearLegacyDense();
        }
        syncEntriesFromSparse();
    }

    private void clearLegacyDense() {
        this.palette = new String[0];
        this.indices = new int[0];
    }

    private void migrateDenseIntoSparse(Map<String, String> sparse) {
        int total = sizeX * sizeY * sizeZ;
        if (indices.length < total) {
            return;
        }
        for (int y = 0; y < sizeY; y++) {
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++) {
                    int paletteIndex = indices[index(x, y, z, sizeX, sizeZ)];
                    if (paletteIndex < 0 || paletteIndex >= palette.length) {
                        continue;
                    }
                    String id = sanitizeStoredId(palette[paletteIndex]);
                    if (isEmptyLike(id)) {
                        continue;
                    }
                    sparse.put(key(x, y, z), id);
                }
            }
        }
    }

    private Map<String, String> sparse() {
        if (sparseByLocal != null) {
            return sparseByLocal;
        }
        sparseByLocal = new LinkedHashMap<>();
        if (entries != null) {
            for (MghgParcelBlockEntry entry : entries) {
                if (entry == null) {
                    continue;
                }
                String id = sanitizeStoredId(entry.getId());
                if (isEmptyLike(id)) {
                    continue;
                }
                sparseByLocal.put(key(entry.getX(), entry.getY(), entry.getZ()), id);
            }
        }
        return sparseByLocal;
    }

    private void syncEntriesFromSparse() {
        Map<String, String> sparse = sparse();
        if (sparse.isEmpty()) {
            this.entries = new MghgParcelBlockEntry[0];
            return;
        }
        List<MghgParcelBlockEntry> out = new ArrayList<>(sparse.size());
        for (Map.Entry<String, String> kv : sparse.entrySet()) {
            String[] parts = kv.getKey().split(",", 3);
            if (parts.length != 3) {
                continue;
            }
            try {
                int x = Integer.parseInt(parts[0]);
                int y = Integer.parseInt(parts[1]);
                int z = Integer.parseInt(parts[2]);
                String id = sanitizeStoredId(kv.getValue());
                if (isEmptyLike(id)) {
                    continue;
                }
                out.add(new MghgParcelBlockEntry(x, y, z, id));
            } catch (NumberFormatException ignored) {
                // Skip malformed keys.
            }
        }
        this.entries = out.toArray(new MghgParcelBlockEntry[0]);
    }

    public static MghgParcelBlocks capture(World world, MghgParcelBounds bounds) {
        // Kept only for compatibility; sparse mode no longer snapshots full regions.
        MghgParcelBlocks snapshot = new MghgParcelBlocks();
        snapshot.sizeX = bounds.getSizeX();
        snapshot.sizeY = bounds.getSizeY();
        snapshot.sizeZ = bounds.getSizeZ();
        return snapshot;
    }

    public void apply(World world, MghgParcelBounds bounds) {
        if (bounds == null) {
            return;
        }
        Map<String, String> sparse = sparse();
        if (sparse.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> kv : sparse.entrySet()) {
            String[] parts = kv.getKey().split(",", 3);
            if (parts.length != 3) {
                continue;
            }
            int localX;
            int localY;
            int localZ;
            try {
                localX = Integer.parseInt(parts[0]);
                localY = Integer.parseInt(parts[1]);
                localZ = Integer.parseInt(parts[2]);
            } catch (NumberFormatException ignored) {
                continue;
            }
            int worldX = bounds.getOriginX() + localX;
            int worldY = bounds.getOriginY() + localY;
            int worldZ = bounds.getOriginZ() + localZ;
            String id = resolveRestoredBlockId(kv.getValue());
            if (id == null) {
                continue;
            }
            if (id.isBlank()) {
                world.breakBlock(worldX, worldY, worldZ, 0);
            } else {
                world.setBlock(worldX, worldY, worldZ, id);
            }
        }
    }

    private static int index(int x, int y, int z, int sizeX, int sizeZ) {
        return (y * sizeZ + z) * sizeX + x;
    }

    private static @Nullable String resolveBlockId(@Nullable BlockType type) {
        if (type == null) return null;
        String id = type.getId();
        return sanitizeStoredId(id);
    }

    private static String sanitizeStoredId(@Nullable String id) {
        if (id == null) {
            return "";
        }
        String trimmed = id.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        String base = MghgBlockIdUtil.resolveBaseIdFromStateId(trimmed);
        return base == null ? trimmed : base;
    }

    private static boolean isEmptyLike(@Nullable String id) {
        if (id == null) {
            return true;
        }
        String trimmed = id.trim();
        return trimmed.isEmpty()
                || "Empty".equalsIgnoreCase(trimmed)
                || "Air".equalsIgnoreCase(trimmed);
    }

    private static @Nullable String resolveRestoredBlockId(@Nullable String id) {
        if (id == null) {
            return "";
        }
        String safe = sanitizeStoredId(id);
        if (safe.isEmpty()) {
            return "";
        }
        int index = BlockType.getAssetMap().getIndex(safe);
        return index == Integer.MIN_VALUE ? null : safe;
    }

    private static String key(int x, int y, int z) {
        return x + "," + y + "," + z;
    }
}
