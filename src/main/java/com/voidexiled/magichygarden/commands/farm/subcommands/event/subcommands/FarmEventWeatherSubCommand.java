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
import org.jspecify.annotations.NonNull;

public class FarmEventWeatherSubCommand extends AbstractPlayerCommand {
    private final RequiredArg<String> weatherIdArg;
    private final DefaultArg<String> modeArg;

    public FarmEventWeatherSubCommand() {
        super("weather", "magichygarden.command.farm.event.weather.description");
        this.weatherIdArg = withRequiredArg(
                "weatherId",
                "magichygarden.command.farm.event.weather.args.weatherId.description",
                ArgTypes.STRING
        );
        this.modeArg = withDefaultArg(
                "mode",
                "magichygarden.command.farm.event.weather.args.mode.description",
                ArgTypes.STRING,
                "",
                ""
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
        String weatherToken = FarmEventCommandShared.raw(weatherIdArg.get(commandContext));
        String weatherId = switch (FarmEventCommandShared.normalize(weatherToken)) {
            case "clear", "none", "reset" -> null;
            default -> weatherToken;
        };
        boolean force = switch (FarmEventCommandShared.normalize(modeArg.get(commandContext))) {
            case "force", "reapply", "refresh" -> true;
            default -> false;
        };
        if (force) {
            MghgFarmEventScheduler.clearWeatherStateCache();
        }
        MghgFarmEventScheduler.applyWeatherToFarmWorlds(weatherId);
        commandContext.sendMessage(Message.raw("Weather aplicado a farm worlds: "
                + FarmEventCommandShared.fallback(weatherId, "clear")
                + (force ? " (forced reapply)" : "")));
    }
}
