package com.voidexiled.magichygarden.commands.farm.subcommands.deny;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcelInviteService;
import javax.annotation.Nonnull;

public class FarmDenySubCommand extends AbstractPlayerCommand {
    public FarmDenySubCommand() {
        super("deny", "magichygarden.command.farm.deny.description");
    }

    @Override
    protected void execute(
            @Nonnull CommandContext ctx,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> playerEntityRef,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
    ) {
        MghgParcelInviteService.Invite invite = MghgParcelInviteService.denyLatest(playerRef.getUuid());
        if (invite == null) {
            ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("No tienes invitaciones pendientes."));
            return;
        }
        ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("Invitación rechazada de " + invite.getOwnerName() + "."));
    }
}
