package com.voidexiled.magichygarden.features.farming.logic;

import com.voidexiled.magichygarden.features.farming.components.MghgCropData;
import com.voidexiled.magichygarden.features.farming.modifiers.MghgCropGrowthModifierAsset;
import com.voidexiled.magichygarden.features.farming.state.ClimateMutation;
import com.voidexiled.magichygarden.features.farming.state.LunarMutation;
import com.voidexiled.magichygarden.features.farming.state.RarityMutation;

import java.util.concurrent.ThreadLocalRandom;

public final class MghgCropDataSeeder {
    private MghgCropDataSeeder() {}

    private static final float DEFAULT_GOLD_CHANCE = 0.005f;
    private static final float DEFAULT_RAINBOW_CHANCE = 0.0005f;

    /**
     * Inicializa data de un crop recién plantado.
     * Fuente de verdad: MghgCropGrowthModifierAsset (si está cargado).
     * - size: [SizeMin, SizeMax]
     * - rarity: chances iniciales
     * - climate: NONE
     * - lastMutationRoll: null (el motor arma el cooldown en el primer tick elegible
     *   salvo que la regla tenga SkipInitialCooldown=true)
     */
    public static void seedNew(MghgCropData data) {
        applySeed(data, 0.0);
    }

    public static void seedNew(MghgCropData data, double baseWeightGrams) {
        applySeed(data, baseWeightGrams);
    }

    /**
     * Re-seed cuando el crop se replanta tras harvest.
     * Misma configuración que seedNew para evitar divergencias.
     */
    public static void seedReplant(MghgCropData data) {
        applySeed(data, 0.0);
    }

    public static void seedReplant(MghgCropData data, double baseWeightGrams) {
        applySeed(data, baseWeightGrams);
    }

    private static void applySeed(MghgCropData data, double baseWeightGrams) {
        MghgCropGrowthModifierAsset cfg = MghgCropGrowthModifierAsset.getLastLoaded();

        int min = (cfg != null ? cfg.getSizeMin() : MghgCropGrowthModifierAsset.DEFAULT_SIZE_MIN);
        int max = (cfg != null ? cfg.getSizeMax() : MghgCropGrowthModifierAsset.DEFAULT_SIZE_MAX);
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
        int size = ThreadLocalRandom.current().nextInt(min, max + 1);
        data.setSize(size);
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

        // Peso inicial: 0g (crece junto con el progreso del crop).
        data.setWeightGrams(0.0);
    }
}
