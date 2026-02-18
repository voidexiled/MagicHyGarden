package com.voidexiled.magichygarden.commands.farm.subcommands.admin;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.voidexiled.magichygarden.commands.farm.subcommands.admin.subcommands.economy.FarmAdminEconomyCommandCollection;
import com.voidexiled.magichygarden.commands.farm.subcommands.admin.subcommands.parcel.FarmAdminParcelCommandCollection;
import com.voidexiled.magichygarden.commands.farm.subcommands.admin.subcommands.perks.FarmAdminPerksCommandCollection;
import com.voidexiled.magichygarden.commands.farm.subcommands.admin.subcommands.paths.FarmAdminPathsSubCommand;
import com.voidexiled.magichygarden.commands.farm.subcommands.admin.subcommands.status.FarmAdminStatusSubCommand;
import com.voidexiled.magichygarden.commands.farm.subcommands.admin.subcommands.stock.FarmAdminStockCommandCollection;
import com.voidexiled.magichygarden.commands.farm.subcommands.admin.subcommands.world.FarmAdminWorldCommandCollection;

public class FarmAdminCommandCollection extends AbstractCommandCollection {

    public FarmAdminCommandCollection() {
        super("admin", "magichygarden.command.farm.admin.description");

        addSubCommand(new FarmAdminEconomyCommandCollection());
        addSubCommand(new FarmAdminParcelCommandCollection());
        addSubCommand(new FarmAdminPerksCommandCollection());
        addSubCommand(new FarmAdminPathsSubCommand());
        addSubCommand(new FarmAdminStatusSubCommand());
        addSubCommand(new FarmAdminStockCommandCollection());
        addSubCommand(new FarmAdminWorldCommandCollection());
    }
}
