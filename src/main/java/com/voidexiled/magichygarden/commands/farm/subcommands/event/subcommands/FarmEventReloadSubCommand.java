package com.voidexiled.magichygarden.commands.farm.subcommands.event.subcommands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.voidexiled.magichygarden.commands.farm.subcommands.event.shared.FarmEventCommandShared;
import com.voidexiled.magichygarden.features.farming.events.MghgFarmEventScheduler;
import org.jspecify.annotations.NonNull;

public class FarmEventReloadSubCommand extends AbstractPlayerCommand {
    public FarmEventReloadSubCommand() {
        super("reload", "magichygarden.command.farm.event.reload.description");
    }

    @Override
    protected void execute(
            @NonNull CommandContext commandContext,
            @NonNull Store<EntityStore> store,
            @NonNull Ref<EntityStore> ref,
            @NonNull PlayerRef playerRef,
            @NonNull World world
    ) {
        MghgFarmEventScheduler.reload();
        commandContext.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("Farm events recargados."));
        FarmEventCommandShared.sendStatus(commandContext);
    }
}
