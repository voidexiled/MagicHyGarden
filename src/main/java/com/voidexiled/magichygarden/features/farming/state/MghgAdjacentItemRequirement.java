package com.voidexiled.magichygarden.features.farming.state;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.validation.validator.ArrayValidator;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class MghgAdjacentItemRequirement {
    public static final BuilderCodec<MghgAdjacentItemRequirement> CODEC =
            BuilderCodec.builder(MghgAdjacentItemRequirement.class, MghgAdjacentItemRequirement::new)
                    .append(new KeyedCodec<>("Ids", Codec.STRING_ARRAY),
                            (o, v) -> o.ids = v, o -> o.ids)
                    .documentation("Item ids or block ids to search for (array). Item ids resolve to their BlockId when possible.")
                    .addValidator(new ArrayValidator<>(MghgMutationRuleValidators.assetRefHint("Item")))
                    .add()
                    .append(new KeyedCodec<>("Radius", Codec.INTEGER, true),
                            (o, v) -> o.radius = v == null ? o.radius : v,
                            o -> o.radius)
                    .documentation("Radius in blocks for all axes (default: 1).")
                    .add()
                    .append(new KeyedCodec<>("RadiusX", Codec.INTEGER, true),
                            (o, v) -> o.radiusX = v == null ? o.radiusX : v,
                            o -> o.radiusX)
                    .documentation("Override radius on X axis. If set, RadiusX is used instead of Radius.")
                    .add()
                    .append(new KeyedCodec<>("RadiusY", Codec.INTEGER, true),
                            (o, v) -> o.radiusY = v == null ? o.radiusY : v,
                            o -> o.radiusY)
                    .documentation("Override radius on Y axis. If set, RadiusY is used instead of Radius.")
                    .add()
                    .append(new KeyedCodec<>("RadiusZ", Codec.INTEGER, true),
                            (o, v) -> o.radiusZ = v == null ? o.radiusZ : v,
                            o -> o.radiusZ)
                    .documentation("Override radius on Z axis. If set, RadiusZ is used instead of Radius.")
                    .add()
                    .append(new KeyedCodec<>("OffsetX", Codec.INTEGER, true),
                            (o, v) -> o.offsetX = v == null ? o.offsetX : v,
                            o -> o.offsetX)
                    .documentation("Offset from crop position on X axis (default: 0).")
                    .add()
                    .append(new KeyedCodec<>("OffsetY", Codec.INTEGER, true),
                            (o, v) -> o.offsetY = v == null ? o.offsetY : v,
                            o -> o.offsetY)
                    .documentation("Offset from crop position on Y axis (default: 0).")
                    .add()
                    .append(new KeyedCodec<>("OffsetZ", Codec.INTEGER, true),
                            (o, v) -> o.offsetZ = v == null ? o.offsetZ : v,
                            o -> o.offsetZ)
                    .documentation("Offset from crop position on Z axis (default: 0).")
                    .add()
                    .append(new KeyedCodec<>("MinCount", Codec.INTEGER, true),
                            (o, v) -> o.minCount = v, o -> o.minCount)
                    .documentation("Minimum matches required inside the radius (default: 1).")
                    .add()
                    .append(new KeyedCodec<>("MaxCount", Codec.INTEGER, true),
                            (o, v) -> o.maxCount = v, o -> o.maxCount)
                    .documentation("Maximum matches allowed inside the radius (optional).")
                    .add()
                    .build();

    private String[] ids = new String[0];
    private int radius = 1;
    private int radiusX = -1;
    private int radiusY = -1;
    private int radiusZ = -1;
    private int offsetX;
    private int offsetY;
    private int offsetZ;
    @Nullable private Integer minCount;
    @Nullable private Integer maxCount;

    @Nullable private Set<String> resolvedIds;

    public void resolveCaches() {
        if (resolvedIds != null) return;
        if (ids == null || ids.length == 0) {
            resolvedIds = Collections.emptySet();
            return;
        }

        Set<String> out = new HashSet<>();
        for (String raw : ids) {
            if (raw == null) continue;
            String trimmed = raw.trim();
            if (trimmed.isEmpty()) continue;
            addMatchId(out, trimmed);

            // If it's an Item id, also add its BlockId.
            Item item = Item.getAssetMap().getAsset(trimmed);
            if (item != null) {
                String blockId = item.getBlockId();
                if (blockId != null && !blockId.isBlank()) {
                    addMatchId(out, blockId);
                }
            }

            // If it's a state id, also add its base block id.
            String baseId = MghgBlockIdUtil.resolveBaseIdFromStateId(trimmed);
            if (baseId != null && !baseId.isBlank()) {
                addMatchId(out, baseId);
            }

            // If it's a block id, add it as-is (base blocks already handled above).
            if (BlockType.getAssetMap().getIndex(trimmed) != BlockType.UNKNOWN_ID) {
                addMatchId(out, trimmed);
            }
        }

        resolvedIds = out.isEmpty() ? Collections.emptySet() : Collections.unmodifiableSet(out);
    }

    private static void addMatchId(Set<String> out, String id) {
        String normalized = MghgBlockIdUtil.normalizeId(id);
        if (normalized != null && !normalized.isEmpty()) {
            out.add(normalized);
        }
    }

    public Set<String> getResolvedIds() {
        return resolvedIds == null ? Collections.emptySet() : resolvedIds;
    }

    public String[] getIds() {
        return ids;
    }

    public int getRadiusX() {
        return radiusX >= 0 ? radiusX : radius;
    }

    public int getRadiusY() {
        return radiusY >= 0 ? radiusY : radius;
    }

    public int getRadiusZ() {
        return radiusZ >= 0 ? radiusZ : radius;
    }

    public int getOffsetX() {
        return offsetX;
    }

    public int getOffsetY() {
        return offsetY;
    }

    public int getOffsetZ() {
        return offsetZ;
    }

    public boolean hasCountConstraint() {
        return minCount != null || maxCount != null;
    }

    public int getMinCount() {
        return minCount == null ? 1 : Math.max(0, minCount);
    }

    public int getMaxCount() {
        return maxCount == null ? -1 : Math.max(-1, maxCount);
    }
}
