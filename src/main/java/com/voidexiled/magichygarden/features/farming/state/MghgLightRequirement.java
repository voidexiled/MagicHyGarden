package com.voidexiled.magichygarden.features.farming.state;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.EnumCodec;

import javax.annotation.Nullable;

public final class MghgLightRequirement {
    public static final BuilderCodec<MghgLightRequirement> CODEC =
            BuilderCodec.builder(MghgLightRequirement.class, MghgLightRequirement::new)
                    .append(new KeyedCodec<>("Match", new EnumCodec<>(AdjacentMatchMode.class), true),
                            (o, v) -> o.match = v == null ? AdjacentMatchMode.ALL : v,
                            o -> o.match)
                    .documentation("How to evaluate light checks: ALL or ANY. Default: ALL.")
                    .add()
                    .append(new KeyedCodec<>("SkyMin", Codec.INTEGER, true),
                            (o, v) -> o.skyMin = v, o -> o.skyMin)
                    .documentation("Min skylight (0-15).")
                    .add()
                    .append(new KeyedCodec<>("SkyMax", Codec.INTEGER, true),
                            (o, v) -> o.skyMax = v, o -> o.skyMax)
                    .documentation("Max skylight (0-15).")
                    .add()
                    .append(new KeyedCodec<>("BlockMin", Codec.INTEGER, true),
                            (o, v) -> o.blockMin = v, o -> o.blockMin)
                    .documentation("Min block light (raw).")
                    .add()
                    .append(new KeyedCodec<>("BlockMax", Codec.INTEGER, true),
                            (o, v) -> o.blockMax = v, o -> o.blockMax)
                    .documentation("Max block light (raw).")
                    .add()
                    .append(new KeyedCodec<>("BlockIntensityMin", Codec.INTEGER, true),
                            (o, v) -> o.blockIntensityMin = v, o -> o.blockIntensityMin)
                    .documentation("Min block light intensity (0-15).")
                    .add()
                    .append(new KeyedCodec<>("BlockIntensityMax", Codec.INTEGER, true),
                            (o, v) -> o.blockIntensityMax = v, o -> o.blockIntensityMax)
                    .documentation("Max block light intensity (0-15).")
                    .add()
                    .append(new KeyedCodec<>("RedMin", Codec.INTEGER, true),
                            (o, v) -> o.redMin = v, o -> o.redMin)
                    .documentation("Min red block light (0-15).")
                    .add()
                    .append(new KeyedCodec<>("RedMax", Codec.INTEGER, true),
                            (o, v) -> o.redMax = v, o -> o.redMax)
                    .documentation("Max red block light (0-15).")
                    .add()
                    .append(new KeyedCodec<>("GreenMin", Codec.INTEGER, true),
                            (o, v) -> o.greenMin = v, o -> o.greenMin)
                    .documentation("Min green block light (0-15).")
                    .add()
                    .append(new KeyedCodec<>("GreenMax", Codec.INTEGER, true),
                            (o, v) -> o.greenMax = v, o -> o.greenMax)
                    .documentation("Max green block light (0-15).")
                    .add()
                    .append(new KeyedCodec<>("BlueMin", Codec.INTEGER, true),
                            (o, v) -> o.blueMin = v, o -> o.blueMin)
                    .documentation("Min blue block light (0-15).")
                    .add()
                    .append(new KeyedCodec<>("BlueMax", Codec.INTEGER, true),
                            (o, v) -> o.blueMax = v, o -> o.blueMax)
                    .documentation("Max blue block light (0-15).")
                    .add()
                    .build();

    private AdjacentMatchMode match = AdjacentMatchMode.ALL;
    @Nullable private Integer skyMin;
    @Nullable private Integer skyMax;
    @Nullable private Integer blockMin;
    @Nullable private Integer blockMax;
    @Nullable private Integer blockIntensityMin;
    @Nullable private Integer blockIntensityMax;
    @Nullable private Integer redMin;
    @Nullable private Integer redMax;
    @Nullable private Integer greenMin;
    @Nullable private Integer greenMax;
    @Nullable private Integer blueMin;
    @Nullable private Integer blueMax;

    public boolean matches(MghgMutationContext ctx) {
        if (ctx == null) return false;
        AdjacentMatchMode mode = match == null ? AdjacentMatchMode.ALL : match;

        boolean anyDefined = false;
        boolean anyPass = false;

        if (isDefined(skyMin, skyMax)) {
            anyDefined = true;
            boolean pass = inRange(ctx.getLightSky(), skyMin, skyMax);
            if (mode == AdjacentMatchMode.ALL && !pass) return false;
            if (pass) anyPass = true;
        }

        if (isDefined(blockMin, blockMax)) {
            anyDefined = true;
            boolean pass = inRange(ctx.getLightBlock(), blockMin, blockMax);
            if (mode == AdjacentMatchMode.ALL && !pass) return false;
            if (pass) anyPass = true;
        }

        if (isDefined(blockIntensityMin, blockIntensityMax)) {
            anyDefined = true;
            boolean pass = inRange(ctx.getLightBlockIntensity(), blockIntensityMin, blockIntensityMax);
            if (mode == AdjacentMatchMode.ALL && !pass) return false;
            if (pass) anyPass = true;
        }

        if (isDefined(redMin, redMax)) {
            anyDefined = true;
            boolean pass = inRange(ctx.getLightRed(), redMin, redMax);
            if (mode == AdjacentMatchMode.ALL && !pass) return false;
            if (pass) anyPass = true;
        }

        if (isDefined(greenMin, greenMax)) {
            anyDefined = true;
            boolean pass = inRange(ctx.getLightGreen(), greenMin, greenMax);
            if (mode == AdjacentMatchMode.ALL && !pass) return false;
            if (pass) anyPass = true;
        }

        if (isDefined(blueMin, blueMax)) {
            anyDefined = true;
            boolean pass = inRange(ctx.getLightBlue(), blueMin, blueMax);
            if (mode == AdjacentMatchMode.ALL && !pass) return false;
            if (pass) anyPass = true;
        }

        if (!anyDefined) return true;
        return mode == AdjacentMatchMode.ALL || anyPass;
    }

    private static boolean isDefined(@Nullable Integer min, @Nullable Integer max) {
        return min != null || max != null;
    }

    private static boolean inRange(int value, @Nullable Integer min, @Nullable Integer max) {
        if (min != null && value < min) return false;
        if (max != null && value > max) return false;
        return true;
    }
}
