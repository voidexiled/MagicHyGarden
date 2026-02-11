package com.voidexiled.magichygarden.features.farming.economy;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;

import java.util.UUID;

public final class MghgEconomyState {
    public static final BuilderCodec<MghgEconomyState> CODEC =
            BuilderCodec.builder(MghgEconomyState.class, MghgEconomyState::new)
                    .append(new KeyedCodec<>("Balances",
                                    ArrayCodec.ofBuilderCodec(Entry.CODEC, Entry[]::new)),
                            (o, v) -> o.entries = v,
                            o -> o.entries)
                    .add().build();

    private Entry[] entries = new Entry[0];

    public MghgEconomyState() {
    }

    public MghgEconomyState(Entry[] entries) {
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
                        .append(new KeyedCodec<>("Balance", Codec.DOUBLE),
                                (o, v) -> o.balance = v,
                                o -> o.balance)
                        .add()
                        .build();

        private UUID uuid;
        private double balance;

        public Entry() {
        }

        public Entry(UUID uuid, double balance) {
            this.uuid = uuid;
            this.balance = balance;
        }

        public UUID getUuid() {
            return uuid;
        }

        public double getBalance() {
            return balance;
        }
    }
}
