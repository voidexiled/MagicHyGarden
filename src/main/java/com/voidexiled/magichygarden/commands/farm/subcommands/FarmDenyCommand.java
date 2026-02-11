package com.voidexiled.magichygarden.commands.farm.subcommands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcelInviteService;
import org.jspecify.annotations.NonNull;

public class FarmDenyCommand extends AbstractPlayerCommand {
    public FarmDenyCommand() {
        super("deny", "magichygarden.command.farm.deny.description");
    }

    @Override
    protected void execute(
            @NonNull CommandContext ctx,
            @NonNull Store<EntityStore> store,
            @NonNull Ref<EntityStore> playerEntityRef,
            @NonNull PlayerRef playerRef,
            @NonNull World world
    ) {
        MghgParcelInviteService.Invite invite = MghgParcelInviteService.denyLatest(playerRef.getUuid());
        if (invite == null) {
            ctx.sendMessage(Message.raw("No tienes invitaciones pendientes."));
            return;
        }
        ctx.sendMessage(Message.raw("Invitación rechazada de " + invite.getOwnerName() + "."));
    }
}
