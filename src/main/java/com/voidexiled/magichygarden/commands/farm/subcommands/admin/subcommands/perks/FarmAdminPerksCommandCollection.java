package com.voidexiled.magichygarden.commands.farm.subcommands.admin.subcommands.perks;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.voidexiled.magichygarden.commands.farm.subcommands.admin.subcommands.perks.subcommands.recalc.FarmAdminPerksRecalcSubCommand;
import com.voidexiled.magichygarden.commands.farm.subcommands.admin.subcommands.perks.subcommands.setlevel.FarmAdminPerksSetLevelSubCommand;
import com.voidexiled.magichygarden.commands.farm.subcommands.admin.subcommands.perks.subcommands.status.FarmAdminPerksStatusSubCommand;

public class FarmAdminPerksCommandCollection extends AbstractCommandCollection {
    public FarmAdminPerksCommandCollection() {
        super("perks", "magichygarden.command.farm.admin.perks.description");

        addSubCommand(new FarmAdminPerksStatusSubCommand());
        addSubCommand(new FarmAdminPerksSetLevelSubCommand());
        addSubCommand(new FarmAdminPerksRecalcSubCommand());
    }
}
