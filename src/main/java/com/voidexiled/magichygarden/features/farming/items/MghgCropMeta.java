package com.voidexiled.magichygarden.features.farming.items;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

public final class MghgCropMeta {
    public static final BuilderCodec<MghgCropMeta> CODEC = BuilderCodec.builder(MghgCropMeta.class, MghgCropMeta::new)
            .append(new KeyedCodec<>("Size", Codec.INTEGER), (o, v) -> o.size = v, o -> o.size)
            .add()
            .append(new KeyedCodec<>("Climate", Codec.STRING), (o, v) -> o.climate = v, o -> o.climate)
            .add()
            .append(new KeyedCodec<>("Rarity", Codec.STRING), (o, v) -> o.rarity = v, o -> o.rarity)
            .add()
            .build();

    public static final KeyedCodec<MghgCropMeta> KEY = new KeyedCodec<>("MGHG_Crop", CODEC);

    private int size;
    private String climate;
    private String rarity;

    public MghgCropMeta() {}

    public static MghgCropMeta fromCropData(int size, String climate, String rarity) {
        MghgCropMeta meta = new MghgCropMeta();
        meta.size = size;
        meta.climate = climate;
        meta.rarity = rarity;
        return meta;
    }

    public int getSize() { return size; }
    public String getClimate() { return climate; }
    public String getRarity() { return rarity; }
}
