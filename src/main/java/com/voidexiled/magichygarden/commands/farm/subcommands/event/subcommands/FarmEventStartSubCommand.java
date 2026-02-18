package com.voidexiled.magichygarden.commands.farm.subcommands.event.subcommands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.DefaultArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.voidexiled.magichygarden.commands.farm.subcommands.event.shared.FarmEventCommandShared;
import com.voidexiled.magichygarden.features.farming.events.MghgFarmEventScheduler;
import com.voidexiled.magichygarden.features.farming.state.MutationEventType;
import org.jspecify.annotations.NonNull;

public class FarmEventStartSubCommand extends AbstractPlayerCommand {
    private final RequiredArg<String> typeArg;
    private final DefaultArg<String> eventIdArg;
    private final DefaultArg<Integer> durationArg;

    public FarmEventStartSubCommand() {
        super("start", "magichygarden.command.farm.event.start.description");
        this.typeArg = withRequiredArg(
                "type",
                "magichygarden.command.farm.event.start.args.type.description",
                ArgTypes.STRING
        );
        this.eventIdArg = withDefaultArg(
                "eventId",
                "magichygarden.command.farm.event.start.args.eventId.description",
                ArgTypes.STRING,
                "random",
                "random"
        );
        this.durationArg = withDefaultArg(
                "durationSec",
                "magichygarden.command.farm.event.start.args.durationSec.description",
                ArgTypes.INTEGER,
                0,
                "0"
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
        if (type == MutationEventType.ANY) {
            commandContext.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text(
                    "Uso: /farm event start <weather|lunar> [eventId|random] [durationSec]"
            ));
            return;
        }

        String eventId = FarmEventCommandShared.raw(eventIdArg.get(commandContext));
        if (eventId.isBlank()) {
            eventId = "random";
        }
        Integer duration = durationArg.get(commandContext);
        Integer durationSec = duration == null || duration <= 0 ? null : duration;
        boolean started = MghgFarmEventScheduler.forceStartConfiguredEvent(type, eventId, durationSec);
        if (!started) {
            commandContext.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text(
                    "No pude iniciar evento. Revisa tipo/id con /farm event list."
            ));
            return;
        }
        commandContext.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("Evento forzado iniciado."));
        FarmEventCommandShared.sendStatus(commandContext);
    }
}
