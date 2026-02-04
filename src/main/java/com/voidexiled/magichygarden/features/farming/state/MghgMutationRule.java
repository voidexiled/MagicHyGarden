package com.voidexiled.magichygarden.features.farming.state;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.EnumCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;
import com.hypixel.hytale.codec.validation.validator.ArrayValidator;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.weather.config.Weather;
import com.voidexiled.magichygarden.features.farming.components.MghgCropData;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class MghgMutationRule {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    // Cambia a false cuando termines de debuguear.
    private static final boolean DEBUG = false;
    // Dejar vacio para loggear todas las reglas con RequiresAdjacentItems.
    private static final String DEBUG_RULE_ID = "";

    public static final BuilderCodec<MghgMutationRule> CODEC =
            BuilderCodec.builder(MghgMutationRule.class, MghgMutationRule::new)
                    .append(new KeyedCodec<>("Id", Codec.STRING, true),
                            (o, v) -> o.id = v, o -> o.id)
                    .documentation("Optional rule id for debugging/logs.")
                    .add()
                    .append(new KeyedCodec<>("EventType", new EnumCodec<>(MutationEventType.class)),
                            (o, v) -> o.eventType = v == null ? MutationEventType.WEATHER : v,
                            o -> o.eventType)
                    .documentation("Event filter: WEATHER | PET | MANUAL | ANY. Default: WEATHER.")
                    .add()
                    .append(new KeyedCodec<>("WeatherIds", Codec.STRING_ARRAY, true),
                            (o, v) -> o.weatherIds = v, o -> o.weatherIds)
                    .documentation("Weather asset ids this rule reacts to (only for EventType=WEATHER). Empty = any weather.")
                    .addValidator(new ArrayValidator<>(MghgMutationRuleValidators.assetRefHint("Weather")))
                    .add()
                    .append(new KeyedCodec<>("Slot", new EnumCodec<>(MutationSlot.class)),
                            (o, v) -> o.slot = v == null ? MutationSlot.CLIMATE : v,
                            o -> o.slot)
                    .documentation("Target slot: CLIMATE | LUNAR | RARITY. Default: CLIMATE.")
                    .add()
                    .append(new KeyedCodec<>("Set", Codec.STRING),
                            (o, v) -> o.set = v, o -> o.set)
                    .documentation("Value to apply (must match the enum of the target slot).")
                    .addValidator(MghgMutationRuleValidators.enumHint(
                            MghgMutationRuleValidators.allMutationValues(),
                            "MutationValue"
                    ))
                    .add()
                    .append(new KeyedCodec<>("Chance", Codec.DOUBLE, true),
                            (o, v) -> o.chance = v == null ? o.chance : v,
                            o -> o.chance)
                    .documentation("Roll chance between 0.0 and 1.0.")
                    .add()
                    .append(new KeyedCodec<>("Priority", Codec.INTEGER, true),
                            (o, v) -> o.priority = v == null ? o.priority : v,
                            o -> o.priority)
                    .documentation("Higher priority wins per slot.")
                    .add()
                    .append(new KeyedCodec<>("Weight", Codec.INTEGER, true),
                            (o, v) -> o.weight = v == null ? o.weight : v,
                            o -> o.weight)
                    .documentation("Tie-break weight when priorities match.")
                    .add()
                    .append(new KeyedCodec<>("CooldownSeconds", Codec.INTEGER, true),
                            (o, v) -> o.cooldownSeconds = v == null ? o.cooldownSeconds : v,
                            o -> o.cooldownSeconds)
                    .documentation("Cooldown per slot. If omitted, uses default cooldown from Size.json.")
                    .add()
                    .append(new KeyedCodec<>("SkipInitialCooldown", Codec.BOOLEAN, true),
                            (o, v) -> o.skipInitialCooldown = v != null && v,
                            o -> o.skipInitialCooldown)
                    .documentation("If true, the first eligible tick can roll immediately (ignores initial cooldown arming).")
                    .add()
                    .append(new KeyedCodec<>("RequiresMature", Codec.BOOLEAN, true),
                            (o, v) -> o.requiresMature = v != null && v,
                            o -> o.requiresMature)
                    .documentation("If true, rule only applies to harvestable crops.")
                    .add()
                    .append(new KeyedCodec<>("RequiresPlayerOnline", Codec.BOOLEAN, true),
                            (o, v) -> o.requiresPlayerOnline = v != null && v,
                            o -> o.requiresPlayerOnline)
                    .documentation("If true, rule only applies while the owner is online.")
                    .add()
                    .append(new KeyedCodec<>("IgnoreSkyCheck", Codec.BOOLEAN, true),
                            (o, v) -> o.ignoreSkyCheck = v != null && v,
                            o -> o.ignoreSkyCheck)
                    .documentation("If true, weather lookup ignores blocks above the crop.")
                    .add()
                    .append(new KeyedCodec<>("RequiresLight", MghgLightRequirement.CODEC, true),
                            (o, v) -> o.requiresLight = v, o -> o.requiresLight)
                    .documentation("Light requirements (sky/block/RGB).")
                    .add()
                    .append(new KeyedCodec<>("RequiresTime", MghgTimeRequirement.CODEC, true),
                            (o, v) -> o.requiresTime = v, o -> o.requiresTime)
                    .documentation("Time requirements (hour range / sunlight factor).")
                    .add()
                    .append(new KeyedCodec<>("MinY", Codec.INTEGER, true),
                            (o, v) -> o.minY = v, o -> o.minY)
                    .documentation("Min world Y for this rule to apply.")
                    .add()
                    .append(new KeyedCodec<>("MaxY", Codec.INTEGER, true),
                            (o, v) -> o.maxY = v, o -> o.maxY)
                    .documentation("Max world Y for this rule to apply.")
                    .add()
                    .append(new KeyedCodec<>("RequiresStageSets", Codec.STRING_ARRAY, true),
                            (o, v) -> o.requiresStageSets = v, o -> o.requiresStageSets)
                    .documentation("Stage sets required (matches FarmingBlock.currentStageSet).")
                    .add()
                    .append(new KeyedCodec<>("MinStageIndex", Codec.INTEGER, true),
                            (o, v) -> o.minStageIndex = v, o -> o.minStageIndex)
                    .documentation("Min stage index required.")
                    .add()
                    .append(new KeyedCodec<>("MaxStageIndex", Codec.INTEGER, true),
                            (o, v) -> o.maxStageIndex = v, o -> o.maxStageIndex)
                    .documentation("Max stage index required.")
                    .add()
                    .append(new KeyedCodec<>("RequiresSoilBlockIds", Codec.STRING_ARRAY, true),
                            (o, v) -> o.requiresSoilBlockIds = v, o -> o.requiresSoilBlockIds)
                    .documentation("Requires the block below to match one of these ids.")
                    .addValidator(new ArrayValidator<>(MghgMutationRuleValidators.assetRefHint("BlockType")))
                    .add()
                    .append(new KeyedCodec<>("MustHaveClimate", Codec.STRING_ARRAY, true),
                            (o, v) -> o.mustHaveClimate = v, o -> o.mustHaveClimate)
                    .documentation("Required climate values for the rule to apply.")
                    .addValidator(new ArrayValidator<>(MghgMutationRuleValidators.enumHint(
                            MghgMutationRuleValidators.climateValues(), "ClimateMutation"
                    )))
                    .add()
                    .append(new KeyedCodec<>("MustNotHaveClimate", Codec.STRING_ARRAY, true),
                            (o, v) -> o.mustNotHaveClimate = v, o -> o.mustNotHaveClimate)
                    .documentation("Blocked climate values for the rule to apply.")
                    .addValidator(new ArrayValidator<>(MghgMutationRuleValidators.enumHint(
                            MghgMutationRuleValidators.climateValues(), "ClimateMutation"
                    )))
                    .add()
                    .append(new KeyedCodec<>("MustHaveLunar", Codec.STRING_ARRAY, true),
                            (o, v) -> o.mustHaveLunar = v, o -> o.mustHaveLunar)
                    .documentation("Required lunar values for the rule to apply.")
                    .addValidator(new ArrayValidator<>(MghgMutationRuleValidators.enumHint(
                            MghgMutationRuleValidators.lunarValues(), "LunarMutation"
                    )))
                    .add()
                    .append(new KeyedCodec<>("MustNotHaveLunar", Codec.STRING_ARRAY, true),
                            (o, v) -> o.mustNotHaveLunar = v, o -> o.mustNotHaveLunar)
                    .documentation("Blocked lunar values for the rule to apply.")
                    .addValidator(new ArrayValidator<>(MghgMutationRuleValidators.enumHint(
                            MghgMutationRuleValidators.lunarValues(), "LunarMutation"
                    )))
                    .add()
                    .append(new KeyedCodec<>("MustHaveRarity", Codec.STRING_ARRAY, true),
                            (o, v) -> o.mustHaveRarity = v, o -> o.mustHaveRarity)
                    .documentation("Required rarity values for the rule to apply.")
                    .addValidator(new ArrayValidator<>(MghgMutationRuleValidators.enumHint(
                            MghgMutationRuleValidators.rarityValues(), "RarityMutation"
                    )))
                    .add()
                    .append(new KeyedCodec<>("MustNotHaveRarity", Codec.STRING_ARRAY, true),
                            (o, v) -> o.mustNotHaveRarity = v, o -> o.mustNotHaveRarity)
                    .documentation("Blocked rarity values for the rule to apply.")
                    .addValidator(new ArrayValidator<>(MghgMutationRuleValidators.enumHint(
                            MghgMutationRuleValidators.rarityValues(), "RarityMutation"
                    )))
                    .add()
                    .append(new KeyedCodec<>("RequiresAdjacentBlockIds", Codec.STRING_ARRAY, true),
                            (o, v) -> o.requiresAdjacentBlockIds = v, o -> o.requiresAdjacentBlockIds)
                    .documentation("Requires at least one adjacent block with any of these ids (6 directions).")
                    .addValidator(new ArrayValidator<>(MghgMutationRuleValidators.assetRefHint("BlockType")))
                    .addValidator(MghgMutationRuleValidators.hideInEditor())
                    .add()
                    .append(new KeyedCodec<>("AdjacentBlockMatch", new EnumCodec<>(AdjacentMatchMode.class), true),
                            (o, v) -> o.adjacentBlockMatch = v == null ? AdjacentMatchMode.ANY : v,
                            o -> o.adjacentBlockMatch)
                    .documentation("How to match RequiresAdjacentBlockIds: ANY or ALL. Default: ANY.")
                    .addValidator(MghgMutationRuleValidators.hideInEditor())
                    .add()
                    .append(new KeyedCodec<>("RequiresAdjacentItems",
                                    ArrayCodec.ofBuilderCodec(MghgAdjacentItemRequirement.CODEC, MghgAdjacentItemRequirement[]::new),
                                    true),
                            (o, v) -> o.requiresAdjacentItems = v, o -> o.requiresAdjacentItems)
                    .documentation("Advanced adjacency requirements (radius/offset). Each entry can target item ids or block ids.")
                    .add()
                    .append(new KeyedCodec<>("AdjacentItemsMatch", new EnumCodec<>(AdjacentMatchMode.class), true),
                            (o, v) -> o.adjacentItemsMatch = v == null ? AdjacentMatchMode.ANY : v,
                            o -> o.adjacentItemsMatch)
                    .documentation("How to match RequiresAdjacentItems: ANY or ALL. Default: ANY.")
                    .add()
                    .append(new KeyedCodec<>("RequiresAdjacentParticles",
                                    ArrayCodec.ofBuilderCodec(MghgAdjacentParticleRequirement.CODEC, MghgAdjacentParticleRequirement[]::new),
                                    true),
                            (o, v) -> o.requiresAdjacentParticles = v, o -> o.requiresAdjacentParticles)
                    .documentation("Advanced particle proximity requirements (runtime packets + block particle assets).")
                    .add()
                    .append(new KeyedCodec<>("AdjacentParticlesMatch", new EnumCodec<>(AdjacentMatchMode.class), true),
                            (o, v) -> o.adjacentParticlesMatch = v == null ? AdjacentMatchMode.ANY : v,
                            o -> o.adjacentParticlesMatch)
                    .documentation("How to match RequiresAdjacentParticles: ANY or ALL. Default: ANY.")
                    .add()
                    .validator(MghgMutationRuleValidators.slotSetValidator())
                    .build();

    private String id;
    private MutationEventType eventType = MutationEventType.WEATHER;
    @Nullable private String[] weatherIds;
    private MutationSlot slot = MutationSlot.CLIMATE;
    private String set;
    private double chance = 0.0;
    private int priority = 0;
    private int weight = 1;
    private int cooldownSeconds = -1;
    private boolean skipInitialCooldown;
    private boolean requiresMature;
    private boolean requiresPlayerOnline;
    private boolean ignoreSkyCheck;
    @Nullable private MghgLightRequirement requiresLight;
    @Nullable private MghgTimeRequirement requiresTime;
    @Nullable private Integer minY;
    @Nullable private Integer maxY;
    @Nullable private String[] requiresStageSets;
    @Nullable private Integer minStageIndex;
    @Nullable private Integer maxStageIndex;
    @Nullable private String[] requiresSoilBlockIds;
    @Nullable private String[] mustHaveClimate;
    @Nullable private String[] mustNotHaveClimate;
    @Nullable private String[] mustHaveLunar;
    @Nullable private String[] mustNotHaveLunar;
    @Nullable private String[] mustHaveRarity;
    @Nullable private String[] mustNotHaveRarity;
    @Nullable private String[] requiresAdjacentBlockIds;
    private AdjacentMatchMode adjacentBlockMatch = AdjacentMatchMode.ANY;
    @Nullable private MghgAdjacentItemRequirement[] requiresAdjacentItems;
    private AdjacentMatchMode adjacentItemsMatch = AdjacentMatchMode.ANY;
    @Nullable private MghgAdjacentParticleRequirement[] requiresAdjacentParticles;
    private AdjacentMatchMode adjacentParticlesMatch = AdjacentMatchMode.ANY;

    // Cached
    @Nullable private IntSet weatherIdSet;
    @Nullable private EnumSet<ClimateMutation> mustHaveClimateSet;
    @Nullable private EnumSet<ClimateMutation> mustNotHaveClimateSet;
    @Nullable private EnumSet<LunarMutation> mustHaveLunarSet;
    @Nullable private EnumSet<LunarMutation> mustNotHaveLunarSet;
    @Nullable private EnumSet<RarityMutation> mustHaveRaritySet;
    @Nullable private EnumSet<RarityMutation> mustNotHaveRaritySet;
    @Nullable private Set<String> requiresSoilBlockIdSet;
    @Nullable private Set<String> requiresStageSetSet;
    @Nullable private ClimateMutation setClimate;
    @Nullable private LunarMutation setLunar;
    @Nullable private RarityMutation setRarity;

    public void resolveCaches() {
        if (weatherIdSet == null) {
            weatherIdSet = resolveWeatherIds(weatherIds);
        }
        mustHaveClimateSet = resolveClimateSet(mustHaveClimate);
        mustNotHaveClimateSet = resolveClimateSet(mustNotHaveClimate);
        mustHaveLunarSet = resolveLunarSet(mustHaveLunar);
        mustNotHaveLunarSet = resolveLunarSet(mustNotHaveLunar);
        mustHaveRaritySet = resolveRaritySet(mustHaveRarity);
        mustNotHaveRaritySet = resolveRaritySet(mustNotHaveRarity);
        requiresSoilBlockIdSet = resolveBlockIdSet(requiresSoilBlockIds);
        requiresStageSetSet = resolveStageSetSet(requiresStageSets);

        if (set != null) {
            switch (slot) {
                case CLIMATE -> setClimate = parseClimate(set);
                case LUNAR -> setLunar = parseLunar(set);
                case RARITY -> setRarity = parseRarity(set);
            }
        }

        if (requiresAdjacentItems != null) {
            for (MghgAdjacentItemRequirement req : requiresAdjacentItems) {
                if (req != null) {
                    req.resolveCaches();
                }
            }
        }
        if (requiresAdjacentParticles != null) {
            for (MghgAdjacentParticleRequirement req : requiresAdjacentParticles) {
                if (req != null) {
                    req.resolveCaches();
                }
            }
        }
    }

    static MghgMutationRule weatherRule(
            String id,
            MutationSlot slot,
            String set,
            double chance,
            int priority,
            int weight,
            @Nullable IntSet weatherIdSet,
            @Nullable String[] mustHaveClimate,
            @Nullable String[] mustNotHaveClimate
    ) {
        MghgMutationRule rule = new MghgMutationRule();
        rule.id = id;
        rule.eventType = MutationEventType.WEATHER;
        rule.slot = slot == null ? MutationSlot.CLIMATE : slot;
        rule.set = set;
        rule.chance = chance;
        rule.priority = priority;
        rule.weight = weight;
        rule.weatherIdSet = weatherIdSet;
        rule.mustHaveClimate = mustHaveClimate;
        rule.mustNotHaveClimate = mustNotHaveClimate;
        rule.resolveCaches();
        return rule;
    }

    public String getId() { return id; }
    public MutationEventType getEventType() { return eventType; }
    public MutationSlot getSlot() { return slot; }
    public @Nullable String getSet() { return set; }
    public double getChance() { return chance; }
    public int getPriority() { return priority; }
    public int getWeight() { return weight <= 0 ? 1 : weight; }
    public boolean isSkipInitialCooldown() { return skipInitialCooldown; }
    public boolean isRequiresMature() { return requiresMature; }
    public boolean isRequiresPlayerOnline() { return requiresPlayerOnline; }
    public boolean isIgnoreSkyCheck() { return ignoreSkyCheck; }
    public @Nullable MghgLightRequirement getRequiresLight() { return requiresLight; }
    public @Nullable MghgTimeRequirement getRequiresTime() { return requiresTime; }
    public @Nullable Integer getMinY() { return minY; }
    public @Nullable Integer getMaxY() { return maxY; }
    public @Nullable String[] getRequiresStageSets() { return requiresStageSets; }
    public @Nullable Integer getMinStageIndex() { return minStageIndex; }
    public @Nullable Integer getMaxStageIndex() { return maxStageIndex; }
    public @Nullable String[] getRequiresSoilBlockIds() { return requiresSoilBlockIds; }
    public int getCooldownSecondsOrDefault(int def) { return cooldownSeconds >= 0 ? cooldownSeconds : def; }
    public @Nullable String[] getRequiresAdjacentBlockIds() { return requiresAdjacentBlockIds; }
    public AdjacentMatchMode getAdjacentBlockMatch() { return adjacentBlockMatch; }
    public @Nullable MghgAdjacentItemRequirement[] getRequiresAdjacentItems() { return requiresAdjacentItems; }
    public AdjacentMatchMode getAdjacentItemsMatch() { return adjacentItemsMatch; }
    public @Nullable MghgAdjacentParticleRequirement[] getRequiresAdjacentParticles() { return requiresAdjacentParticles; }
    public AdjacentMatchMode getAdjacentParticlesMatch() { return adjacentParticlesMatch; }

    public boolean matchesEvent(MghgMutationContext ctx) {
        if (eventType == MutationEventType.ANY) return true;
        if (ctx == null) return false;
        if (eventType != ctx.getEventType()) return false;
        if (eventType != MutationEventType.WEATHER) return true;
        int weatherId = ignoreSkyCheck ? ctx.getWeatherIdIgnoreSky() : ctx.getWeatherId();
        if (weatherIdSet == null || weatherIdSet.isEmpty()) return true;
        return weatherIdSet.contains(weatherId);
    }

    public boolean matchesRequirements(MghgCropData data, MghgMutationContext ctx) {
        if (data == null) return false;
        if (requiresMature && (ctx == null || !ctx.isMature())) return false;
        if (requiresPlayerOnline && (ctx == null || !ctx.isPlayerOnline())) return false;
        if (requiresLight != null && (ctx == null || !requiresLight.matches(ctx))) return false;
        if (requiresTime != null && (ctx == null || !requiresTime.matches(ctx))) return false;
        if (minY != null && (ctx == null || ctx.getY() < minY)) return false;
        if (maxY != null && (ctx == null || ctx.getY() > maxY)) return false;
        if (requiresStageSetSet != null) {
            String stageSet = ctx == null ? null : ctx.getStageSet();
            if (stageSet == null) return false;
            String key = stageSet.trim().toLowerCase(Locale.ROOT);
            if (!requiresStageSetSet.contains(key)) return false;
        }
        if (minStageIndex != null || maxStageIndex != null) {
            int stageIndex = ctx == null ? -1 : ctx.getStageIndex();
            if (stageIndex < 0) return false;
            if (minStageIndex != null && stageIndex < minStageIndex) return false;
            if (maxStageIndex != null && stageIndex > maxStageIndex) return false;
        }
        if (requiresSoilBlockIdSet != null) {
            String soilId = ctx == null ? null : ctx.getSoilBlockId();
            if (soilId == null || !requiresSoilBlockIdSet.contains(soilId)) return false;
        }
        if (mustHaveClimateSet != null && !mustHaveClimateSet.contains(data.getClimate())) return false;
        if (mustNotHaveClimateSet != null && mustNotHaveClimateSet.contains(data.getClimate())) return false;
        if (mustHaveLunarSet != null && !mustHaveLunarSet.contains(data.getLunar())) return false;
        if (mustNotHaveLunarSet != null && mustNotHaveLunarSet.contains(data.getLunar())) return false;
        if (mustHaveRaritySet != null && !mustHaveRaritySet.contains(data.getRarity())) return false;
        if (mustNotHaveRaritySet != null && mustNotHaveRaritySet.contains(data.getRarity())) return false;
        boolean hasAdvancedAdjacent = requiresAdjacentItems != null && requiresAdjacentItems.length > 0;
        if (!hasAdvancedAdjacent && requiresAdjacentBlockIds != null && requiresAdjacentBlockIds.length > 0) {
            if (ctx == null || !ctx.matchesAdjacentBlocks(requiresAdjacentBlockIds, adjacentBlockMatch)) return false;
        }
        if (hasAdvancedAdjacent) {
            boolean ok = ctx != null && ctx.matchesAdjacentItems(requiresAdjacentItems, adjacentItemsMatch);
            if (DEBUG && (DEBUG_RULE_ID.isEmpty() || (id != null && id.equalsIgnoreCase(DEBUG_RULE_ID)))) {
                LOGGER.atWarning().log(
                        "[MGHG|ADJ_RULE] id=%s ok=%s pos=%d,%d,%d mode=%s reqs=%d",
                        id, ok,
                        ctx == null ? -1 : ctx.getX(),
                        ctx == null ? -1 : ctx.getY(),
                        ctx == null ? -1 : ctx.getZ(),
                        adjacentItemsMatch,
                        requiresAdjacentItems == null ? 0 : requiresAdjacentItems.length
                );
                if (requiresAdjacentItems != null) {
                    for (int i = 0; i < requiresAdjacentItems.length; i++) {
                        MghgAdjacentItemRequirement req = requiresAdjacentItems[i];
                        if (req == null) continue;
                        LOGGER.atWarning().log(
                                "[MGHG|ADJ_RULE] req[%d] ids=%s resolved=%s radius=%d,%d,%d offset=%d,%d,%d",
                                i,
                                Arrays.toString(req.getIds()),
                                req.getResolvedIds(),
                                req.getRadiusX(), req.getRadiusY(), req.getRadiusZ(),
                                req.getOffsetX(), req.getOffsetY(), req.getOffsetZ()
                        );
                    }
                }
            }
            if (!ok) return false;
        }
        if (requiresAdjacentParticles != null && requiresAdjacentParticles.length > 0) {
            boolean ok = ctx != null && ctx.matchesAdjacentParticles(requiresAdjacentParticles, adjacentParticlesMatch);
            if (!ok) return false;
        }
        return true;
    }

    public boolean applyTo(MghgCropData data) {
        if (data == null) return false;
        boolean changed = false;
        switch (slot) {
            case CLIMATE -> {
                ClimateMutation next = setClimate == null ? ClimateMutation.NONE : setClimate;
                if (data.getClimate() != next) {
                    data.setClimate(next);
                    changed = true;
                }
            }
            case LUNAR -> {
                LunarMutation next = setLunar == null ? LunarMutation.NONE : setLunar;
                if (data.getLunar() != next) {
                    data.setLunar(next);
                    changed = true;
                }
            }
            case RARITY -> {
                RarityMutation next = setRarity == null ? RarityMutation.NONE : setRarity;
                if (data.getRarity() != next) {
                    data.setRarity(next);
                    changed = true;
                }
            }
        }
        return changed;
    }

    private static @Nullable IntSet resolveWeatherIds(@Nullable String[] ids) {
        if (ids == null || ids.length == 0) return null;
        IntOpenHashSet set = new IntOpenHashSet(ids.length * 2);
        for (String id : ids) {
            int idx = Weather.getAssetMap().getIndex(id);
            if (idx != Weather.UNKNOWN_ID) {
                set.add(idx);
            }
        }
        return set.isEmpty() ? null : set;
    }

    private static @Nullable EnumSet<ClimateMutation> resolveClimateSet(@Nullable String[] values) {
        if (values == null || values.length == 0) return null;
        EnumSet<ClimateMutation> set = EnumSet.noneOf(ClimateMutation.class);
        for (String v : values) {
            ClimateMutation parsed = parseClimate(v);
            if (parsed != null) {
                set.add(parsed);
            }
        }
        return set.isEmpty() ? null : set;
    }

    private static @Nullable EnumSet<LunarMutation> resolveLunarSet(@Nullable String[] values) {
        if (values == null || values.length == 0) return null;
        EnumSet<LunarMutation> set = EnumSet.noneOf(LunarMutation.class);
        for (String v : values) {
            LunarMutation parsed = parseLunar(v);
            if (parsed != null) {
                set.add(parsed);
            }
        }
        return set.isEmpty() ? null : set;
    }

    private static @Nullable EnumSet<RarityMutation> resolveRaritySet(@Nullable String[] values) {
        if (values == null || values.length == 0) return null;
        EnumSet<RarityMutation> set = EnumSet.noneOf(RarityMutation.class);
        for (String v : values) {
            RarityMutation parsed = parseRarity(v);
            if (parsed != null) {
                set.add(parsed);
            }
        }
        return set.isEmpty() ? null : set;
    }

    private static @Nullable Set<String> resolveBlockIdSet(@Nullable String[] values) {
        if (values == null || values.length == 0) return null;
        Set<String> set = new HashSet<>();
        for (String raw : values) {
            if (raw == null || raw.isBlank()) continue;
            String normalized = MghgBlockIdUtil.normalizeId(raw);
            if (normalized != null && !normalized.isBlank()) {
                set.add(normalized);
            }
            String baseId = MghgBlockIdUtil.resolveBaseIdFromStateId(raw);
            if (baseId != null && !baseId.isBlank()) {
                String baseNorm = MghgBlockIdUtil.normalizeId(baseId);
                if (baseNorm != null && !baseNorm.isBlank()) {
                    set.add(baseNorm);
                }
            }
        }
        return set.isEmpty() ? null : set;
    }

    private static @Nullable Set<String> resolveStageSetSet(@Nullable String[] values) {
        if (values == null || values.length == 0) return null;
        Set<String> set = new HashSet<>();
        for (String raw : values) {
            if (raw == null || raw.isBlank()) continue;
            set.add(raw.trim().toLowerCase(Locale.ROOT));
        }
        return set.isEmpty() ? null : set;
    }

    private static @Nullable ClimateMutation parseClimate(@Nullable String value) {
        if (value == null) return null;
        try {
            return ClimateMutation.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static @Nullable LunarMutation parseLunar(@Nullable String value) {
        if (value == null) return null;
        try {
            return LunarMutation.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static @Nullable RarityMutation parseRarity(@Nullable String value) {
        if (value == null) return null;
        try {
            return RarityMutation.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
