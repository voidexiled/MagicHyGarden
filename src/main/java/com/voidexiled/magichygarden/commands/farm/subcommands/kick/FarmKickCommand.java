package com.voidexiled.magichygarden.commands.farm.subcommands.kick;

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
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcelManager;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcelRole;
import com.voidexiled.magichygarden.features.farming.storage.MghgPlayerNameManager;
import org.jspecify.annotations.NonNull;

import java.util.UUID;

public class FarmKickCommand extends AbstractPlayerCommand {
    private final RequiredArg<String> playerArg;

    public FarmKickCommand() {
        super("kick", "magichygarden.command.farm.kick.description");
        this.playerArg = withRequiredArg(
                "player",
                "magichygarden.command.farm.kick.args.player.description",
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
        UUID targetId = MghgPlayerNameManager.resolveUuid(playerArg.get(ctx));
        if (targetId == null) {
            ctx.sendMessage(Message.raw("Jugador inválido. Usa UUID o username online/cacheado."));
            return;
        }
        String targetName = MghgPlayerNameManager.resolve(targetId);
        if (targetId.equals(playerRef.getUuid())) {
            ctx.sendMessage(Message.raw("No puedes expulsarte a ti mismo."));
            return;
        }

        MghgParcel parcel = FarmParcelCommandUtil.resolveManagedParcel(playerRef.getUuid(), world);
        if (parcel == null) {
            ctx.sendMessage(Message.raw("No tienes una parcela gestionable en este contexto."));
            return;
        }

        MghgParcelRole role = MghgParcelAccess.resolveRole(parcel, playerRef.getUuid());
        if (role != MghgParcelRole.OWNER && role != MghgParcelRole.MANAGER) {
            ctx.sendMessage(Message.raw("No tienes permisos para expulsar."));
            return;
        }

        boolean removed = parcel.removeMember(targetId);
        if (removed) {
            MghgParcelManager.save();
            ctx.sendMessage(Message.raw("Expulsaste a " + targetName + " de tu granja."));
        } else {
            ctx.sendMessage(Message.raw("Ese jugador no está en tu granja."));
        }
    }
}
