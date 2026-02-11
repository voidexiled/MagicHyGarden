package com.voidexiled.magichygarden.features.farming.shop;

import com.voidexiled.magichygarden.features.farming.items.MghgCropMeta;

import javax.annotation.Nullable;

public final class MghgShopPricing {
    private MghgShopPricing() {
    }

    public static double computeUnitSellPrice(@Nullable MghgShopConfig.ShopItem item, @Nullable MghgCropMeta meta) {
        if (item == null) {
            return 0.0d;
        }
        double base = Math.max(0.0d, item.getSellPrice());
        if (!item.isEnableMetaSellPricing() || meta == null) {
            return base;
        }

        double sizeMultiplier = sanitizeMultiplier(computeSizeMultiplier(item, meta.getSize()));
        double climate = sanitizeMultiplier(item.getClimateMultiplier(meta.getClimate()));
        double lunar = sanitizeMultiplier(item.getLunarMultiplier(meta.getLunar()));
        double rarity = sanitizeMultiplier(item.getRarityMultiplier(meta.getRarity()));
        double rarityBase = Math.max(0.0d, base * rarity);
        double total = rarityBase * sizeMultiplier * climate * lunar;
        if (Double.isNaN(total) || Double.isInfinite(total)) {
            return base;
        }
        return Math.max(0.0d, total);
    }

    public static double computeSizeMultiplier(@Nullable MghgShopConfig.ShopItem item, int size) {
        if (item == null) {
            return 1.0d;
        }
        double minSize = item.getSellSizeMultiplierMinSize();
        double maxSize = item.getSellSizeMultiplierMaxSize();
        double minMultiplier = item.getSellSizeMultiplierAtMin();
        double maxMultiplier = item.getSellSizeMultiplierAtMax();

        if (Double.isNaN(minSize) || Double.isInfinite(minSize)) {
            minSize = 50.0d;
        }
        if (Double.isNaN(maxSize) || Double.isInfinite(maxSize)) {
            maxSize = 100.0d;
        }
        if (Double.isNaN(minMultiplier) || Double.isInfinite(minMultiplier)) {
            minMultiplier = 1.0d;
        }
        if (Double.isNaN(maxMultiplier) || Double.isInfinite(maxMultiplier)) {
            maxMultiplier = 2.0d;
        }

        if (maxSize < minSize) {
            double swapSize = minSize;
            minSize = maxSize;
            maxSize = swapSize;
            double swapMultiplier = minMultiplier;
            minMultiplier = maxMultiplier;
            maxMultiplier = swapMultiplier;
        }

        if (Math.abs(maxSize - minSize) < 0.0001d) {
            return minMultiplier;
        }

        double t = (Math.max(0.0d, size) - minSize) / (maxSize - minSize);
        t = Math.max(0.0d, Math.min(1.0d, t));
        return minMultiplier + ((maxMultiplier - minMultiplier) * t);
    }

    private static double sanitizeMultiplier(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 1.0d;
        }
        return Math.max(0.0d, value);
    }
}
