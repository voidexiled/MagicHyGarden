package com.voidexiled.magichygarden.commands.farm.subcommands.help;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

public class FarmHelpCommand extends AbstractPlayerCommand {
    public FarmHelpCommand() {
        super("help", "magichygarden.command.farm.help.description");
        addAliases("h", "?");
    }

    @Override
    protected void execute(
            @Nonnull CommandContext ctx,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> playerEntityRef,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
    ) {
        ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("Farm quick help:"));
        ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text(" - /farm home | /farm lobby | /farm survival"));
        ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text(" - /farm visit <player> | /farm farms"));
        ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text(" - /farm spawn status | /farm spawn set | /farm spawn reset | /farm setspawn"));
        ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text(" - /farm members | /farm invite <player> | /farm invites | /farm accept | /farm deny"));
        ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text(" - /farm role <player> <manager|member|visitor> | /farm kick <player>"));
        ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text(" - /farm balance | /farm stock | /farm shop"));
        ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text(" - /farm perks | /farm perks close | /farm perks status | /farm perks upgrade [fertile_soil]"));
        ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text(" - /farm buy <shopId> [qty] | /farm buymax <shopId>"));
        ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text(" - /farm sell <shopId> [qty] | /farm sellall [shopId|all]"));
        ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text(" - /farm event status | /farm event list [weather|lunar|all] | /farm reload [all|events|worlds|parcels|invites|economy|perks|shop|names]"));
        ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text(" - /farm admin status | /farm admin paths | /farm admin parcel | /farm admin world | /farm admin stock | /farm admin economy | /farm admin perks"));
    }
}
