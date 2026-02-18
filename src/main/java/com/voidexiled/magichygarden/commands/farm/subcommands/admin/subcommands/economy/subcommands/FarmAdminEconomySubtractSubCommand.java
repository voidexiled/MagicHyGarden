package com.voidexiled.magichygarden.commands.farm.subcommands.admin.subcommands.economy.subcommands;

import com.hypixel.hytale.codec.validation.Validators;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.voidexiled.magichygarden.commands.farm.subcommands.admin.shared.FarmAdminCommandShared;
import com.voidexiled.magichygarden.features.farming.economy.MghgEconomyManager;
import com.voidexiled.magichygarden.features.farming.storage.MghgPlayerNameManager;
import org.jspecify.annotations.NonNull;

import java.util.Locale;
import java.util.UUID;

public class FarmAdminEconomySubtractSubCommand extends AbstractPlayerCommand {
    private final RequiredArg<String> targetArg;
    private final RequiredArg<Integer> amountArg;

    public FarmAdminEconomySubtractSubCommand() {
        super("subtract", "magichygarden.command.farm.admin.economy.subtract.description");

        this.targetArg = withRequiredArg(
                "target",
                "magichygarden.command.farm.admin.economy.target.description",
                ArgTypes.STRING
        );
        this.amountArg = withRequiredArg(
                "amount",
                "magichygarden.command.farm.admin.economy.subtract.args.amount.description",
                ArgTypes.INTEGER
        ).addValidator(Validators.greaterThan(0));

        // Keep backward compatibility with the old typo command.
        addAliases("sub", "substract");
    }

    @Override
    protected void execute(@NonNull CommandContext commandContext,
                           @NonNull Store<EntityStore> store,
                           @NonNull Ref<EntityStore> ref,
                           @NonNull PlayerRef executor,
                           @NonNull World world) {
        MghgPlayerNameManager.remember(executor);
        String targetToken = targetArg.get(commandContext);
        UUID targetUuid = FarmAdminCommandShared.resolveUuid(executor, targetToken);
        if (targetUuid == null) {
            commandContext.sendMessage(Message.raw("Target invalido. Usa self, UUID o username cacheado/online."));
            return;
        }
        int amount = amountArg.get(commandContext);

        boolean ok = MghgEconomyManager.withdraw(targetUuid, amount);
        if (!ok) {
            String targetName = MghgPlayerNameManager.resolve(targetUuid);
            commandContext.sendMessage(Message.raw("Failed to subtract $" + amount + " from " + targetName + "'s balance."));
            return;
        }

        double updated = MghgEconomyManager.getBalance(targetUuid);
        String targetName = MghgPlayerNameManager.resolve(targetUuid);
        commandContext.sendMessage(Message.raw(String.format(Locale.ROOT, "Balance actualizado %s = $%.2f", targetName, updated)));
    }
}
