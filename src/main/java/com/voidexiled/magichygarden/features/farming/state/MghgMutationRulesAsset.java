package com.voidexiled.magichygarden.features.farming.state;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.assetstore.AssetRegistry;
import com.hypixel.hytale.assetstore.AssetStore;
import com.hypixel.hytale.assetstore.codec.AssetBuilderCodec;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.assetstore.map.JsonAssetWithMap;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;
import com.hypixel.hytale.codec.codecs.EnumCodec;
import com.hypixel.hytale.codec.schema.metadata.ui.UIPropertyTitle;

import javax.annotation.Nullable;

public final class MghgMutationRulesAsset implements JsonAssetWithMap<String, DefaultAssetMap<String, MghgMutationRulesAsset>> {
    public static final AssetBuilderCodec<String, MghgMutationRulesAsset> CODEC =
            AssetBuilderCodec.builder(
                            MghgMutationRulesAsset.class,
                            MghgMutationRulesAsset::new,
                            Codec.STRING,
                            (asset, id) -> asset.id = id,
                            asset -> asset.id,
                            (asset, data) -> asset.data = data,
                            asset -> asset.data
                    )
                    .documentation("MagicHyGarden mutation ruleset asset.")
                    .appendInherited(
                            new KeyedCodec<>("CooldownClock", new EnumCodec<>(CooldownClock.class), true),
                            (asset, v) -> asset.cooldownClock = v == null ? asset.cooldownClock : v,
                            asset -> asset.cooldownClock,
                            (asset, parent) -> asset.cooldownClock = parent.cooldownClock
                    )
                    .documentation("Clock used for cooldowns: REAL_TIME or GAME_TIME.")
                    .metadata(new UIPropertyTitle("Cooldown Clock"))
                    .add()
                    .appendInherited(
                            new KeyedCodec<>("Rules", ArrayCodec.ofBuilderCodec(MghgMutationRule.CODEC, MghgMutationRule[]::new)),
                            (asset, v) -> asset.rules = v,
                            asset -> asset.rules,
                            (asset, parent) -> asset.rules = parent.rules
                    )
                    .documentation("Ordered list of mutation rules. The engine groups by slot, then applies priority/weight.")
                    .metadata(new UIPropertyTitle("Rules"))
                    .add()
                    .build();

    private static AssetStore<String, MghgMutationRulesAsset, DefaultAssetMap<String, MghgMutationRulesAsset>> ASSET_STORE;

    private AssetExtraInfo.Data data;
    private String id;
    private MghgMutationRule[] rules = new MghgMutationRule[0];
    private CooldownClock cooldownClock = CooldownClock.REAL_TIME;

    public static @Nullable AssetStore<String, MghgMutationRulesAsset, DefaultAssetMap<String, MghgMutationRulesAsset>> getAssetStore() {
        if (ASSET_STORE == null) {
            ASSET_STORE = AssetRegistry.getAssetStore(MghgMutationRulesAsset.class);
        }
        return ASSET_STORE;
    }

    public static @Nullable DefaultAssetMap<String, MghgMutationRulesAsset> getAssetMap() {
        AssetStore<String, MghgMutationRulesAsset, DefaultAssetMap<String, MghgMutationRulesAsset>> store = getAssetStore();
        return store == null ? null : (DefaultAssetMap<String, MghgMutationRulesAsset>) store.getAssetMap();
    }

    public String getId() {
        return id;
    }

    public MghgMutationRule[] getRules() {
        return rules;
    }

    public CooldownClock getCooldownClock() {
        return cooldownClock;
    }
}
