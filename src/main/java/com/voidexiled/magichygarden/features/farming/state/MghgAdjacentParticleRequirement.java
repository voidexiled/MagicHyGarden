package com.voidexiled.magichygarden.features.farming.state;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.validation.validator.ArrayValidator;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class MghgAdjacentParticleRequirement {
    public static final BuilderCodec<MghgAdjacentParticleRequirement> CODEC =
            BuilderCodec.builder(MghgAdjacentParticleRequirement.class, MghgAdjacentParticleRequirement::new)
                    .append(new KeyedCodec<>("AnyParticle", Codec.BOOLEAN, true),
                            (o, v) -> o.anyParticle = v != null && v,
                            o -> o.anyParticle)
                    .documentation("If true, any particle id counts (ignores id lists).")
                    .add()
                    .append(new KeyedCodec<>("UseRuntimeParticles", Codec.BOOLEAN, true),
                            (o, v) -> o.useRuntimeParticles = v == null ? o.useRuntimeParticles : v,
                            o -> o.useRuntimeParticles)
                    .documentation("If true, uses packet-based particle tracker (all particles sent to clients).")
                    .add()
                    .append(new KeyedCodec<>("UseBlockParticles", Codec.BOOLEAN, true),
                            (o, v) -> o.useBlockParticles = v == null ? o.useBlockParticles : v,
                            o -> o.useBlockParticles)
                    .documentation("If true, checks BlockType particle assets around the crop.")
                    .add()
                    .append(new KeyedCodec<>("ParticleSystemIds", Codec.STRING_ARRAY, true),
                            (o, v) -> o.particleSystemIds = v, o -> o.particleSystemIds)
                    .documentation("ParticleSystem ids to search for (array).")
                    .addValidator(new ArrayValidator<>(MghgMutationRuleValidators.assetRefHint("ParticleSystem")))
                    .add()
                    .append(new KeyedCodec<>("BlockParticleSetIds", Codec.STRING_ARRAY, true),
                            (o, v) -> o.blockParticleSetIds = v, o -> o.blockParticleSetIds)
                    .documentation("BlockParticleSet ids to search for (array).")
                    .addValidator(new ArrayValidator<>(MghgMutationRuleValidators.assetRefHint("BlockParticleSet")))
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
                    .append(new KeyedCodec<>("MaxAgeSeconds", Codec.DOUBLE, true),
                            (o, v) -> o.maxAgeSeconds = v == null ? o.maxAgeSeconds : v,
                            o -> o.maxAgeSeconds)
                    .documentation("Max age (seconds) for runtime particle events (default: 2).")
                    .add()
                    .build();

    private boolean anyParticle;
    private boolean useRuntimeParticles = true;
    private boolean useBlockParticles = true;
    private String[] particleSystemIds = new String[0];
    private String[] blockParticleSetIds = new String[0];
    private int radius = 1;
    private int radiusX = -1;
    private int radiusY = -1;
    private int radiusZ = -1;
    private int offsetX;
    private int offsetY;
    private int offsetZ;
    @Nullable private Integer minCount;
    @Nullable private Integer maxCount;
    private double maxAgeSeconds = 2.0;

    @Nullable private Set<String> resolvedIds;

    public void resolveCaches() {
        if (resolvedIds != null) return;

        Set<String> out = new HashSet<>();
        if (!anyParticle) {
            addIds(out, particleSystemIds);
            addIds(out, blockParticleSetIds);
        }

        resolvedIds = out.isEmpty() ? Collections.emptySet() : Collections.unmodifiableSet(out);
    }

    private static void addIds(Set<String> out, String[] ids) {
        if (ids == null || ids.length == 0) return;
        for (String raw : ids) {
            if (raw == null) continue;
            String trimmed = raw.trim();
            if (trimmed.isEmpty()) continue;
            out.add(normalizeId(trimmed));
        }
    }

    public Set<String> getResolvedIds() {
        return resolvedIds == null ? Collections.emptySet() : resolvedIds;
    }

    public boolean matchesId(String id) {
        if (anyParticle) return true;
        if (id == null || id.isBlank()) return false;
        String norm = normalizeId(id);
        return getResolvedIds().contains(norm);
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

    public boolean isAnyParticle() {
        return anyParticle;
    }

    public boolean isUseRuntimeParticles() {
        return useRuntimeParticles;
    }

    public boolean isUseBlockParticles() {
        return useBlockParticles;
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

    public long getMaxAgeMillis(long fallbackMs) {
        double seconds = maxAgeSeconds <= 0 ? fallbackMs / 1000.0 : maxAgeSeconds;
        long ms = (long) Math.max(0, seconds * 1000.0);
        return ms == 0 ? fallbackMs : ms;
    }

    private static String normalizeId(String id) {
        return id.trim().toLowerCase(Locale.ROOT);
    }
}
