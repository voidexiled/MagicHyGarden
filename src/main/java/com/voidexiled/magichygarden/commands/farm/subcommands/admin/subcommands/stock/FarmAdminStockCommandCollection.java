package com.voidexiled.magichygarden.commands.farm.subcommands.admin.subcommands.stock;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.voidexiled.magichygarden.commands.farm.subcommands.admin.subcommands.stock.subcommands.restock.FarmAdminStockRestockSubCommand;
import com.voidexiled.magichygarden.commands.farm.subcommands.admin.subcommands.stock.subcommands.set.FarmAdminStockSetSubCommand;
import com.voidexiled.magichygarden.commands.farm.subcommands.admin.subcommands.stock.subcommands.status.FarmAdminStockStatusSubCommand;

public class FarmAdminStockCommandCollection extends AbstractCommandCollection {
    public FarmAdminStockCommandCollection() {
        super("stock", "magichygarden.command.farm.admin.stock.description");

        addSubCommand(new FarmAdminStockStatusSubCommand());
        addSubCommand(new FarmAdminStockRestockSubCommand());
        addSubCommand(new FarmAdminStockSetSubCommand());
    }
}
