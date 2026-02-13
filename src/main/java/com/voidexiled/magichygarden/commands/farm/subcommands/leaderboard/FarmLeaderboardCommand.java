package com.voidexiled.magichygarden.commands.farm.subcommands.leaderboard;

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
import com.voidexiled.magichygarden.features.farming.economy.MghgEconomyManager;
import com.voidexiled.magichygarden.features.farming.economy.MghgEconomyState;
import com.voidexiled.magichygarden.features.farming.storage.MghgPlayerNameManager;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Locale;

public class FarmLeaderboardCommand extends AbstractPlayerCommand {
    private final DefaultArg<Integer> limitArg;

    public FarmLeaderboardCommand() {
        super("leaderboard", "magichygarden.command.farm.leaderboard.description");
        this.limitArg = withDefaultArg(
                "limit",
                "magichygarden.command.farm.leaderboard.args.limit.description",
                ArgTypes.INTEGER,
                10,
                "10"
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
        int limit = Math.max(1, Math.min(50, limitArg.get(ctx)));
        List<MghgEconomyState.Entry> top = MghgEconomyManager.getTopBalances(limit);
        if (top.isEmpty()) {
            ctx.sendMessage(Message.raw("Leaderboard vacio."));
            return;
        }

        ctx.sendMessage(Message.raw("Top balances (global):"));
        int rank = 1;
        for (MghgEconomyState.Entry entry : top) {
            if (entry == null || entry.getUuid() == null) {
                continue;
            }
            String name = MghgPlayerNameManager.resolve(entry.getUuid());
            String line = String.format(
                    Locale.ROOT,
                    "#%d %s - $%.2f",
                    rank,
                    name,
                    Math.max(0.0d, entry.getBalance())
            );
            ctx.sendMessage(Message.raw(line));
            rank++;
        }
    }
}
