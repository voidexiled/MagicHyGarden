package com.voidexiled.magichygarden.commands.farm.subcommands.accept;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcel;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcelAccess;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcelInviteService;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcelManager;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcelRole;
import org.jspecify.annotations.NonNull;

public class FarmAcceptSubCommand extends AbstractPlayerCommand {
    public FarmAcceptSubCommand() {
        super("accept", "magichygarden.command.farm.accept.description");
    }

    @Override
    protected void execute(
            @NonNull CommandContext ctx,
            @NonNull Store<EntityStore> store,
            @NonNull Ref<EntityStore> playerEntityRef,
            @NonNull PlayerRef playerRef,
            @NonNull World world
    ) {
        MghgParcelInviteService.Invite invite = MghgParcelInviteService.acceptLatest(playerRef.getUuid());
        if (invite == null) {
            ctx.sendMessage(Message.raw("No tienes invitaciones pendientes."));
            return;
        }

        MghgParcel parcel = MghgParcelManager.getByOwner(invite.getOwnerId());
        if (parcel == null) {
            ctx.sendMessage(Message.raw("La granja de destino ya no existe."));
            return;
        }

        if (MghgParcelAccess.resolveRole(parcel, playerRef.getUuid()) == MghgParcelRole.VISITOR) {
            parcel.upsertMember(playerRef.getUuid(), MghgParcelRole.MEMBER);
            MghgParcelManager.save();
        }

        ctx.sendMessage(Message.raw(
                "Invitacion aceptada. Usa /farm visit " + invite.getOwnerId()
                        + " (owner: " + invite.getOwnerName() + ")"
        ));
    }
}
