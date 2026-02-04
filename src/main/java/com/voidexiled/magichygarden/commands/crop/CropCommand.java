package com.voidexiled.magichygarden.commands.crop;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.voidexiled.magichygarden.commands.crop.subcommands.add.CropAddCommand;
import com.voidexiled.magichygarden.commands.crop.subcommands.debug.CropDebugCommand;
import com.voidexiled.magichygarden.commands.crop.subcommands.grow.CropGrowCommand;
import com.voidexiled.magichygarden.commands.crop.subcommands.reload.CropReloadCommand;
import com.voidexiled.magichygarden.commands.crop.subcommands.set.CropSetCommand;

public class CropCommand extends AbstractCommandCollection {
    public CropCommand() {
        super("crop", "magichygarden.command.crop.description");

        addSubCommand(new CropAddCommand());
        addSubCommand(new CropGrowCommand());
        addSubCommand(new CropSetCommand());
        addSubCommand(new CropReloadCommand());
        addSubCommand(new CropDebugCommand());
    }

}
