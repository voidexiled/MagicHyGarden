package com.voidexiled.magichygarden.features.farming.state;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

import javax.annotation.Nullable;

public final class MghgTimeRequirement {
    public static final BuilderCodec<MghgTimeRequirement> CODEC =
            BuilderCodec.builder(MghgTimeRequirement.class, MghgTimeRequirement::new)
                    .append(new KeyedCodec<>("HourMin", Codec.INTEGER, true),
                            (o, v) -> o.hourMin = v, o -> o.hourMin)
                    .documentation("Min hour (0-23).")
                    .add()
                    .append(new KeyedCodec<>("HourMax", Codec.INTEGER, true),
                            (o, v) -> o.hourMax = v, o -> o.hourMax)
                    .documentation("Max hour (0-23). If HourMax < HourMin, range wraps across midnight.")
                    .add()
                    .append(new KeyedCodec<>("SunlightMin", Codec.DOUBLE, true),
                            (o, v) -> o.sunlightMin = v, o -> o.sunlightMin)
                    .documentation("Min sunlight factor (0.0-1.0).")
                    .add()
                    .append(new KeyedCodec<>("SunlightMax", Codec.DOUBLE, true),
                            (o, v) -> o.sunlightMax = v, o -> o.sunlightMax)
                    .documentation("Max sunlight factor (0.0-1.0).")
                    .add()
                    .build();

    @Nullable private Integer hourMin;
    @Nullable private Integer hourMax;
    @Nullable private Double sunlightMin;
    @Nullable private Double sunlightMax;

    public boolean matches(MghgMutationContext ctx) {
        if (ctx == null) return false;

        if (hourMin != null || hourMax != null) {
            int hour = ctx.getCurrentHour();
            if (hour < 0) return false;
            int min = hourMin == null ? 0 : hourMin;
            int max = hourMax == null ? 23 : hourMax;
            if (!matchesHour(hour, min, max)) return false;
        }

        if (sunlightMin != null || sunlightMax != null) {
            double sun = ctx.getSunlightFactor();
            if (sun < 0.0) return false;
            if (sunlightMin != null && sun < sunlightMin) return false;
            if (sunlightMax != null && sun > sunlightMax) return false;
        }

        return true;
    }

    private static boolean matchesHour(int hour, int min, int max) {
        if (min <= max) {
            return hour >= min && hour <= max;
        }
        return hour >= min || hour <= max;
    }
}
