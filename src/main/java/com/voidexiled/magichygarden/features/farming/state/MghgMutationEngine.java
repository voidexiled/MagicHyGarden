package com.voidexiled.magichygarden.features.farming.state;

import com.voidexiled.magichygarden.features.farming.components.MghgCropData;
import com.hypixel.hytale.logger.HytaleLogger;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Motor central de mutaciones:
 * - Evalúa reglas por evento (weather/pet/manual).
 * - Respeta cooldown por slot.
 * - Evita sobreescrituras (una regla por slot y tick).
 */
public final class MghgMutationEngine {
    private MghgMutationEngine() {}

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    // Cambia a false cuando termines de debuguear.
    private static final boolean DEBUG = false;

    public static boolean applyRules(
            MghgCropData data,
            MghgMutationContext ctx,
            MghgMutationRuleSet ruleSet,
            int defaultCooldownSeconds
    ) {
        if (data == null || ctx == null || ruleSet == null || ruleSet.isEmpty()) {
            return false;
        }

        Map<MutationSlot, List<MghgMutationRule>> candidates = new EnumMap<>(MutationSlot.class);
        Map<MutationSlot, Integer> topPriority = new EnumMap<>(MutationSlot.class);

        int totalRules = 0;
        int eventOk = 0;
        int reqOk = 0;
        int chanceOk = 0;
        int cooldownOk = 0;

        for (MghgMutationRule rule : ruleSet.getRules()) {
            if (rule == null) continue;
            totalRules++;
            if (!rule.matchesEvent(ctx)) continue;
            eventOk++;
            if (!rule.matchesRequirements(data, ctx)) continue;
            reqOk++;
            if (rule.getChance() <= 0.0) continue;
            chanceOk++;

            int cooldown = rule.getCooldownSecondsOrDefault(defaultCooldownSeconds);
            // Primer “arming”: si nunca se ha tirado en este slot, arranca el cooldown
            // y evita mutación instantánea (plantar o al volverse maduro).
            if (cooldown > 0 && getLastRoll(data, rule.getSlot()) == null && !rule.isSkipInitialCooldown()) {
                setLastRoll(data, rule.getSlot(), ctx.getNow());
                continue;
            }
            if (!cooldownReady(data, rule.getSlot(), ctx.getNow(), cooldown)) {
                continue;
            }
            cooldownOk++;

            int priority = rule.getPriority();
            Integer current = topPriority.get(rule.getSlot());
            if (current == null || priority > current) {
                List<MghgMutationRule> list = new ArrayList<>();
                list.add(rule);
                candidates.put(rule.getSlot(), list);
                topPriority.put(rule.getSlot(), priority);
            } else if (priority == current) {
                candidates.get(rule.getSlot()).add(rule);
            }
        }

        if (DEBUG && candidates.isEmpty() && ruleSet.needsAdjacentItems()) {
            LOGGER.atInfo().log(
                    "[MGHG|MUTATION_ENGINE] no candidates total=%d event=%d req=%d chance=%d cooldown=%d weatherId=%d mature=%s",
                    totalRules, eventOk, reqOk, chanceOk, cooldownOk, ctx.getWeatherId(), ctx.isMature()
            );
        }

        boolean dirty = false;
        for (Map.Entry<MutationSlot, List<MghgMutationRule>> entry : candidates.entrySet()) {
            MutationSlot slot = entry.getKey();
            List<MghgMutationRule> list = entry.getValue();
            if (list == null || list.isEmpty()) continue;

            MghgMutationRule chosen = pickWeighted(list);
            if (chosen == null) continue;

            // Consume roll for this slot
            setLastRoll(data, slot, ctx.getNow());

            double chance = chosen.getChance();
            if (ThreadLocalRandom.current().nextDouble() <= chance) {
                boolean changed = chosen.applyTo(data);
                if (changed) dirty = true;
            } else {
                dirty = true; // we still consumed a roll
            }
        }

        return dirty;
    }

    private static boolean cooldownReady(MghgCropData data, MutationSlot slot, Instant now, int cooldownSeconds) {
        if (cooldownSeconds <= 0) return true;
        Instant last = getLastRoll(data, slot);
        if (last == null) return true;
        Duration since = Duration.between(last, now);
        // If clocks changed (game time vs real time), allow the roll instead of blocking forever.
        if (since.isNegative()) return true;
        return since.getSeconds() >= cooldownSeconds;
    }

    private static Instant getLastRoll(MghgCropData data, MutationSlot slot) {
        return switch (slot) {
            case CLIMATE -> data.getLastRegularRoll();
            case LUNAR -> data.getLastLunarRoll();
            case RARITY -> data.getLastSpecialRoll();
        };
    }

    private static void setLastRoll(MghgCropData data, MutationSlot slot, Instant now) {
        switch (slot) {
            case CLIMATE -> data.setLastRegularRoll(now);
            case LUNAR -> data.setLastLunarRoll(now);
            case RARITY -> data.setLastSpecialRoll(now);
        }
    }

    private static MghgMutationRule pickWeighted(List<MghgMutationRule> rules) {
        int total = 0;
        for (MghgMutationRule rule : rules) {
            total += Math.max(1, rule.getWeight());
        }
        if (total <= 0) return rules.get(0);
        int roll = ThreadLocalRandom.current().nextInt(total);
        int acc = 0;
        for (MghgMutationRule rule : rules) {
            acc += Math.max(1, rule.getWeight());
            if (roll < acc) return rule;
        }
        return rules.get(0);
    }
}
