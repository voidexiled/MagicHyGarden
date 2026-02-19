package com.voidexiled.magichygarden.commands.farm.subcommands.invites;

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

import java.time.Instant;
import java.util.List;

public class FarmInvitesSubCommand extends AbstractPlayerCommand {
    public FarmInvitesSubCommand() {
        super("invites", "magichygarden.command.farm.invites.description");
    }

    @Override
    protected void execute(
            @Nonnull CommandContext ctx,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> playerEntityRef,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
    ) {
        List<MghgParcelInviteService.Invite> pending = MghgParcelInviteService.getPending(playerRef.getUuid());
        if (pending.isEmpty()) {
            ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("No tienes invitaciones pendientes."));
            return;
        }
        long now = Instant.now().getEpochSecond();
        ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("Invitaciones pendientes: " + pending.size()));
        for (MghgParcelInviteService.Invite invite : pending) {
            long remaining = invite.getRemainingSeconds(now);
            ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text(
                    " - owner=" + invite.getOwnerName()
                            + " (" + invite.getOwnerId() + ")"
                            + " | inviter=" + invite.getInviterName()
                            + " | expira en " + formatDuration(remaining)
            ));
        }
        ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("Usa /farm accept para aceptar la más reciente, o /farm deny para rechazarla."));
    }

    private static String formatDuration(long seconds) {
        long safe = Math.max(0L, seconds);
        long minutes = safe / 60L;
        long rem = safe % 60L;
        return minutes + "m " + rem + "s";
    }
}
