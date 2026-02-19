package com.voidexiled.magichygarden.commands.farm.subcommands.leave;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.DefaultArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcel;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcelAccess;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcelManager;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcelRole;
import com.voidexiled.magichygarden.features.farming.storage.MghgPlayerNameManager;
import javax.annotation.Nonnull;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class FarmLeaveCommand extends AbstractPlayerCommand {
    private final DefaultArg<String> ownerArg;

    public FarmLeaveCommand() {
        super("leave", "magichygarden.command.farm.leave.description");
        this.ownerArg = withDefaultArg(
                "owner",
                "magichygarden.command.farm.leave.args.owner.description",
                ArgTypes.STRING,
                "",
                ""
        );
    }

    @Override
    protected void execute(
            @Nonnull CommandContext ctx,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> playerEntityRef,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
    ) {
        MghgPlayerNameManager.remember(playerRef);
        UUID playerId = playerRef.getUuid();
        String rawOwner = normalize(ownerArg.get(ctx));

        if (!rawOwner.isBlank()) {
            UUID ownerId = resolveOwner(rawOwner);
            if (ownerId == null) {
                ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("Owner invalido. Usa username cacheado/online o UUID."));
                return;
            }
            leaveFromOwner(ctx, playerId, ownerId);
            return;
        }

        MghgParcel worldParcel = MghgParcelAccess.resolveParcel(world);
        if (worldParcel != null && canLeaveRole(worldParcel, playerId)) {
            applyLeave(ctx, worldParcel, playerId);
            return;
        }

        List<MghgParcel> memberships = resolveMemberships(playerId);
        if (memberships.isEmpty()) {
            ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("No perteneces a ninguna granja ajena."));
            return;
        }
        if (memberships.size() == 1) {
            applyLeave(ctx, memberships.get(0), playerId);
            return;
        }

        ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text(
                "Perteneces a " + memberships.size()
                        + " granjas. Usa /farm leave <ownerUuid> para elegir:"
        ));
        for (int i = 0; i < Math.min(10, memberships.size()); i++) {
            MghgParcel parcel = memberships.get(i);
            ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text(" - owner=" + resolveName(parcel.getOwner()) + " | " + parcel.getOwner()));
        }
    }

    private static void leaveFromOwner(@Nonnull CommandContext ctx, @Nonnull UUID playerId, @Nonnull UUID ownerId) {
        MghgParcel parcel = MghgParcelManager.getByOwner(ownerId);
        if (parcel == null) {
            ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("No existe una granja para ese owner."));
            return;
        }
        if (!canLeaveRole(parcel, playerId)) {
            ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("No tienes rol de member/manager en esa granja."));
            return;
        }
        applyLeave(ctx, parcel, playerId);
    }

    private static boolean canLeaveRole(@Nonnull MghgParcel parcel, @Nonnull UUID playerId) {
        MghgParcelRole role = MghgParcelAccess.resolveRole(parcel, playerId);
        return role == MghgParcelRole.MEMBER || role == MghgParcelRole.MANAGER;
    }

    private static void applyLeave(@Nonnull CommandContext ctx, @Nonnull MghgParcel parcel, @Nonnull UUID playerId) {
        boolean removed = parcel.removeMember(playerId);
        if (!removed) {
            ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("No se pudo salir de la granja."));
            return;
        }
        MghgParcelManager.save();
        ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("Saliste de la granja de " + resolveName(parcel.getOwner()) + "."));
    }

    private static @Nullable UUID resolveOwner(@Nonnull String rawOwner) {
        return MghgPlayerNameManager.resolveUuid(rawOwner);
    }

    private static List<MghgParcel> resolveMemberships(@Nonnull UUID playerId) {
        List<MghgParcel> result = new ArrayList<>();
        for (MghgParcel parcel : MghgParcelManager.all()) {
            if (parcel == null || parcel.getOwner() == null || playerId.equals(parcel.getOwner())) {
                continue;
            }
            if (canLeaveRole(parcel, playerId)) {
                result.add(parcel);
            }
        }
        return result;
    }

    private static String resolveName(@Nonnull UUID owner) {
        return MghgPlayerNameManager.resolve(owner);
    }

    private static String normalize(@Nullable String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim();
    }
}
