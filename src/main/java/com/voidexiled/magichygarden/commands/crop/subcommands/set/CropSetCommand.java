package com.voidexiled.magichygarden.commands.crop.subcommands.set;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.voidexiled.magichygarden.commands.crop.subcommands.set.subcommands.CropSetSizeCommand;

public class CropSetCommand extends AbstractCommandCollection {
    public CropSetCommand() {
        super("set", "magichygarden.command.crop.set.description");

        addSubCommand(new CropSetSizeCommand());
    }
}
