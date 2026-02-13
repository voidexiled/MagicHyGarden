package com.voidexiled.magichygarden.commands.farm.subcommands.admin.subcommands.parcel;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.voidexiled.magichygarden.commands.farm.subcommands.admin.subcommands.parcel.subcommands.info.FarmAdminParcelInfoSubCommand;
import com.voidexiled.magichygarden.commands.farm.subcommands.admin.subcommands.parcel.subcommands.list.FarmAdminParcelListSubCommand;
import com.voidexiled.magichygarden.commands.farm.subcommands.admin.subcommands.parcel.subcommands.reload.FarmAdminParcelReloadSubCommand;
import com.voidexiled.magichygarden.commands.farm.subcommands.admin.subcommands.parcel.subcommands.save.FarmAdminParcelSaveSubCommand;


public class FarmAdminParcelCommandCollection extends AbstractCommandCollection {
    public FarmAdminParcelCommandCollection() {
        super("parcel", "magichygarden.command.farm.admin.parcel.description");

        addSubCommand(new FarmAdminParcelListSubCommand());
        addSubCommand(new FarmAdminParcelSaveSubCommand());
        addSubCommand(new FarmAdminParcelReloadSubCommand());
        addSubCommand(new FarmAdminParcelInfoSubCommand());
    }

}
