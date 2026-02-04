package com.voidexiled.magichygarden.features.farming.state;

import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.codec.util.RawJsonReader;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.voidexiled.magichygarden.features.farming.modifiers.MghgCropGrowthModifierAsset;
import it.unimi.dsi.fastutil.ints.IntSet;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public final class MghgMutationRules {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String RESOURCE_PATH = "Server/Farming/Mutations/Mghg_Mutations.json";
    private static final String DEFAULT_RULESET_ID = "Mghg_Mutations";

    private static volatile MghgMutationRuleSet CURRENT = new MghgMutationRuleSet(List.of(), CooldownClock.REAL_TIME);

    private MghgMutationRules() {}

    public static void reload() {
        try {
            MghgMutationRuleSet fromAssets = loadFromAssets(DEFAULT_RULESET_ID);
            if (fromAssets != null) {
                CURRENT = fromAssets;
                if (fromAssets.isEmpty()) {
                    LOGGER.atWarning().log(
                            "[MGHG|MUTATION_RULES] Ruleset '%s' is empty. Using fallback from GrowthModifier.",
                            DEFAULT_RULESET_ID
                    );
                } else {
                    LOGGER.atInfo().log("[MGHG|MUTATION_RULES] Loaded %d rules from asset '%s'",
                            fromAssets.getRules().size(), DEFAULT_RULESET_ID);
                }
                return;
            }

            MghgMutationRulesConfig config = loadLegacyConfig();
            if (config == null || config.getRules() == null || config.getRules().length == 0) {
                CURRENT = new MghgMutationRuleSet(List.of(), config == null ? CooldownClock.REAL_TIME : config.getCooldownClock());
                LOGGER.atWarning().log("[MGHG|MUTATION_RULES] No rules loaded. Using fallback from GrowthModifier.");
                return;
            }

            List<MghgMutationRule> rules = new ArrayList<>();
            for (MghgMutationRule rule : config.getRules()) {
                if (rule != null) {
                    rules.add(rule);
                }
            }
            CURRENT = new MghgMutationRuleSet(rules, config.getCooldownClock());
            LOGGER.atInfo().log("[MGHG|MUTATION_RULES] Loaded %d legacy rules", rules.size());
        } catch (Exception e) {
            CURRENT = new MghgMutationRuleSet(List.of(), CooldownClock.REAL_TIME);
            LOGGER.atSevere().withCause(e).log("[MGHG|MUTATION_RULES] Failed to reload rules. Using fallback from GrowthModifier.");
        }
    }

    public static MghgMutationRuleSet getActive(@Nullable MghgCropGrowthModifierAsset cfg) {
        if (CURRENT != null && !CURRENT.isEmpty()) {
            return CURRENT;
        }
        if (cfg == null) {
            return CURRENT;
        }
        return buildFallbackFromGrowth(cfg);
    }

    private static MghgMutationRuleSet buildFallbackFromGrowth(MghgCropGrowthModifierAsset cfg) {
        List<MghgMutationRule> rules = new ArrayList<>();

        IntSet rainIds = cfg.getRainWeatherIds();
        IntSet snowIds = cfg.getSnowWeatherIds();
        IntSet frozenIds = cfg.getFrozenWeatherIds();

        IntSet rainSet = mergeSets(rainIds, frozenIds);
        IntSet snowSet = mergeSets(snowIds, frozenIds);

        // Base rain/snow rules (NONE -> RAIN/SNOW)
        if (rainSet != null && cfg.getMutationChanceRain() > 0.0) {
            rules.add(MghgMutationRule.weatherRule(
                    "mghg_default_rain",
                    MutationSlot.CLIMATE,
                    ClimateMutation.RAIN.name(),
                    cfg.getMutationChanceRain(),
                    10,
                    1,
                    rainSet,
                    null,
                    new String[]{ClimateMutation.SNOW.name(), ClimateMutation.FROZEN.name(), ClimateMutation.RAIN.name()}
            ));
        }

        if (snowSet != null && cfg.getMutationChanceSnow() > 0.0) {
            rules.add(MghgMutationRule.weatherRule(
                    "mghg_default_snow",
                    MutationSlot.CLIMATE,
                    ClimateMutation.SNOW.name(),
                    cfg.getMutationChanceSnow(),
                    10,
                    1,
                    snowSet,
                    null,
                    new String[]{ClimateMutation.RAIN.name(), ClimateMutation.FROZEN.name(), ClimateMutation.SNOW.name()}
            ));
        }

        // Upgrade to frozen when opposite weather hits
        if (rainSet != null && cfg.getMutationChanceFrozen() > 0.0) {
            rules.add(MghgMutationRule.weatherRule(
                    "mghg_default_rain_to_frozen",
                    MutationSlot.CLIMATE,
                    ClimateMutation.FROZEN.name(),
                    cfg.getMutationChanceFrozen(),
                    20,
                    1,
                    rainSet,
                    new String[]{ClimateMutation.SNOW.name()},
                    null
            ));
        }

        if (snowSet != null && cfg.getMutationChanceFrozen() > 0.0) {
            rules.add(MghgMutationRule.weatherRule(
                    "mghg_default_snow_to_frozen",
                    MutationSlot.CLIMATE,
                    ClimateMutation.FROZEN.name(),
                    cfg.getMutationChanceFrozen(),
                    20,
                    1,
                    snowSet,
                    new String[]{ClimateMutation.RAIN.name()},
                    null
            ));
        }

        return new MghgMutationRuleSet(rules, CooldownClock.REAL_TIME);
    }

    private static @Nullable IntSet mergeSets(@Nullable IntSet a, @Nullable IntSet b) {
        if (a == null && b == null) return null;
        if (a == null) return b;
        if (b == null) return a;
        var merged = new it.unimi.dsi.fastutil.ints.IntOpenHashSet(a);
        merged.addAll(b);
        return merged;
    }

    private static @Nullable MghgMutationRuleSet loadFromAssets(String id) {
        DefaultAssetMap<String, MghgMutationRulesAsset> map = MghgMutationRulesAsset.getAssetMap();
        if (map == null) {
            LOGGER.atWarning().log("[MGHG|MUTATION_RULES] Asset store not registered; falling back to legacy JSON.");
            return null;
        }
        MghgMutationRulesAsset asset = map.getAsset(id);
        if (asset == null) {
            LOGGER.atWarning().log("[MGHG|MUTATION_RULES] Ruleset asset '%s' not found.", id);
            return null;
        }
        MghgMutationRule[] rules = asset.getRules();
        if (rules == null || rules.length == 0) {
            return new MghgMutationRuleSet(List.of(), asset.getCooldownClock());
        }
        List<MghgMutationRule> list = new ArrayList<>();
        for (MghgMutationRule rule : rules) {
            if (rule != null) {
                list.add(rule);
            }
        }
        return new MghgMutationRuleSet(list, asset.getCooldownClock());
    }

    private static @Nullable MghgMutationRulesConfig loadLegacyConfig() {
        Path buildPath = Paths.get("build", "resources", "main", "Server", "Farming", "Mutations", "Mghg_Mutations.json");
        if (Files.exists(buildPath)) {
            try {
                String payload = Files.readString(buildPath, StandardCharsets.UTF_8);
                RawJsonReader json = RawJsonReader.fromJsonString(payload);
                return MghgMutationRulesConfig.CODEC.decodeJson(json, new ExtraInfo());
            } catch (Exception e) {
                LOGGER.atWarning().log("[MGHG|MUTATION_RULES] Failed to load from build/resources: %s", e.getMessage());
            }
        } else {
            LOGGER.atWarning().log("[MGHG|MUTATION_RULES] No config found at %s or %s", RESOURCE_PATH, buildPath.toString());
        }

        try (InputStream stream = MghgMutationRules.class.getClassLoader().getResourceAsStream(RESOURCE_PATH)) {
            if (stream != null) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                    StringBuilder payload = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        payload.append(line).append('\n');
                    }
                    RawJsonReader json = RawJsonReader.fromJsonString(payload.toString());
                    return MghgMutationRulesConfig.CODEC.decodeJson(json, new ExtraInfo());
                }
            }
        } catch (Exception e) {
            LOGGER.atWarning().log("[MGHG|MUTATION_RULES] Failed to load from classpath: %s", e.getMessage());
        }

        return null;
    }
}
