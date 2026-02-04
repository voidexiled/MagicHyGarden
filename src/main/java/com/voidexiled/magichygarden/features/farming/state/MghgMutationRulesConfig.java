package com.voidexiled.magichygarden.features.farming.state;

import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.EnumCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;

public final class MghgMutationRulesConfig {
    public static final BuilderCodec<MghgMutationRulesConfig> CODEC =
            BuilderCodec.builder(MghgMutationRulesConfig.class, MghgMutationRulesConfig::new)
                    .append(new KeyedCodec<>("CooldownClock", new EnumCodec<>(CooldownClock.class), true),
                            (o, v) -> o.cooldownClock = v == null ? o.cooldownClock : v,
                            o -> o.cooldownClock)
                    .documentation("Clock used for cooldowns: REAL_TIME or GAME_TIME.")
                    .add()
                    .append(new KeyedCodec<>("Rules", ArrayCodec.ofBuilderCodec(MghgMutationRule.CODEC, MghgMutationRule[]::new)),
                            (o, v) -> o.rules = v, o -> o.rules)
                    .add()
                    .build();

    private MghgMutationRule[] rules = new MghgMutationRule[0];
    private CooldownClock cooldownClock = CooldownClock.REAL_TIME;

    public MghgMutationRulesConfig() {}

    public MghgMutationRule[] getRules() {
        return rules;
    }

    public CooldownClock getCooldownClock() {
        return cooldownClock;
    }
}
