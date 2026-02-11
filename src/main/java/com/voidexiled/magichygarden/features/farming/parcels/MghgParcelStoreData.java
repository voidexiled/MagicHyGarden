package com.voidexiled.magichygarden.features.farming.parcels;

import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;

public final class MghgParcelStoreData {
    public static final BuilderCodec<MghgParcelStoreData> CODEC = BuilderCodec.builder(MghgParcelStoreData.class, MghgParcelStoreData::new)
            .<MghgParcel[]>append(
                    new KeyedCodec<>("Parcels", ArrayCodec.ofBuilderCodec(MghgParcel.CODEC, MghgParcel[]::new)),
                    (o, v) -> o.parcels = v,
                    o -> o.parcels
            )
            .add()
            .build();

    private MghgParcel[] parcels = new MghgParcel[0];

    public MghgParcelStoreData() {
    }

    public MghgParcelStoreData(MghgParcel[] parcels) {
        this.parcels = parcels == null ? new MghgParcel[0] : parcels;
    }

    public MghgParcel[] getParcels() {
        return parcels;
    }
}
