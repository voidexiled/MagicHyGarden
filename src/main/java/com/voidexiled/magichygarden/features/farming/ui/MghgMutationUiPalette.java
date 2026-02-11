package com.voidexiled.magichygarden.features.farming.ui;

import com.voidexiled.magichygarden.features.farming.state.ClimateMutation;
import com.voidexiled.magichygarden.features.farming.state.LunarMutation;
import com.voidexiled.magichygarden.features.farming.state.RarityMutation;

import javax.annotation.Nullable;
import java.util.Locale;

public final class MghgMutationUiPalette {
    private MghgMutationUiPalette() {
    }

    public static @Nullable String colorForClimate(@Nullable ClimateMutation climate) {
        if (climate == null) {
            return null;
        }
        return switch (climate) {
            case RAIN -> "#55b7ff";
            case SNOW -> "#bfe7ff";
            case FROZEN -> "#7ff1ff";
            default -> null;
        };
    }

    public static @Nullable String colorForClimate(@Nullable String climateKey) {
        String key = normalize(climateKey);
        if (key == null) {
            return null;
        }
        return switch (key) {
            case "RAIN", "WET" -> "#55b7ff";
            case "SNOW", "CHILLED" -> "#bfe7ff";
            case "FROZEN" -> "#7ff1ff";
            default -> null;
        };
    }

    public static @Nullable String colorForLunar(@Nullable LunarMutation lunar) {
        if (lunar == null) {
            return null;
        }
        return switch (lunar) {
            case DAWNLIT -> "#f3a6ff";
            case DAWNBOUND -> "#d57dff";
            case AMBERLIT -> "#ffb357";
            case AMBERBOUND -> "#ff8c42";
            default -> null;
        };
    }

    public static @Nullable String colorForLunar(@Nullable String lunarKey) {
        String key = normalize(lunarKey);
        if (key == null) {
            return null;
        }
        return switch (key) {
            case "DAWNLIT" -> "#f3a6ff";
            case "DAWNBOUND" -> "#d57dff";
            case "AMBERLIT" -> "#ffb357";
            case "AMBERBOUND" -> "#ff8c42";
            default -> null;
        };
    }

    public static @Nullable String colorForRarity(@Nullable RarityMutation rarity) {
        if (rarity == null) {
            return null;
        }
        return switch (rarity) {
            case GOLD -> "#ffd24a";
            case RAINBOW -> "#8be9ff";
            default -> null;
        };
    }

    public static @Nullable String colorForRarity(@Nullable String rarityKey) {
        String key = normalize(rarityKey);
        if (key == null) {
            return null;
        }
        return switch (key) {
            case "GOLD" -> "#ffd24a";
            case "RAINBOW" -> "#8be9ff";
            default -> null;
        };
    }

    public static String colorForMultiplier(double multiplier) {
        if (Double.isNaN(multiplier) || Double.isInfinite(multiplier)) {
            return "#9fb6d1";
        }
        if (multiplier < 1.0d) {
            return "#9aa8b8";
        }
        if (Math.abs(multiplier - 1.0d) < 0.0001d) {
            return "#ffffff";
        }
        if (multiplier < 1.5d) {
            return "#8ed7a8";
        }
        if (multiplier < 2.0d) {
            return "#79d8ff";
        }
        if (multiplier < 5.0d) {
            return "#66b3ff";
        }
        if (multiplier < 10.0d) {
            return "#b28cff";
        }
        if (multiplier < 20.0d) {
            return "#ffb36b";
        }
        return "#ff8a8a";
    }

    private static @Nullable String normalize(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }
}

