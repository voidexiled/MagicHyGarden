package com.voidexiled.magichygarden.features.farming.shop;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;

import java.time.Instant;
import java.util.UUID;

public final class MghgShopStockState {
    public static final BuilderCodec<MghgShopStockState> CODEC =
            BuilderCodec.builder(MghgShopStockState.class, MghgShopStockState::new)
                    .append(new KeyedCodec<>("NextRestockAt", Codec.INSTANT, true),
                            (o, v) -> o.nextRestockAt = v,
                            o -> o.nextRestockAt)
                    .add()
                    .append(new KeyedCodec<>("Stocks",
                                    ArrayCodec.ofBuilderCodec(MghgShopStockState.StockEntry.CODEC, StockEntry[]::new)),
                            (o, v) -> o.stocks = v,
                            o -> o.stocks)
                    .add()
                    .append(new KeyedCodec<>(
                                    "PlayerPurchases",
                                    ArrayCodec.ofBuilderCodec(MghgShopStockState.PlayerPurchaseEntry.CODEC, PlayerPurchaseEntry[]::new),
                                    true),
                            (o, v) -> o.playerPurchases = v == null ? new PlayerPurchaseEntry[0] : v,
                            o -> o.playerPurchases)
                    .add()
                    .build();

    private Instant nextRestockAt;
    private StockEntry[] stocks = new StockEntry[0];
    private PlayerPurchaseEntry[] playerPurchases = new PlayerPurchaseEntry[0];

    public MghgShopStockState() {
    }

    public MghgShopStockState(Instant nextRestockAt, StockEntry[] stocks) {
        this(nextRestockAt, stocks, new PlayerPurchaseEntry[0]);
    }

    public MghgShopStockState(Instant nextRestockAt, StockEntry[] stocks, PlayerPurchaseEntry[] playerPurchases) {
        this.nextRestockAt = nextRestockAt;
        this.stocks = stocks == null ? new StockEntry[0] : stocks;
        this.playerPurchases = playerPurchases == null ? new PlayerPurchaseEntry[0] : playerPurchases;
    }

    public Instant getNextRestockAt() {
        return nextRestockAt;
    }

    public void setNextRestockAt(Instant nextRestockAt) {
        this.nextRestockAt = nextRestockAt;
    }

    public StockEntry[] getStocks() {
        return stocks == null ? new StockEntry[0] : stocks;
    }

    public void setStocks(StockEntry[] stocks) {
        this.stocks = stocks == null ? new StockEntry[0] : stocks;
    }

    public PlayerPurchaseEntry[] getPlayerPurchases() {
        return playerPurchases == null ? new PlayerPurchaseEntry[0] : playerPurchases;
    }

    public void setPlayerPurchases(PlayerPurchaseEntry[] playerPurchases) {
        this.playerPurchases = playerPurchases == null ? new PlayerPurchaseEntry[0] : playerPurchases;
    }

    public static final class StockEntry {
        public static final BuilderCodec<StockEntry> CODEC =
                BuilderCodec.builder(StockEntry.class, StockEntry::new)
                        .append(new KeyedCodec<>("Id", Codec.STRING),
                                (o, v) -> o.id = v,
                                o -> o.id)
                        .add()
                        .append(new KeyedCodec<>("Stock", Codec.INTEGER),
                                (o, v) -> o.stock = v,
                                o -> o.stock)
                        .add()
                        .build();

        private String id;
        private int stock;

        public StockEntry() {
        }

        public StockEntry(String id, int stock) {
            this.id = id;
            this.stock = stock;
        }

        public String getId() {
            return id;
        }

        public int getStock() {
            return stock;
        }

        public void setStock(int stock) {
            this.stock = stock;
        }
    }

    public static final class PlayerPurchaseEntry {
        public static final BuilderCodec<PlayerPurchaseEntry> CODEC =
                BuilderCodec.builder(PlayerPurchaseEntry.class, PlayerPurchaseEntry::new)
                        .append(new KeyedCodec<>("PlayerUuid", Codec.UUID_STRING),
                                (o, v) -> o.playerUuid = v,
                                o -> o.playerUuid)
                        .add()
                        .append(new KeyedCodec<>("Id", Codec.STRING),
                                (o, v) -> o.id = v,
                                o -> o.id)
                        .add()
                        .append(new KeyedCodec<>("Purchased", Codec.INTEGER),
                                (o, v) -> o.purchased = v,
                                o -> o.purchased)
                        .add()
                        .build();

        private UUID playerUuid;
        private String id;
        private int purchased;

        public PlayerPurchaseEntry() {
        }

        public PlayerPurchaseEntry(UUID playerUuid, String id, int purchased) {
            this.playerUuid = playerUuid;
            this.id = id;
            this.purchased = purchased;
        }

        public UUID getPlayerUuid() {
            return playerUuid;
        }

        public String getId() {
            return id;
        }

        public int getPurchased() {
            return purchased;
        }

        public void setPurchased(int purchased) {
            this.purchased = purchased;
        }
    }
}
