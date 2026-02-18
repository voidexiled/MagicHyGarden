package com.voidexiled.magichygarden.commands.farm.subcommands.event.subcommands;

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
import com.voidexiled.magichygarden.commands.farm.subcommands.event.shared.FarmEventCommandShared;
import com.voidexiled.magichygarden.features.farming.events.MghgFarmEventConfig;
import com.voidexiled.magichygarden.features.farming.events.MghgFarmEventScheduler;
import com.voidexiled.magichygarden.features.farming.state.MutationEventType;
import org.jspecify.annotations.NonNull;

public class FarmEventListSubCommand extends AbstractPlayerCommand {
    private final DefaultArg<String> typeArg;

    public FarmEventListSubCommand() {
        super("list", "magichygarden.command.farm.event.list.description");
        this.typeArg = withDefaultArg(
                "type",
                "magichygarden.command.farm.event.list.args.type.description",
                ArgTypes.STRING,
                "all",
                "all"
        );
    }

    @Override
    protected void execute(
            @NonNull CommandContext commandContext,
            @NonNull Store<EntityStore> store,
            @NonNull Ref<EntityStore> ref,
            @NonNull PlayerRef playerRef,
            @NonNull World world
    ) {
        MutationEventType type = FarmEventCommandShared.parseEventType(typeArg.get(commandContext));
        MghgFarmEventConfig cfg = MghgFarmEventScheduler.getConfig();
        if (cfg == null) {
            commandContext.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("Config de events no disponible."));
            return;
        }
        if (type == MutationEventType.WEATHER || type == MutationEventType.ANY) {
            FarmEventCommandShared.sendEventGroupList(commandContext, "WEATHER", cfg.getRegular());
        }
        if (type == MutationEventType.LUNAR || type == MutationEventType.ANY) {
            FarmEventCommandShared.sendEventGroupList(commandContext, "LUNAR", cfg.getLunar());
        }
    }
}
