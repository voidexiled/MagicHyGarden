package com.voidexiled.magichygarden.features.farming.registry;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;

public final class MghgCropRegistryConfig {
    public static final BuilderCodec<MghgCropRegistryConfig> CODEC =
            BuilderCodec.builder(MghgCropRegistryConfig.class, MghgCropRegistryConfig::new)
                    .append(new KeyedCodec<>("Definitions", ArrayCodec.ofBuilderCodec(MghgCropDefinition.CODEC, MghgCropDefinition[]::new)),
                            (o, v) -> o.definitions = v, o -> o.definitions)
                    .add()
                    .build();

    private MghgCropDefinition[] definitions = new MghgCropDefinition[0];

    public MghgCropRegistryConfig() {
    }

    public MghgCropDefinition[] getDefinitions() {
        return definitions;
    }
}
