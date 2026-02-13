package com.voidexiled.magichygarden.commands.farm.subcommands.invite;

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
import com.voidexiled.magichygarden.commands.farm.FarmParcelCommandUtil;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcel;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcelAccess;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcelInviteService;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcelRole;
import com.voidexiled.magichygarden.features.farming.storage.MghgPlayerNameManager;
import org.jspecify.annotations.NonNull;

import java.util.UUID;

public class FarmInviteSubCommand extends AbstractPlayerCommand {
    private final RequiredArg<String> playerArg;

    public FarmInviteSubCommand() {
        super("invite", "magichygarden.command.farm.invite.description");
        this.playerArg = withRequiredArg(
                "player",
                "magichygarden.command.farm.invite.args.player.description",
                ArgTypes.STRING
        );
    }

    @Override
    protected void execute(
            @NonNull CommandContext ctx,
            @NonNull Store<EntityStore> store,
            @NonNull Ref<EntityStore> playerEntityRef,
            @NonNull PlayerRef playerRef,
            @NonNull World world
    ) {
        MghgPlayerNameManager.remember(playerRef);
        String rawTarget = playerArg.get(ctx);
        UUID targetId = MghgPlayerNameManager.resolveUuid(rawTarget);
        if (targetId == null) {
            ctx.sendMessage(Message.raw("Jugador inválido. Usa UUID o username online/cacheado."));
            return;
        }
        String targetName = MghgPlayerNameManager.resolve(targetId);
        if (targetId.equals(playerRef.getUuid())) {
            ctx.sendMessage(Message.raw("No puedes invitarte a ti mismo."));
            return;
        }

        MghgParcel parcel = FarmParcelCommandUtil.resolveManagedParcel(playerRef.getUuid(), world);
        if (parcel == null) {
            ctx.sendMessage(Message.raw("No tienes una parcela gestionable en este contexto."));
            return;
        }

        MghgParcelRole role = MghgParcelAccess.resolveRole(parcel, playerRef.getUuid());
        if (role != MghgParcelRole.OWNER && role != MghgParcelRole.MANAGER) {
            ctx.sendMessage(Message.raw("No tienes permisos para invitar."));
            return;
        }

        if (MghgParcelAccess.resolveRole(parcel, targetId) != MghgParcelRole.VISITOR) {
            ctx.sendMessage(Message.raw("Ese jugador ya forma parte de la granja."));
            return;
        }

        MghgParcelInviteService.createInvite(
                parcel.getOwner(),
                resolveOwnerName(parcel),
                playerRef.getUuid(),
                playerRef.getUsername(),
                targetId,
                targetName
        );
        ctx.sendMessage(Message.raw(
                "Invitación enviada a " + targetName
                        + ". Debe aceptar con /farm accept (expira en 15m)."
        ));
    }

    private static String resolveOwnerName(@NonNull MghgParcel parcel) {
        PlayerRef ownerRef = com.hypixel.hytale.server.core.universe.Universe.get()
                .getPlayer(parcel.getOwner());
        if (ownerRef != null && ownerRef.getUsername() != null && !ownerRef.getUsername().isBlank()) {
            MghgPlayerNameManager.remember(ownerRef);
            return ownerRef.getUsername();
        }
        return MghgPlayerNameManager.resolve(parcel.getOwner());
    }
}
