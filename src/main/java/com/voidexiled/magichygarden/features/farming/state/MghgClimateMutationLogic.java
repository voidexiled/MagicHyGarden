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
     * Nota: si raining && snowing y current==NONE, primero adquiere RAIN o SNOW (random order),
     * y luego puede intentar upgrade a FROZEN en el mismo tick si aplica.
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
        ClimateMutation next = current;

        // 1) Adquirir primera mutación desde NONE
        if (next == ClimateMutation.NONE) {
            if (raining && snowing) {
                // orden aleatorio para no sesgar siempre a rain o snow
                if (rnd.nextBoolean()) {
                    if (raining && rnd.nextDouble() < chanceRain) next = ClimateMutation.RAIN;
                    if (next == ClimateMutation.NONE && snowing && rnd.nextDouble() < chanceSnow) next = ClimateMutation.SNOW;
                } else {
                    if (snowing && rnd.nextDouble() < chanceSnow) next = ClimateMutation.SNOW;
                    if (next == ClimateMutation.NONE && raining && rnd.nextDouble() < chanceRain) next = ClimateMutation.RAIN;
                }
            } else if (raining) {
                if (rnd.nextDouble() < chanceRain) next = ClimateMutation.RAIN;
            } else if (snowing) {
                if (rnd.nextDouble() < chanceSnow) next = ClimateMutation.SNOW;
            }
        }

        // 2) Upgrade a FROZEN cuando llega el clima complementario
        if (next == ClimateMutation.RAIN && snowing) {
            if (rnd.nextDouble() < chanceFrozen) next = ClimateMutation.FROZEN;
        } else if (next == ClimateMutation.SNOW && raining) {
            if (rnd.nextDouble() < chanceFrozen) next = ClimateMutation.FROZEN;
        }

        return next;
    }
}
