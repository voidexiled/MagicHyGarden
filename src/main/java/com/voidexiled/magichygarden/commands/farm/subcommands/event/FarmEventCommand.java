package com.voidexiled.magichygarden.commands.farm.subcommands.event;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.voidexiled.magichygarden.commands.farm.subcommands.event.subcommands.FarmEventGrowthSubCommand;
import com.voidexiled.magichygarden.commands.farm.subcommands.event.subcommands.FarmEventListSubCommand;
import com.voidexiled.magichygarden.commands.farm.subcommands.event.subcommands.FarmEventReloadSubCommand;
import com.voidexiled.magichygarden.commands.farm.subcommands.event.subcommands.FarmEventStartSubCommand;
import com.voidexiled.magichygarden.commands.farm.subcommands.event.subcommands.FarmEventStatusSubCommand;
import com.voidexiled.magichygarden.commands.farm.subcommands.event.subcommands.FarmEventStopSubCommand;
import com.voidexiled.magichygarden.commands.farm.subcommands.event.subcommands.FarmEventWeatherSubCommand;
import com.voidexiled.magichygarden.commands.farm.subcommands.event.subcommands.FarmEventWorldsSubCommand;

public class FarmEventCommand extends AbstractCommandCollection {
    public FarmEventCommand() {
        super("event", "magichygarden.command.farm.event.description");

        addSubCommand(new FarmEventStatusSubCommand());
        addSubCommand(new FarmEventReloadSubCommand());
        addSubCommand(new FarmEventWorldsSubCommand());
        addSubCommand(new FarmEventGrowthSubCommand());
        addSubCommand(new FarmEventWeatherSubCommand());
        addSubCommand(new FarmEventListSubCommand());
        addSubCommand(new FarmEventStartSubCommand());
        addSubCommand(new FarmEventStopSubCommand());
    }
}
