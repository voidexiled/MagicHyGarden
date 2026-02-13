package com.voidexiled.magichygarden.commands.farm.subcommands.admin.subcommands.economy.subcommands;

import com.hypixel.hytale.codec.validation.Validators;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractTargetPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.voidexiled.magichygarden.features.farming.economy.MghgEconomyManager;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

public class FarmAdminEconomySubstractSubCommand extends AbstractTargetPlayerCommand {
    private final RequiredArg<Integer> amountArg;


    public FarmAdminEconomySubstractSubCommand() {
        super("substract", "magichygarden.command.farm.admin.economy.substract.description");

        amountArg = withRequiredArg(
                "amount",
                "magichygarden.command.farm.admin.economy.substract.args.amount.description",
                ArgTypes.INTEGER
        ).addValidator(Validators.greaterThan(0));

        addAliases("sub");
    }

    @Override
    protected void execute(@NonNull CommandContext commandContext,
                           @Nullable Ref<EntityStore> ref,
                           @NonNull Ref<EntityStore> ref1,
                           @NonNull PlayerRef playerRef,
                           @NonNull World world,
                           @NonNull Store<EntityStore> store) {
        int amount = amountArg.get(commandContext);

        boolean ok = MghgEconomyManager.withdraw(playerRef.getUuid(), amount);
        if (!ok) {
            commandContext.sendMessage(Message.raw("Failed to substract $" + amount + " from " + playerRef.getUsername() + "'s balance."));
            return;
        }

        double updated = MghgEconomyManager.getBalance(playerRef.getUuid());
        commandContext.sendMessage(Message.raw(String.format(Locale.ROOT, "Balance actualizado %s = $%.2f", playerRef.getUsername(), updated)));
    }
}
