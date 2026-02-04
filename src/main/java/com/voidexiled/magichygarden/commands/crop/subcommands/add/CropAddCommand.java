package com.voidexiled.magichygarden.commands.crop.subcommands.add;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.voidexiled.magichygarden.commands.crop.subcommands.add.subcommands.CropAddLunarCommand;
import com.voidexiled.magichygarden.commands.crop.subcommands.add.subcommands.CropAddMutationCommand;
import com.voidexiled.magichygarden.commands.crop.subcommands.add.subcommands.CropAddRarityCommand;

public class CropAddCommand extends AbstractCommandCollection {
    public CropAddCommand() {
        super("add", "magichygarden.command.crop.add.description");

        addSubCommand(new CropAddMutationCommand());
        addSubCommand(new CropAddLunarCommand());
        addSubCommand(new CropAddRarityCommand());
    }
}
