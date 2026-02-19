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
import javax.annotation.Nonnull;

import java.util.UUID;

public class FarmMembersCommand extends AbstractPlayerCommand {
    public FarmMembersCommand() {
        super("members", "magichygarden.command.farm.members.description");
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
        MghgParcel parcel = FarmParcelCommandUtil.resolveManagedParcel(playerRef.getUuid(), world);
        if (parcel == null) {
            ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("No tienes una parcela gestionable en este contexto."));
            return;
        }

        ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("Owner: " + resolveName(parcel.getOwner()) + " (" + parcel.getOwner() + ")"));
        MghgParcelMember[] members = parcel.getMembers();
        if (members == null || members.length == 0) {
            ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("Members: 0"));
            return;
        }

        ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("Members: " + members.length));
        for (MghgParcelMember member : members) {
            if (member == null || member.getUuid() == null || member.getRole() == null) {
                continue;
            }
            UUID uuid = member.getUuid();
            ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text(" - " + resolveName(uuid) + " | " + member.getRole().name()));
        }
    }

    private static String resolveName(@Nonnull UUID playerUuid) {
        return MghgPlayerNameManager.resolve(playerUuid);
    }
}
