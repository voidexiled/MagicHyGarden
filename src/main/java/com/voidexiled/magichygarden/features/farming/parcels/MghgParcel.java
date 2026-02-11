package com.voidexiled.magichygarden.features.farming.parcels;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class MghgParcel {
    public static final BuilderCodec<MghgParcel> CODEC = BuilderCodec.builder(MghgParcel.class, MghgParcel::new)
            .<UUID>append(new KeyedCodec<>("Id", Codec.UUID_STRING), (o, v) -> o.id = v, o -> o.id)
            .add()
            .<UUID>append(new KeyedCodec<>("Owner", Codec.UUID_STRING), (o, v) -> o.owner = v, o -> o.owner)
            .add()
            .<MghgParcelBounds>append(new KeyedCodec<>("Bounds", MghgParcelBounds.CODEC), (o, v) -> o.bounds = v, o -> o.bounds)
            .add()
            .<Integer>append(new KeyedCodec<>("SpawnX", Codec.INTEGER, true), (o, v) -> o.spawnX = v, o -> o.spawnX)
            .add()
            .<Integer>append(new KeyedCodec<>("SpawnY", Codec.INTEGER, true), (o, v) -> o.spawnY = v, o -> o.spawnY)
            .add()
            .<Integer>append(new KeyedCodec<>("SpawnZ", Codec.INTEGER, true), (o, v) -> o.spawnZ = v, o -> o.spawnZ)
            .add()
            .<MghgParcelBlocks>append(new KeyedCodec<>("Blocks", MghgParcelBlocks.CODEC, true), (o, v) -> o.blocks = v, o -> o.blocks)
            .add()
            .<MghgParcelMember[]>append(
                    new KeyedCodec<>("Members", ArrayCodec.ofBuilderCodec(MghgParcelMember.CODEC, MghgParcelMember[]::new)),
                    (o, v) -> o.members = v,
                    o -> o.members
            )
            .add()
            .build();

    private UUID id;
    private UUID owner;
    private MghgParcelBounds bounds;
    private Integer spawnX;
    private Integer spawnY;
    private Integer spawnZ;
    private MghgParcelBlocks blocks;
    private MghgParcelMember[] members = new MghgParcelMember[0];

    public MghgParcel() {
    }

    public MghgParcel(UUID id, UUID owner, MghgParcelBounds bounds) {
        this.id = id;
        this.owner = owner;
        this.bounds = bounds;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwner() {
        return owner;
    }

    public MghgParcelBounds getBounds() {
        return bounds;
    }

    public void setBounds(@Nullable MghgParcelBounds bounds) {
        this.bounds = bounds;
    }

    public MghgParcelBlocks getBlocks() {
        return blocks;
    }

    public void setBlocks(MghgParcelBlocks blocks) {
        this.blocks = blocks;
    }

    public boolean hasCustomSpawn() {
        return spawnX != null && spawnY != null && spawnZ != null;
    }

    public void setCustomSpawn(int x, int y, int z) {
        this.spawnX = x;
        this.spawnY = y;
        this.spawnZ = z;
    }

    public void clearCustomSpawn() {
        this.spawnX = null;
        this.spawnY = null;
        this.spawnZ = null;
    }

    public int resolveSpawnX() {
        if (spawnX != null) {
            return spawnX;
        }
        MghgParcelBounds current = bounds;
        return current == null ? 0 : current.getOriginX() + (current.getSizeX() / 2);
    }

    public int resolveSpawnY() {
        if (spawnY != null) {
            return spawnY;
        }
        MghgParcelBounds current = bounds;
        return current == null ? 80 : current.getOriginY() + 1;
    }

    public int resolveSpawnZ() {
        if (spawnZ != null) {
            return spawnZ;
        }
        MghgParcelBounds current = bounds;
        return current == null ? 0 : current.getOriginZ() + (current.getSizeZ() / 2);
    }

    public MghgParcelMember[] getMembers() {
        return members;
    }

    public @Nullable MghgParcelMember findMember(@Nullable UUID uuid) {
        if (uuid == null || members == null) {
            return null;
        }
        for (MghgParcelMember member : members) {
            if (member == null || member.getUuid() == null) {
                continue;
            }
            if (uuid.equals(member.getUuid())) {
                return member;
            }
        }
        return null;
    }

    public MghgParcelRole getRole(@Nullable UUID uuid) {
        if (uuid == null) {
            return MghgParcelRole.VISITOR;
        }
        if (uuid.equals(owner)) {
            return MghgParcelRole.OWNER;
        }
        MghgParcelMember member = findMember(uuid);
        if (member == null || member.getRole() == null) {
            return MghgParcelRole.VISITOR;
        }
        return member.getRole();
    }

    public void upsertMember(@Nullable UUID uuid, @Nullable MghgParcelRole role) {
        if (uuid == null) {
            return;
        }
        if (uuid.equals(owner)) {
            return;
        }
        MghgParcelRole safeRole = role == null ? MghgParcelRole.MEMBER : role;
        List<MghgParcelMember> list = new ArrayList<>();
        if (members != null) {
            for (MghgParcelMember member : members) {
                if (member == null || member.getUuid() == null) {
                    continue;
                }
                if (uuid.equals(member.getUuid())) {
                    list.add(new MghgParcelMember(uuid, safeRole));
                } else {
                    list.add(member);
                }
            }
        }
        boolean exists = list.stream().anyMatch(m -> uuid.equals(m.getUuid()));
        if (!exists) {
            list.add(new MghgParcelMember(uuid, safeRole));
        }
        members = list.toArray(new MghgParcelMember[0]);
    }

    public boolean removeMember(@Nullable UUID uuid) {
        if (uuid == null || members == null || members.length == 0) {
            return false;
        }
        List<MghgParcelMember> list = new ArrayList<>();
        boolean removed = false;
        for (MghgParcelMember member : members) {
            if (member == null || member.getUuid() == null) {
                continue;
            }
            if (uuid.equals(member.getUuid())) {
                removed = true;
                continue;
            }
            list.add(member);
        }
        if (removed) {
            members = list.toArray(new MghgParcelMember[0]);
        }
        return removed;
    }
}
