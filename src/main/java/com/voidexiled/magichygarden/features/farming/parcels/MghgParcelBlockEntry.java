package com.voidexiled.magichygarden.features.farming.parcels;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

public final class MghgParcelBlockEntry {
    public static final BuilderCodec<MghgParcelBlockEntry> CODEC =
            BuilderCodec.builder(MghgParcelBlockEntry.class, MghgParcelBlockEntry::new)
                    .<Integer>append(new KeyedCodec<>("X", Codec.INTEGER), (o, v) -> o.x = v, o -> o.x)
                    .add()
                    .<Integer>append(new KeyedCodec<>("Y", Codec.INTEGER), (o, v) -> o.y = v, o -> o.y)
                    .add()
                    .<Integer>append(new KeyedCodec<>("Z", Codec.INTEGER), (o, v) -> o.z = v, o -> o.z)
                    .add()
                    .<String>append(new KeyedCodec<>("Id", Codec.STRING), (o, v) -> o.id = v, o -> o.id)
                    .add()
                    .build();

    private int x;
    private int y;
    private int z;
    private String id;

    public MghgParcelBlockEntry() {
    }

    public MghgParcelBlockEntry(int x, int y, int z, String id) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.id = id;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public String getId() {
        return id;
    }
}
