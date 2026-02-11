package com.voidexiled.magichygarden.commands.farm;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.voidexiled.magichygarden.commands.farm.subcommands.FarmBalanceCommand;
import com.voidexiled.magichygarden.commands.farm.subcommands.FarmBuyCommand;
import com.voidexiled.magichygarden.commands.farm.subcommands.FarmBuyMaxCommand;
import com.voidexiled.magichygarden.commands.farm.subcommands.FarmAdminCommand;
import com.voidexiled.magichygarden.commands.farm.subcommands.FarmAcceptCommand;
import com.voidexiled.magichygarden.commands.farm.subcommands.FarmEventCommand;
import com.voidexiled.magichygarden.commands.farm.subcommands.FarmDenyCommand;
import com.voidexiled.magichygarden.commands.farm.subcommands.FarmHomeCommand;
import com.voidexiled.magichygarden.commands.farm.subcommands.FarmFarmsCommand;
import com.voidexiled.magichygarden.commands.farm.subcommands.FarmInviteCommand;
import com.voidexiled.magichygarden.commands.farm.subcommands.FarmInvitesCommand;
import com.voidexiled.magichygarden.commands.farm.subcommands.FarmKickCommand;
import com.voidexiled.magichygarden.commands.farm.subcommands.FarmLeaderboardCommand;
import com.voidexiled.magichygarden.commands.farm.subcommands.FarmLeaveCommand;
import com.voidexiled.magichygarden.commands.farm.subcommands.FarmLogCommand;
import com.voidexiled.magichygarden.commands.farm.subcommands.FarmLobbyCommand;
import com.voidexiled.magichygarden.commands.farm.subcommands.FarmMembersCommand;
import com.voidexiled.magichygarden.commands.farm.subcommands.FarmRoleCommand;
import com.voidexiled.magichygarden.commands.farm.subcommands.FarmReloadCommand;
import com.voidexiled.magichygarden.commands.farm.subcommands.FarmSellCommand;
import com.voidexiled.magichygarden.commands.farm.subcommands.FarmSellAllCommand;
import com.voidexiled.magichygarden.commands.farm.subcommands.FarmShopCommand;
import com.voidexiled.magichygarden.commands.farm.subcommands.FarmStockCommand;
import com.voidexiled.magichygarden.commands.farm.subcommands.FarmSpawnCommand;
import com.voidexiled.magichygarden.commands.farm.subcommands.FarmSurvivalCommand;
import com.voidexiled.magichygarden.commands.farm.subcommands.FarmVisitCommand;

public class FarmCommand extends AbstractCommandCollection {
    public FarmCommand() {
        super("farm", "magichygarden.command.farm.description");

        addSubCommand(new FarmHomeCommand());
        addSubCommand(new FarmFarmsCommand());
        addSubCommand(new FarmLobbyCommand());
        addSubCommand(new FarmSurvivalCommand());
        addSubCommand(new FarmVisitCommand());
        addSubCommand(new FarmSpawnCommand());
        addSubCommand(new FarmInviteCommand());
        addSubCommand(new FarmInvitesCommand());
        addSubCommand(new FarmAcceptCommand());
        addSubCommand(new FarmDenyCommand());
        addSubCommand(new FarmMembersCommand());
        addSubCommand(new FarmLeaveCommand());
        addSubCommand(new FarmKickCommand());
        addSubCommand(new FarmRoleCommand());
        addSubCommand(new FarmBalanceCommand());
        addSubCommand(new FarmLogCommand());
        addSubCommand(new FarmLeaderboardCommand());
        addSubCommand(new FarmStockCommand());
        addSubCommand(new FarmShopCommand());
        addSubCommand(new FarmBuyCommand());
        addSubCommand(new FarmBuyMaxCommand());
        addSubCommand(new FarmSellCommand());
        addSubCommand(new FarmSellAllCommand());
        addSubCommand(new FarmEventCommand());
        addSubCommand(new FarmReloadCommand());
        addSubCommand(new FarmAdminCommand());
    }
}
