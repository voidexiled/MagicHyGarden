package com.voidexiled.magichygarden.commands.farm.subcommands.role;

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

import javax.annotation.Nullable;
import java.util.Locale;
import java.util.UUID;

public class FarmRoleCommand extends AbstractPlayerCommand {
    private final RequiredArg<String> playerArg;
    private final RequiredArg<String> roleArg;

    public FarmRoleCommand() {
        super("role", "magichygarden.command.farm.role.description");
        this.playerArg = withRequiredArg(
                "player",
                "magichygarden.command.farm.role.args.player.description",
                ArgTypes.STRING
        );
        this.roleArg = withRequiredArg(
                "role",
                "magichygarden.command.farm.role.args.role.description",
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
            ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("Jugador inválido. Usa UUID o username online/cacheado."));
            return;
        }
        String targetName = MghgPlayerNameManager.resolve(targetId);

        MghgParcel parcel = FarmParcelCommandUtil.resolveManagedParcel(playerRef.getUuid(), world);
        if (parcel == null) {
            ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("No tienes una parcela gestionable en este contexto."));
            return;
        }

        MghgParcelRole myRole = MghgParcelAccess.resolveRole(parcel, playerRef.getUuid());
        if (myRole != MghgParcelRole.OWNER && myRole != MghgParcelRole.MANAGER) {
            ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("No tienes permisos para cambiar roles."));
            return;
        }

        MghgParcelRole desired = parseRole(roleArg.get(ctx));
        if (desired == null) {
            ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("Rol inválido. Usa: owner, manager, member, visitor"));
            return;
        }

        if (desired == MghgParcelRole.OWNER) {
            ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("No puedes transferir ownership con este comando."));
            return;
        }

        if (desired == MghgParcelRole.MANAGER && myRole != MghgParcelRole.OWNER) {
            ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("Solo el owner puede asignar MANAGER."));
            return;
        }

        if (targetId.equals(parcel.getOwner())) {
            ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("No puedes cambiar el rol del owner."));
            return;
        }

        if (desired == MghgParcelRole.VISITOR) {
            parcel.removeMember(targetId);
        } else {
            parcel.upsertMember(targetId, desired);
        }
        MghgParcelManager.save();
        ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("Rol actualizado: " + targetName + " -> " + desired.name()));
    }

    private static @Nullable MghgParcelRole parseRole(@Nullable String raw) {
        if (raw == null) return null;
        String key = raw.trim().toUpperCase(Locale.ROOT);
        return switch (key) {
            case "OWNER" -> MghgParcelRole.OWNER;
            case "MANAGER", "MOD" -> MghgParcelRole.MANAGER;
            case "MEMBER" -> MghgParcelRole.MEMBER;
            case "VISITOR", "GUEST" -> MghgParcelRole.VISITOR;
            default -> null;
        };
    }
}
