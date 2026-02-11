package com.voidexiled.magichygarden.features.farming.parcels;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

import java.util.UUID;

public final class MghgParcelMember {
    public static final BuilderCodec<MghgParcelMember> CODEC = BuilderCodec.builder(MghgParcelMember.class, MghgParcelMember::new)
            .<UUID>append(new KeyedCodec<>("Uuid", Codec.UUID_STRING), (o, v) -> o.uuid = v, o -> o.uuid)
            .add()
            .<MghgParcelRole>append(new KeyedCodec<>("Role", MghgParcelRole.CODEC), (o, v) -> o.role = v, o -> o.role)
            .add()
            .build();

    private UUID uuid;
    private MghgParcelRole role = MghgParcelRole.MEMBER;

    public MghgParcelMember() {
    }

    public MghgParcelMember(UUID uuid, MghgParcelRole role) {
        this.uuid = uuid;
        this.role = role;
    }

    public UUID getUuid() {
        return uuid;
    }

    public MghgParcelRole getRole() {
        return role;
    }
}
