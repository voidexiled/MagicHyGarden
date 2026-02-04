package com.voidexiled.magichygarden.commands.crop.subcommands.debug;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.voidexiled.magichygarden.commands.crop.subcommands.debug.subcommands.CropDebugHeldCommand;
import com.voidexiled.magichygarden.commands.crop.subcommands.debug.subcommands.CropDebugRulesCommand;
import com.voidexiled.magichygarden.commands.crop.subcommands.debug.subcommands.CropDebugTargetCommand;
import com.voidexiled.magichygarden.commands.crop.subcommands.debug.subcommands.CropDebugTestCommand;

public class CropDebugCommand extends AbstractCommandCollection {
    public CropDebugCommand() {
        super("debug", "magichygarden.command.crop.debug.description");

        addSubCommand(new CropDebugTargetCommand());
        addSubCommand(new CropDebugTestCommand());
        addSubCommand(new CropDebugHeldCommand());
        addSubCommand(new CropDebugRulesCommand());
    }
}
