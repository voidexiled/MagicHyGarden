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

public class FarmAdminEconomyAddSubCommand extends AbstractTargetPlayerCommand {
    private final RequiredArg<Integer> amountArg;


    public FarmAdminEconomyAddSubCommand() {
        super("add", "magichygarden.command.farm.admin.economy.add.description");

        amountArg = withRequiredArg(
                "amount",
                "magichygarden.command.farm.admin.economy.add.args.amount.description",
                ArgTypes.INTEGER
        ).addValidator(Validators.greaterThan(0));
    }

    @Override
    protected void execute(@NonNull CommandContext commandContext,
                           @Nullable Ref<EntityStore> ref,
                           @NonNull Ref<EntityStore> ref1,
                           @NonNull PlayerRef playerRef,
                           @NonNull World world,
                           @NonNull Store<EntityStore> store) {
        int amount = amountArg.get(commandContext);

        MghgEconomyManager.deposit(playerRef.getUuid(), amount);

        double updated = MghgEconomyManager.getBalance(playerRef.getUuid());
        commandContext.sendMessage(Message.raw(String.format(Locale.ROOT, "Balance actualizado %s = $%.2f", playerRef.getUsername(), updated)));
    }
}
