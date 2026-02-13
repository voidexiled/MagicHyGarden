package com.voidexiled.magichygarden.commands.farm.subcommands.members;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.voidexiled.magichygarden.commands.farm.FarmParcelCommandUtil;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcel;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcelMember;
import com.voidexiled.magichygarden.features.farming.storage.MghgPlayerNameManager;
import org.jspecify.annotations.NonNull;

import java.util.UUID;

public class FarmMembersCommand extends AbstractPlayerCommand {
    public FarmMembersCommand() {
        super("members", "magichygarden.command.farm.members.description");
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
        MghgParcel parcel = FarmParcelCommandUtil.resolveManagedParcel(playerRef.getUuid(), world);
        if (parcel == null) {
            ctx.sendMessage(Message.raw("No tienes una parcela gestionable en este contexto."));
            return;
        }

        ctx.sendMessage(Message.raw("Owner: " + resolveName(parcel.getOwner()) + " (" + parcel.getOwner() + ")"));
        MghgParcelMember[] members = parcel.getMembers();
        if (members == null || members.length == 0) {
            ctx.sendMessage(Message.raw("Members: 0"));
            return;
        }

        ctx.sendMessage(Message.raw("Members: " + members.length));
        for (MghgParcelMember member : members) {
            if (member == null || member.getUuid() == null || member.getRole() == null) {
                continue;
            }
            UUID uuid = member.getUuid();
            ctx.sendMessage(Message.raw(" - " + resolveName(uuid) + " | " + member.getRole().name()));
        }
    }

    private static String resolveName(@NonNull UUID playerUuid) {
        return MghgPlayerNameManager.resolve(playerUuid);
    }
}
