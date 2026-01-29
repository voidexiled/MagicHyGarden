package com.voidexiled.magichygarden.commands.crop;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.voidexiled.magichygarden.commands.crop.subcommands.debug.CropDebugCommand;
import com.voidexiled.magichygarden.commands.crop.subcommands.grow.CropGrowCommand;

public class CropCommand extends AbstractCommandCollection {
    public CropCommand() {
        super("crop", "magichygarden.command.crop.description");

        addSubCommand(new CropGrowCommand());
        addSubCommand(new CropDebugCommand());
    }

}
