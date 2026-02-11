package com.voidexiled.magichygarden.features.farming.storage;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;

import java.util.UUID;

public final class MghgPlayerNamesState {
    public static final BuilderCodec<MghgPlayerNamesState> CODEC =
            BuilderCodec.builder(MghgPlayerNamesState.class, MghgPlayerNamesState::new)
                    .append(new KeyedCodec<>("Players",
                                    ArrayCodec.ofBuilderCodec(Entry.CODEC, Entry[]::new)),
                            (o, v) -> o.entries = v,
                            o -> o.entries)
                    .add()
                    .build();

    private Entry[] entries = new Entry[0];

    public MghgPlayerNamesState() {
    }

    public MghgPlayerNamesState(Entry[] entries) {
        this.entries = entries == null ? new Entry[0] : entries;
    }

    public Entry[] getEntries() {
        return entries == null ? new Entry[0] : entries;
    }

    public static final class Entry {
        public static final BuilderCodec<Entry> CODEC =
                BuilderCodec.builder(Entry.class, Entry::new)
                        .append(new KeyedCodec<>("Uuid", Codec.UUID_STRING),
                                (o, v) -> o.uuid = v,
                                o -> o.uuid)
                        .add()
                        .append(new KeyedCodec<>("Name", Codec.STRING),
                                (o, v) -> o.name = v,
                                o -> o.name)
                        .add()
                        .build();

        private UUID uuid;
        private String name = "";

        public Entry() {
        }

        public Entry(UUID uuid, String name) {
            this.uuid = uuid;
            this.name = name == null ? "" : name;
        }

        public UUID getUuid() {
            return uuid;
        }

        public String getName() {
            return name == null ? "" : name;
        }
    }
}
