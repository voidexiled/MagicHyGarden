package com.voidexiled.magichygarden.commands.farm.subcommands.admin.subcommands.world;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.voidexiled.magichygarden.commands.farm.subcommands.admin.subcommands.world.subcommands.backup.FarmAdminWorldBackupSubCommand;
import com.voidexiled.magichygarden.commands.farm.subcommands.admin.subcommands.world.subcommands.backupall.FarmAdminWorldBackupAllSubCommand;
import com.voidexiled.magichygarden.commands.farm.subcommands.admin.subcommands.world.subcommands.ensure.FarmAdminWorldEnsureSubCommand;
import com.voidexiled.magichygarden.commands.farm.subcommands.admin.subcommands.world.subcommands.restore.FarmAdminWorldRestoreSubCommand;
import com.voidexiled.magichygarden.commands.farm.subcommands.admin.subcommands.world.subcommands.status.FarmAdminWorldStatusSubCommand;

public class FarmAdminWorldCommandCollection extends AbstractCommandCollection {
    public FarmAdminWorldCommandCollection() {
        super("world", "magichygarden.command.farm.admin.world.description");

        addSubCommand(new FarmAdminWorldStatusSubCommand());
        addSubCommand(new FarmAdminWorldBackupSubCommand());
        addSubCommand(new FarmAdminWorldBackupAllSubCommand());
        addSubCommand(new FarmAdminWorldRestoreSubCommand());
        addSubCommand(new FarmAdminWorldEnsureSubCommand());
    }
}
