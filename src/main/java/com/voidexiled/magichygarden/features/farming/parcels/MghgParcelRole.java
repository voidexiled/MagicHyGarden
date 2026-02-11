package com.voidexiled.magichygarden.features.farming.parcels;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.codecs.EnumCodec;

public enum MghgParcelRole {
    OWNER,
    MANAGER,
    MEMBER,
    VISITOR;

    public static final Codec<MghgParcelRole> CODEC = new EnumCodec<>(MghgParcelRole.class);
}
