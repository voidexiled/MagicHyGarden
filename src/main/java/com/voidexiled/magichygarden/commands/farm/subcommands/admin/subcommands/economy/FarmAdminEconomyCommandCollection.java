package com.voidexiled.magichygarden.commands.farm.subcommands.admin.subcommands.economy;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.voidexiled.magichygarden.commands.farm.subcommands.admin.subcommands.economy.subcommands.FarmAdminEconomyAddSubCommand;
import com.voidexiled.magichygarden.commands.farm.subcommands.admin.subcommands.economy.subcommands.FarmAdminEconomySetSubCommand;
import com.voidexiled.magichygarden.commands.farm.subcommands.admin.subcommands.economy.subcommands.FarmAdminEconomySubstractSubCommand;
import com.voidexiled.magichygarden.features.farming.economy.MghgEconomyManager;
import org.jspecify.annotations.NonNull;

import javax.annotation.Nullable;
import java.util.Locale;
import java.util.UUID;

public class FarmAdminEconomyCommandCollection extends AbstractCommandCollection {
    public FarmAdminEconomyCommandCollection() {
        super("economy", "magichygarden.command.farm.admin.economy.description");

        addSubCommand(new FarmAdminEconomySetSubCommand());
        addSubCommand(new FarmAdminEconomyAddSubCommand());
        addSubCommand(new FarmAdminEconomySubstractSubCommand());
    }


}
