package com.voidexiled.magichygarden.features.farming.parcels;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

public final class MghgParcelBounds {
    public static final int DEFAULT_SIZE_X = 1000;
    public static final int DEFAULT_SIZE_Y = 256;
    public static final int DEFAULT_SIZE_Z = 1000;

    public static final BuilderCodec<MghgParcelBounds> CODEC = BuilderCodec.builder(MghgParcelBounds.class, MghgParcelBounds::new)
            .<Integer>append(new KeyedCodec<>("OriginX", Codec.INTEGER), (o, v) -> o.originX = v, o -> o.originX)
            .add()
            .<Integer>append(new KeyedCodec<>("OriginY", Codec.INTEGER), (o, v) -> o.originY = v, o -> o.originY)
            .add()
            .<Integer>append(new KeyedCodec<>("OriginZ", Codec.INTEGER), (o, v) -> o.originZ = v, o -> o.originZ)
            .add()
            .<Integer>append(new KeyedCodec<>("SizeX", Codec.INTEGER), (o, v) -> o.sizeX = v, o -> o.sizeX)
            .add()
            .<Integer>append(new KeyedCodec<>("SizeY", Codec.INTEGER), (o, v) -> o.sizeY = v, o -> o.sizeY)
            .add()
            .<Integer>append(new KeyedCodec<>("SizeZ", Codec.INTEGER), (o, v) -> o.sizeZ = v, o -> o.sizeZ)
            .add()
            .afterDecode(MghgParcelBounds::normalize)
            .build();

    private int originX;
    private int originY;
    private int originZ;
    private int sizeX = DEFAULT_SIZE_X;
    private int sizeY = DEFAULT_SIZE_Y;
    private int sizeZ = DEFAULT_SIZE_Z;

    public MghgParcelBounds() {
    }

    public MghgParcelBounds(int originX, int originY, int originZ, int sizeX, int sizeY, int sizeZ) {
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        normalize();
    }

    private void normalize() {
        if (this.sizeX <= 0) this.sizeX = DEFAULT_SIZE_X;
        if (this.sizeY <= 0) this.sizeY = DEFAULT_SIZE_Y;
        if (this.sizeZ <= 0) this.sizeZ = DEFAULT_SIZE_Z;
    }

    public int getOriginX() {
        return originX;
    }

    public int getOriginY() {
        return originY;
    }

    public int getOriginZ() {
        return originZ;
    }

    public int getSizeX() {
        return sizeX;
    }

    public int getSizeY() {
        return sizeY;
    }

    public int getSizeZ() {
        return sizeZ;
    }
}
