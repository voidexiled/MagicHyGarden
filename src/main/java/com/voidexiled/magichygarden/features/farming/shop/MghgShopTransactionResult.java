package com.voidexiled.magichygarden.features.farming.shop;

import javax.annotation.Nonnull;

public record MghgShopTransactionResult(boolean success, boolean stateChanged, @Nonnull String message) {
    public static MghgShopTransactionResult ok(@Nonnull String message) {
        return new MghgShopTransactionResult(true, true, message);
    }

    public static MghgShopTransactionResult fail(@Nonnull String message) {
        return new MghgShopTransactionResult(false, false, message);
    }
}
