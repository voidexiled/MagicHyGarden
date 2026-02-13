package com.voidexiled.magichygarden.commands.farm.subcommands.help;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.jspecify.annotations.NonNull;

public class FarmHelpCommand extends AbstractPlayerCommand {
    public FarmHelpCommand() {
        super("help", "magichygarden.command.farm.help.description");
        addAliases("h", "?");
    }

    @Override
    protected void execute(
            @NonNull CommandContext ctx,
            @NonNull Store<EntityStore> store,
            @NonNull Ref<EntityStore> playerEntityRef,
            @NonNull PlayerRef playerRef,
            @NonNull World world
    ) {
        ctx.sendMessage(Message.raw("Farm quick help:"));
        ctx.sendMessage(Message.raw(" - /farm home | /farm lobby | /farm survival"));
        ctx.sendMessage(Message.raw(" - /farm visit <player> | /farm farms"));
        ctx.sendMessage(Message.raw(" - /farm spawn status | /farm spawn set | /farm spawn reset | /farm setspawn"));
        ctx.sendMessage(Message.raw(" - /farm members | /farm invite <player> | /farm invites | /farm accept | /farm deny"));
        ctx.sendMessage(Message.raw(" - /farm role <player> <manager|member|visitor> | /farm kick <player>"));
        ctx.sendMessage(Message.raw(" - /farm balance | /farm stock | /farm shop"));
        ctx.sendMessage(Message.raw(" - /farm buy <shopId> [qty] | /farm buymax <shopId>"));
        ctx.sendMessage(Message.raw(" - /farm sell <shopId> [qty] | /farm sellall [shopId|all]"));
        ctx.sendMessage(Message.raw(" - /farm event help | /farm reload [all|events|worlds|parcels|invites|economy|shop|names]"));
        ctx.sendMessage(Message.raw(" - /farm admin help"));
    }
}
