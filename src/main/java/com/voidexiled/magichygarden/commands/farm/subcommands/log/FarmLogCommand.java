package com.voidexiled.magichygarden.commands.farm.subcommands.log;

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
import com.voidexiled.magichygarden.features.farming.shop.MghgShopUiLogManager;
import javax.annotation.Nonnull;

public final class FarmLogCommand extends AbstractPlayerCommand {
    private static final int DEFAULT_LIMIT = 30;
    private static final int MAX_LIMIT = 120;

    private final DefaultArg<String> actionArg;
    private final DefaultArg<Integer> limitArg;

    public FarmLogCommand() {
        super("log", "magichygarden.command.farm.log.description");
        this.actionArg = withDefaultArg(
                "action",
                "magichygarden.command.farm.log.args.action.description",
                ArgTypes.STRING,
                "show",
                "show"
        );
        this.limitArg = withDefaultArg(
                "limit",
                "magichygarden.command.farm.log.args.limit.description",
                ArgTypes.INTEGER,
                DEFAULT_LIMIT,
                Integer.toString(DEFAULT_LIMIT)
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
        String action = normalize(actionArg.get(ctx));
        if ("clear".equals(action)) {
            int removed = MghgShopUiLogManager.clear(playerRef.getUuid());
            ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("Shop log cleared. Removed " + removed + " entries."));
            return;
        }

        int limit = clampLimit(limitArg.get(ctx));
        String[] lines = MghgShopUiLogManager.getRecentLines(playerRef.getUuid(), limit);
        if (lines.length == 0) {
            ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("No shop activity yet."));
            return;
        }

        ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("Shop activity log (latest " + lines.length + "):"));
        for (String line : lines) {
            if (line != null && !line.isBlank()) {
                ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text(line));
            }
        }
        ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("Use /farm log clear to reset your history."));
    }

    private static int clampLimit(int value) {
        if (value <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(MAX_LIMIT, value);
    }

    private static String normalize(String input) {
        if (input == null) {
            return "";
        }
        return input.trim().toLowerCase();
    }
}
