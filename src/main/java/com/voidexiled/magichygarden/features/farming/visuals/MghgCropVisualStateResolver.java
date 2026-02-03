package com.voidexiled.magichygarden.features.farming.visuals;

import com.voidexiled.magichygarden.features.farming.components.MghgCropData;

public class MghgCropVisualStateResolver {
    private MghgCropVisualStateResolver() {
    }

    /**
     * Clave base usada tanto para:
     * - item state (ItemStack state)
     * - stage set (FarmingBlock.currentStageSet)
     *
     * Ej:
     *  - none + none      -> mghg_none
     *  - gold + rain      -> mghg_gold_rain
     *  - rainbow + frozen -> mghg_rainbow_rain_snow
     */
    public static String resolveVariantKey(MghgCropData data) {
        String prefix = switch (data.getRarity()) {
            case GOLD -> "mghg_gold_";
            case RAINBOW -> "mghg_rainbow_";
            case NONE -> "mghg_";
        };

        String climate = switch (data.getClimate()) {
            case RAIN -> "rain";
            case SNOW -> "snow";
            case FROZEN -> "frozen";
            case NONE -> "none";
        };

        return prefix + climate;
    }

    // Mantén tu API previa para items
    public static String resolveItemState(MghgCropData data) {
        return resolveVariantKey(data);
    }

    // Alias explícito para farming stage sets
    public static String resolveBlockStageSet(MghgCropData data) {
        return resolveVariantKey(data);
    }
}
