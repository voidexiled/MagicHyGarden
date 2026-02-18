package com.voidexiled.magichygarden.commands.farm.subcommands.admin.subcommands.economy;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.voidexiled.magichygarden.commands.farm.subcommands.admin.subcommands.economy.subcommands.FarmAdminEconomyAddSubCommand;
import com.voidexiled.magichygarden.commands.farm.subcommands.admin.subcommands.economy.subcommands.FarmAdminEconomySetSubCommand;
import com.voidexiled.magichygarden.commands.farm.subcommands.admin.subcommands.economy.subcommands.FarmAdminEconomySubtractSubCommand;

public class FarmAdminEconomyCommandCollection extends AbstractCommandCollection {
    public FarmAdminEconomyCommandCollection() {
        super("economy", "magichygarden.command.farm.admin.economy.description");

        addSubCommand(new FarmAdminEconomySetSubCommand());
        addSubCommand(new FarmAdminEconomyAddSubCommand());
        addSubCommand(new FarmAdminEconomySubtractSubCommand());
    }
}
