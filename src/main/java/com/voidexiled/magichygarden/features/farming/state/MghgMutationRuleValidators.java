package com.voidexiled.magichygarden.features.farming.state;

import com.hypixel.hytale.codec.schema.SchemaContext;
import com.hypixel.hytale.codec.schema.config.Schema;
import com.hypixel.hytale.codec.schema.config.StringSchema;
import com.hypixel.hytale.codec.schema.metadata.ui.UIDisplayMode;
import com.hypixel.hytale.codec.validation.ValidationResults;
import com.hypixel.hytale.codec.validation.Validator;

import javax.annotation.Nonnull;
import java.util.Locale;
import java.util.function.BiConsumer;

final class MghgMutationRuleValidators {
    private MghgMutationRuleValidators() {}

    static String[] climateValues() {
        return enumNames(ClimateMutation.values());
    }

    static String[] lunarValues() {
        return enumNames(LunarMutation.values());
    }

    static String[] rarityValues() {
        return enumNames(RarityMutation.values());
    }

    static String[] allMutationValues() {
        String[] climate = climateValues();
        String[] lunar = lunarValues();
        String[] rarity = rarityValues();
        String[] out = new String[climate.length + lunar.length + rarity.length];
        int idx = 0;
        for (String v : climate) out[idx++] = v;
        for (String v : lunar) out[idx++] = v;
        for (String v : rarity) out[idx++] = v;
        return out;
    }

    static Validator<String> enumHint(String[] values, String title) {
        return new EnumHintValidator(values, title);
    }

    static Validator<String> assetRefHint(String assetTypeName) {
        return new AssetRefHintValidator(assetTypeName);
    }

    static <T> Validator<T> hideInEditor() {
        return new HiddenFieldValidator<>();
    }

    static BiConsumer<MghgMutationRule, ValidationResults> slotSetValidator() {
        return (rule, results) -> {
            if (rule == null) return;
            String set = rule.getSet();
            MutationSlot slot = rule.getSlot() == null ? MutationSlot.CLIMATE : rule.getSlot();

            if (set == null || set.isBlank()) {
                results.fail("Set is required.");
                return;
            }

            boolean valid = switch (slot) {
                case CLIMATE -> isEnumValue(set, ClimateMutation.values());
                case LUNAR -> isEnumValue(set, LunarMutation.values());
                case RARITY -> isEnumValue(set, RarityMutation.values());
            };

            if (!valid) {
                String allowed = switch (slot) {
                    case CLIMATE -> String.join(", ", climateValues());
                    case LUNAR -> String.join(", ", lunarValues());
                    case RARITY -> String.join(", ", rarityValues());
                };
                results.fail("Set '" + set + "' is not valid for slot " + slot + ". Allowed: " + allowed);
            }
        };
    }

    private static String[] enumNames(Enum<?>[] values) {
        String[] out = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            out[i] = values[i].name();
        }
        return out;
    }

    private static boolean isEnumValue(String value, Enum<?>[] values) {
        if (value == null) return false;
        String needle = value.trim().toUpperCase(Locale.ROOT);
        for (Enum<?> v : values) {
            if (v != null && v.name().equalsIgnoreCase(needle)) {
                return true;
            }
        }
        return false;
    }

    private static final class EnumHintValidator implements Validator<String> {
        private final String[] values;
        private final String title;

        private EnumHintValidator(String[] values, String title) {
            this.values = values == null ? new String[0] : values;
            this.title = title;
        }

        @Override
        public void accept(String value, @Nonnull ValidationResults results) {
            if (value == null || value.isBlank()) {
                return;
            }
            String needle = value.trim().toUpperCase(Locale.ROOT);
            for (String v : values) {
                if (v != null && v.equalsIgnoreCase(needle)) {
                    return;
                }
            }
            results.fail("Invalid value '" + value + "'. Allowed: " + String.join(", ", values));
        }

        @Override
        public void updateSchema(SchemaContext context, @Nonnull Schema target) {
            if (target instanceof StringSchema stringSchema) {
                stringSchema.setEnum(values);
                if (title != null && !title.isBlank()) {
                    stringSchema.setTitle(title);
                }
                if (stringSchema.getHytale() != null) {
                    stringSchema.getHytale().setType("Enum");
                }
            }
        }
    }

    private static final class AssetRefHintValidator implements Validator<String> {
        private final String assetTypeName;

        private AssetRefHintValidator(String assetTypeName) {
            this.assetTypeName = assetTypeName;
        }

        @Override
        public void accept(String value, @Nonnull ValidationResults results) {
            // No hard validation: allows custom ids while still giving editor picker hints.
        }

        @Override
        public void updateSchema(SchemaContext context, @Nonnull Schema target) {
            target.setHytaleAssetRef(assetTypeName);
        }
    }

    private static final class HiddenFieldValidator<T> implements Validator<T> {
        @Override
        public void accept(T value, @Nonnull ValidationResults results) {
            // No validation; used to hide legacy fields in the editor.
        }

        @Override
        public void updateSchema(SchemaContext context, @Nonnull Schema target) {
            Schema.HytaleMetadata meta = target.getHytale(true);
            if (meta != null) {
                meta.setUiDisplayMode(UIDisplayMode.DisplayMode.HIDDEN);
            }
        }
    }
}
