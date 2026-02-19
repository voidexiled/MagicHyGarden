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
import javax.annotation.Nonnull;

public class FarmEventStopSubCommand extends AbstractPlayerCommand {
    public FarmEventStopSubCommand() {
        super("stop", "magichygarden.command.farm.event.stop.description");
    }

    @Override
    protected void execute(
            @Nonnull CommandContext commandContext,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
    ) {
        MghgFarmEventScheduler.forceStopActiveEvent();
        commandContext.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("Evento activo forzado a detenerse."));
        FarmEventCommandShared.sendStatus(commandContext);
    }
}
