package com.voidexiled.magichygarden.features.farming.state;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class MghgMutationRuleSet {
    private final List<MghgMutationRule> rules;
    private final Set<String> adjacentBlockIds;
    private final boolean needsAdjacentItems;
    private final boolean needsAdjacentParticles;
    private final CooldownClock cooldownClock;

    public MghgMutationRuleSet(List<MghgMutationRule> rules) {
        this(rules, CooldownClock.REAL_TIME);
    }

    public MghgMutationRuleSet(List<MghgMutationRule> rules, CooldownClock cooldownClock) {
        if (rules == null) {
            this.rules = Collections.emptyList();
            this.adjacentBlockIds = Collections.emptySet();
            this.needsAdjacentItems = false;
            this.needsAdjacentParticles = false;
            this.cooldownClock = cooldownClock == null ? CooldownClock.REAL_TIME : cooldownClock;
            return;
        }

        for (MghgMutationRule rule : rules) {
            if (rule != null) {
                rule.resolveCaches();
            }
        }

        List<MghgMutationRule> copy = new ArrayList<>(rules.size());
        for (MghgMutationRule rule : rules) {
            if (rule != null) {
                copy.add(rule);
            }
        }
        copy.sort(Comparator.comparingInt(MghgMutationRule::getPriority).reversed());
        this.rules = Collections.unmodifiableList(copy);

        Set<String> adj = new HashSet<>();
        boolean adjacentItems = false;
        boolean adjacentParticles = false;
        for (MghgMutationRule rule : copy) {
            if (rule == null) continue;
            String[] ids = rule.getRequiresAdjacentBlockIds();
            if (ids != null) {
                for (String id : ids) {
                    if (id != null && !id.isBlank()) {
                        String normalized = MghgBlockIdUtil.normalizeId(id);
                        if (normalized != null && !normalized.isBlank()) {
                            adj.add(normalized);
                        }
                        String baseFromState = MghgBlockIdUtil.resolveBaseIdFromStateId(id);
                        if (baseFromState != null && !baseFromState.isBlank()) {
                            String baseNorm = MghgBlockIdUtil.normalizeId(baseFromState);
                            if (baseNorm != null && !baseNorm.isBlank()) {
                                adj.add(baseNorm);
                            }
                        }
                    }
                }
            }
            MghgAdjacentItemRequirement[] reqs = rule.getRequiresAdjacentItems();
            if (reqs != null && reqs.length > 0) {
                adjacentItems = true;
            }
            MghgAdjacentParticleRequirement[] particleReqs = rule.getRequiresAdjacentParticles();
            if (particleReqs != null && particleReqs.length > 0) {
                adjacentParticles = true;
            }
        }
        this.adjacentBlockIds = adj.isEmpty() ? Collections.emptySet() : Collections.unmodifiableSet(adj);
        this.needsAdjacentItems = adjacentItems;
        this.needsAdjacentParticles = adjacentParticles;
        this.cooldownClock = cooldownClock == null ? CooldownClock.REAL_TIME : cooldownClock;
    }

    public List<MghgMutationRule> getRules() {
        return rules;
    }

    public boolean isEmpty() {
        return rules.isEmpty();
    }

    public Set<String> getRequiredAdjacentBlockIds() {
        return adjacentBlockIds;
    }

    public boolean needsAdjacentItems() {
        return needsAdjacentItems;
    }

    public boolean needsAdjacentParticles() {
        return needsAdjacentParticles;
    }

    public CooldownClock getCooldownClock() {
        return cooldownClock;
    }

    //
}
