package com.voidexiled.magichygarden.features.farming.shop;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;

import java.util.UUID;

public final class MghgShopUiLogState {
    public static final BuilderCodec<MghgShopUiLogState> CODEC =
            BuilderCodec.builder(MghgShopUiLogState.class, MghgShopUiLogState::new)
                    .append(
                            new KeyedCodec<>(
                                    "Players",
                                    ArrayCodec.ofBuilderCodec(PlayerLogEntry.CODEC, PlayerLogEntry[]::new),
                                    true
                            ),
                            (o, v) -> o.players = v == null ? new PlayerLogEntry[0] : v,
                            o -> o.players
                    )
                    .add()
                    .build();

    private PlayerLogEntry[] players = new PlayerLogEntry[0];

    public MghgShopUiLogState() {
    }

    public MghgShopUiLogState(PlayerLogEntry[] players) {
        this.players = players == null ? new PlayerLogEntry[0] : players;
    }

    public PlayerLogEntry[] getPlayers() {
        return players == null ? new PlayerLogEntry[0] : players;
    }

    public void setPlayers(PlayerLogEntry[] players) {
        this.players = players == null ? new PlayerLogEntry[0] : players;
    }

    public static final class PlayerLogEntry {
        public static final BuilderCodec<PlayerLogEntry> CODEC =
                BuilderCodec.builder(PlayerLogEntry.class, PlayerLogEntry::new)
                        .append(new KeyedCodec<>("PlayerUuid", Codec.UUID_STRING), (o, v) -> o.playerUuid = v, o -> o.playerUuid)
                        .add()
                        .append(new KeyedCodec<>("Lines", new ArrayCodec<>(Codec.STRING, String[]::new), true), (o, v) -> o.lines = v == null ? new String[0] : v, o -> o.lines)
                        .add()
                        .build();

        private UUID playerUuid;
        private String[] lines = new String[0];

        public PlayerLogEntry() {
        }

        public PlayerLogEntry(UUID playerUuid, String[] lines) {
            this.playerUuid = playerUuid;
            this.lines = lines == null ? new String[0] : lines;
        }

        public UUID getPlayerUuid() {
            return playerUuid;
        }

        public String[] getLines() {
            return lines == null ? new String[0] : lines;
        }
    }
}
