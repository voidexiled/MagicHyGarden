package com.voidexiled.magichygarden.commands.farm.subcommands.spawn;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.voidexiled.magichygarden.commands.farm.subcommands.spawn.subcommands.reset.FarmSpawnResetSubCommand;
import com.voidexiled.magichygarden.commands.farm.subcommands.spawn.subcommands.set.FarmSpawnSetSubCommand;
import com.voidexiled.magichygarden.commands.farm.subcommands.spawn.subcommands.status.FarmSpawnStatusSubCommand;

public class FarmSpawnCommandCollection extends AbstractCommandCollection {

    public FarmSpawnCommandCollection() {
        super("spawn", "magichygarden.command.farm.spawn.description");

        addSubCommand(new FarmSpawnSetSubCommand());
        addSubCommand(new FarmSpawnStatusSubCommand());
        addSubCommand(new FarmSpawnResetSubCommand());
    }

}
