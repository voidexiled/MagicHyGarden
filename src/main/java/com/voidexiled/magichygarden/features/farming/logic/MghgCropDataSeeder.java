package com.voidexiled.magichygarden.features.farming.logic;

import com.voidexiled.magichygarden.features.farming.components.MghgCropData;
import com.voidexiled.magichygarden.features.farming.modifiers.MghgCropGrowthModifierAsset;
import com.voidexiled.magichygarden.features.farming.state.ClimateMutation;
import com.voidexiled.magichygarden.features.farming.state.LunarMutation;
import com.voidexiled.magichygarden.features.farming.state.RarityMutation;

import java.util.concurrent.ThreadLocalRandom;

public final class MghgCropDataSeeder {
    private MghgCropDataSeeder() {}

    private static final int DEFAULT_SIZE_MIN = 50;
    private static final int DEFAULT_SIZE_MAX = 100;
    private static final float DEFAULT_GOLD_CHANCE = 0.005f;
    private static final float DEFAULT_RAINBOW_CHANCE = 0.0005f;

    /**
     * Inicializa data de un crop recién plantado.
     * Fuente de verdad: MghgCropGrowthModifierAsset (si está cargado).
     * - size: [SizeMin, SizeMax]
     * - rarity: chances iniciales
     * - climate: NONE
     * - lastMutationRoll: null (permite mutación inmediata si hay clima)
     */
    public static void seedNew(MghgCropData data) {
        applySeed(data);
    }

    /**
     * Re-seed cuando el crop se replanta tras harvest.
     * Misma configuración que seedNew para evitar divergencias.
     */
    public static void seedReplant(MghgCropData data) {
        applySeed(data);
    }

    private static void applySeed(MghgCropData data) {
        MghgCropGrowthModifierAsset cfg = MghgCropGrowthModifierAsset.getLastLoaded();

        int min = (cfg != null ? cfg.getSizeMin() : DEFAULT_SIZE_MIN);
        int max = (cfg != null ? cfg.getSizeMax() : DEFAULT_SIZE_MAX);
        if (max < min) {
            int tmp = min;
            min = max;
            max = tmp;
        }

        float goldChance = (cfg != null ? cfg.getInitialRarityGoldChance() : DEFAULT_GOLD_CHANCE);
        float rainbowChance = (cfg != null ? cfg.getInitialRarityRainbowChance() : DEFAULT_RAINBOW_CHANCE);
        if (goldChance < 0f) goldChance = 0f;
        if (rainbowChance < 0f) rainbowChance = 0f;

        // size inicial dentro del rango configurado
        data.setSize(ThreadLocalRandom.current().nextInt(min, max + 1));
        data.setClimate(ClimateMutation.NONE);
        data.setLunar(LunarMutation.NONE);

        // rarity inicial mutuamente excluyente
        float r = ThreadLocalRandom.current().nextFloat();
        if (r < rainbowChance) data.setRarity(RarityMutation.RAINBOW);
        else if (r < rainbowChance + goldChance) data.setRarity(RarityMutation.GOLD);
        else data.setRarity(RarityMutation.NONE);

        // reset: permite mutar sin esperar cooldown
        data.setLastRegularRoll(null);
        data.setLastLunarRoll(null);
        data.setLastSpecialRoll(null);
        data.setLastMutationRoll(null);
    }
}
