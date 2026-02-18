package com.voidexiled.magichygarden.features.farming.registry;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

public final class MghgCropDefinition {
    public static final BuilderCodec<MghgCropDefinition> CODEC =
            BuilderCodec.builder(MghgCropDefinition.class, MghgCropDefinition::new)
                    .append(new KeyedCodec<>("Id", Codec.STRING),
                            (o, v) -> o.id = v, o -> o.id)
                    .add()
                    .append(new KeyedCodec<>("BlockId", Codec.STRING),
                            (o, v) -> o.blockId = v, o -> o.blockId)
                    .add()
                    .append(new KeyedCodec<>("ItemId", Codec.STRING),
                            (o, v) -> o.itemId = v, o -> o.itemId)
                    .add()
                    .append(new KeyedCodec<>("BaseWeightGrams", Codec.DOUBLE),
                            (o, v) -> o.baseWeightGrams = v == null ? 0.0 : v,
                            o -> o.baseWeightGrams <= 0 ? null : o.baseWeightGrams)
                    .add()
                    .append(new KeyedCodec<>("GrowTimeSeconds", Codec.INTEGER, true),
                            (o, v) -> o.growTimeSeconds = v == null ? o.growTimeSeconds : Math.max(0, v),
                            o -> o.growTimeSeconds <= 0 ? null : o.growTimeSeconds)
                    .documentation("Approximate full growth time in seconds (seed to mature). Used by tooltips/UI.")
                    .add()
                    .build();

    private String id;
    private String blockId;
    private String itemId;
    private double baseWeightGrams;
    private int growTimeSeconds;

    public MghgCropDefinition() {
    }

    public String getId() {
        return id;
    }

    public String getBlockId() {
        return blockId;
    }

    public String getItemId() {
        return itemId;
    }

    public double getBaseWeightGrams() {
        return baseWeightGrams;
    }

    public int getGrowTimeSeconds() {
        return growTimeSeconds;
    }

    void normalize() {
        if (id != null) {
            id = id.trim();
        }
        if (blockId != null) {
            blockId = blockId.trim();
        }
        if (itemId != null) {
            itemId = itemId.trim();
        }
    }
}
