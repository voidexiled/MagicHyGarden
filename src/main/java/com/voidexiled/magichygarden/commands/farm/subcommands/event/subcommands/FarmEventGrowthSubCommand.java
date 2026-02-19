package com.voidexiled.magichygarden.commands.farm.subcommands.event.subcommands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.DefaultArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.voidexiled.magichygarden.commands.farm.subcommands.event.shared.FarmEventCommandShared;
import com.voidexiled.magichygarden.features.farming.events.MghgFarmEventScheduler;
import javax.annotation.Nonnull;

import java.util.Locale;

public class FarmEventGrowthSubCommand extends AbstractPlayerCommand {
    private final RequiredArg<String> ownerOfflineArg;
    private final DefaultArg<String> serverEmptyArg;

    public FarmEventGrowthSubCommand() {
        super("growth", "magichygarden.command.farm.event.growth.description");
        this.ownerOfflineArg = withRequiredArg(
                "ownerOffline",
                "magichygarden.command.farm.event.growth.args.ownerOffline.description",
                ArgTypes.STRING
        );
        this.serverEmptyArg = withDefaultArg(
                "serverEmpty",
                "magichygarden.command.farm.event.growth.args.serverEmpty.description",
                ArgTypes.STRING,
                "",
                ""
        );
    }

    @Override
    protected void execute(
            @Nonnull CommandContext commandContext,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
    ) {
        String ownerOfflineToken = FarmEventCommandShared.raw(ownerOfflineArg.get(commandContext));
        if ("reset".equals(FarmEventCommandShared.normalize(ownerOfflineToken))) {
            MghgFarmEventScheduler.setGrowthPolicyOverrides(null, null);
            commandContext.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("Growth overrides reseteados (vuelven al JSON)."));
            return;
        }

        Boolean ownerOffline = FarmEventCommandShared.parseBoolean(ownerOfflineToken);
        if (ownerOffline == null) {
            commandContext.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text(
                    "Valor invalido para ownerOffline. Usa true/false o reset."
            ));
            commandContext.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text(
                    "Uso: /farm event growth <true|false|reset> [serverEmpty true|false]"
            ));
            return;
        }

        String serverEmptyToken = FarmEventCommandShared.raw(serverEmptyArg.get(commandContext));
        Boolean serverEmpty = serverEmptyToken.isBlank()
                ? ownerOffline
                : FarmEventCommandShared.parseBoolean(serverEmptyToken);
        if (serverEmpty == null) {
            commandContext.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("Valor invalido para serverEmpty. Usa true/false."));
            return;
        }

        MghgFarmEventScheduler.setGrowthPolicyOverrides(ownerOffline, serverEmpty);
        commandContext.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text(String.format(
                Locale.ROOT,
                "Growth overrides actualizados: ownerOffline=%s | serverEmpty=%s",
                ownerOffline,
                serverEmpty
        )));
    }
}
