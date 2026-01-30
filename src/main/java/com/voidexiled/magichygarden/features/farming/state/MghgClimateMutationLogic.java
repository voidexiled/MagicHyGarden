package com.voidexiled.magichygarden.features.farming.state;

import java.util.concurrent.ThreadLocalRandom;

public final class MghgClimateMutationLogic {
    private MghgClimateMutationLogic() {}

    /**
     * Reglas:
     * NONE -> RAIN si raining
     * NONE -> SNOW si snowing
     * RAIN + snowing -> FROZEN
     * SNOW + raining -> FROZEN
     * sin downgrade
     *
     * Nota: si raining && snowing y current==NONE, primero adquiere RAIN o SNOW (random order).
     * No salta directo a FROZEN desde NONE.
     */
    public static ClimateMutation computeNext(
            ClimateMutation current,
            boolean raining,
            boolean snowing,
            double chanceRain,
            double chanceSnow,
            double chanceFrozen
    ) {
        if (current == null) current = ClimateMutation.NONE;
        if (current == ClimateMutation.FROZEN) return ClimateMutation.FROZEN;

        ThreadLocalRandom rnd = ThreadLocalRandom.current();

        if (current == ClimateMutation.RAIN) {
            if (snowing && rnd.nextDouble() < chanceFrozen) return ClimateMutation.FROZEN;
            return ClimateMutation.RAIN;
        }

        if (current == ClimateMutation.SNOW) {
            if (raining && rnd.nextDouble() < chanceFrozen) return ClimateMutation.FROZEN;
            return ClimateMutation.SNOW;
        }

        // current == NONE: adquirir RAIN o SNOW según clima
        if (raining && snowing) {
            // orden aleatorio para no sesgar siempre a rain o snow
            if (rnd.nextBoolean()) {
                if (rnd.nextDouble() < chanceRain) return ClimateMutation.RAIN;
                if (rnd.nextDouble() < chanceSnow) return ClimateMutation.SNOW;
            } else {
                if (rnd.nextDouble() < chanceSnow) return ClimateMutation.SNOW;
                if (rnd.nextDouble() < chanceRain) return ClimateMutation.RAIN;
            }
            return ClimateMutation.NONE;
        }

        if (raining) {
            if (rnd.nextDouble() < chanceRain) return ClimateMutation.RAIN;
            return ClimateMutation.NONE;
        }

        if (snowing) {
            if (rnd.nextDouble() < chanceSnow) return ClimateMutation.SNOW;
            return ClimateMutation.NONE;
        }

        return ClimateMutation.NONE;
    }

    /**
     * Aplica una mutación manual "add" (debug):
     * - NONE + RAIN/SNOW => RAIN/SNOW
     * - RAIN + SNOW => FROZEN
     * - SNOW + RAIN => FROZEN
     * - FROZEN nunca baja
     * - add=NONE resetea a NONE
     *
     * No usa RNG: es determinístico.
     */
    public static ClimateMutation applyManualAdd(ClimateMutation current, ClimateMutation add) {
        if (current == null) current = ClimateMutation.NONE;
        if (add == null) add = ClimateMutation.NONE;

        if (add == ClimateMutation.NONE) {
            return ClimateMutation.NONE;
        }

        if (current == ClimateMutation.FROZEN) {
            return ClimateMutation.FROZEN;
        }

        if (add == ClimateMutation.FROZEN) {
            return ClimateMutation.FROZEN;
        }

        if (add == ClimateMutation.RAIN) {
            if (current == ClimateMutation.SNOW) return ClimateMutation.FROZEN;
            return (current == ClimateMutation.NONE) ? ClimateMutation.RAIN : current;
        }

        if (add == ClimateMutation.SNOW) {
            if (current == ClimateMutation.RAIN) return ClimateMutation.FROZEN;
            return (current == ClimateMutation.NONE) ? ClimateMutation.SNOW : current;
        }

        return current;
    }
}
