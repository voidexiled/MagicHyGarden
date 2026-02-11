package com.voidexiled.magichygarden.features.farming.parcels;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;

import java.util.UUID;

public final class MghgParcelInviteState {
    public static final BuilderCodec<MghgParcelInviteState> CODEC =
            BuilderCodec.builder(MghgParcelInviteState.class, MghgParcelInviteState::new)
                    .append(new KeyedCodec<>(
                                    "Invites",
                                    ArrayCodec.ofBuilderCodec(Entry.CODEC, Entry[]::new)
                            ),
                            (o, v) -> o.entries = v,
                            o -> o.entries)
                    .add()
                    .build();

    private Entry[] entries = new Entry[0];

    public MghgParcelInviteState() {
    }

    public MghgParcelInviteState(Entry[] entries) {
        this.entries = entries == null ? new Entry[0] : entries;
    }

    public Entry[] getEntries() {
        return entries == null ? new Entry[0] : entries;
    }

    public static final class Entry {
        public static final BuilderCodec<Entry> CODEC =
                BuilderCodec.builder(Entry.class, Entry::new)
                        .append(new KeyedCodec<>("OwnerId", Codec.UUID_STRING),
                                (o, v) -> o.ownerId = v,
                                o -> o.ownerId)
                        .add()
                        .append(new KeyedCodec<>("OwnerName", Codec.STRING),
                                (o, v) -> o.ownerName = v,
                                o -> o.ownerName)
                        .add()
                        .append(new KeyedCodec<>("InviterId", Codec.UUID_STRING),
                                (o, v) -> o.inviterId = v,
                                o -> o.inviterId)
                        .add()
                        .append(new KeyedCodec<>("InviterName", Codec.STRING),
                                (o, v) -> o.inviterName = v,
                                o -> o.inviterName)
                        .add()
                        .append(new KeyedCodec<>("TargetId", Codec.UUID_STRING),
                                (o, v) -> o.targetId = v,
                                o -> o.targetId)
                        .add()
                        .append(new KeyedCodec<>("TargetName", Codec.STRING),
                                (o, v) -> o.targetName = v,
                                o -> o.targetName)
                        .add()
                        .append(new KeyedCodec<>("CreatedAtEpochSecond", Codec.LONG),
                                (o, v) -> o.createdAtEpochSecond = v,
                                o -> o.createdAtEpochSecond)
                        .add()
                        .build();

        private UUID ownerId;
        private String ownerName;
        private UUID inviterId;
        private String inviterName;
        private UUID targetId;
        private String targetName;
        private long createdAtEpochSecond;

        public Entry() {
        }

        public Entry(
                UUID ownerId,
                String ownerName,
                UUID inviterId,
                String inviterName,
                UUID targetId,
                String targetName,
                long createdAtEpochSecond
        ) {
            this.ownerId = ownerId;
            this.ownerName = ownerName;
            this.inviterId = inviterId;
            this.inviterName = inviterName;
            this.targetId = targetId;
            this.targetName = targetName;
            this.createdAtEpochSecond = createdAtEpochSecond;
        }

        public UUID getOwnerId() {
            return ownerId;
        }

        public String getOwnerName() {
            return ownerName;
        }

        public UUID getInviterId() {
            return inviterId;
        }

        public String getInviterName() {
            return inviterName;
        }

        public UUID getTargetId() {
            return targetId;
        }

        public String getTargetName() {
            return targetName;
        }

        public long getCreatedAtEpochSecond() {
            return createdAtEpochSecond;
        }
    }
}

