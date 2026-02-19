package com.voidexiled.magichygarden.commands.farm.subcommands.balance;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.voidexiled.magichygarden.features.farming.economy.MghgEconomyManager;
import com.voidexiled.magichygarden.features.farming.storage.MghgPlayerNameManager;
import javax.annotation.Nonnull;

import java.util.Locale;

public class FarmBalanceCommand extends AbstractPlayerCommand {
    public FarmBalanceCommand() {
        super("balance", "magichygarden.command.farm.balance.description");
    }

    @Override
    protected void execute(
            @Nonnull CommandContext ctx,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> playerEntityRef,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
    ) {
        MghgPlayerNameManager.remember(playerRef);
        double balance = MghgEconomyManager.getBalance(playerRef.getUuid());
        ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("Balance: $" + formatMoney(balance)));
    }

    private static String formatMoney(double value) {
        return String.format(Locale.ROOT, "%.2f", Math.max(0.0, value));
    }
}
