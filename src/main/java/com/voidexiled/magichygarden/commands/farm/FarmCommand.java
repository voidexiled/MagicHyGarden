package com.voidexiled.magichygarden.commands.farm;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.voidexiled.magichygarden.commands.farm.subcommands.balance.FarmBalanceCommand;
import com.voidexiled.magichygarden.commands.farm.subcommands.buy.FarmBuyCommand;
import com.voidexiled.magichygarden.commands.farm.subcommands.buymax.FarmBuyMaxCommand;
import com.voidexiled.magichygarden.commands.farm.subcommands.admin.FarmAdminCommandCollection;
import com.voidexiled.magichygarden.commands.farm.subcommands.accept.FarmAcceptSubCommand;
import com.voidexiled.magichygarden.commands.farm.subcommands.event.FarmEventCommand;
import com.voidexiled.magichygarden.commands.farm.subcommands.deny.FarmDenySubCommand;
import com.voidexiled.magichygarden.commands.farm.subcommands.home.FarmHomeSubCommand;
import com.voidexiled.magichygarden.commands.farm.subcommands.farms.FarmFarmsSubCommand;
import com.voidexiled.magichygarden.commands.farm.subcommands.FarmSetSpawnCommand;
import com.voidexiled.magichygarden.commands.farm.subcommands.help.FarmHelpCommand;
import com.voidexiled.magichygarden.commands.farm.subcommands.invite.FarmInviteSubCommand;
import com.voidexiled.magichygarden.commands.farm.subcommands.invites.FarmInvitesSubCommand;
import com.voidexiled.magichygarden.commands.farm.subcommands.kick.FarmKickCommand;
import com.voidexiled.magichygarden.commands.farm.subcommands.leaderboard.FarmLeaderboardCommand;
import com.voidexiled.magichygarden.commands.farm.subcommands.leave.FarmLeaveCommand;
import com.voidexiled.magichygarden.commands.farm.subcommands.log.FarmLogCommand;
import com.voidexiled.magichygarden.commands.farm.subcommands.lobby.FarmLobbyCommand;
import com.voidexiled.magichygarden.commands.farm.subcommands.members.FarmMembersCommand;
import com.voidexiled.magichygarden.commands.farm.subcommands.role.FarmRoleCommand;
import com.voidexiled.magichygarden.commands.farm.subcommands.reload.FarmReloadCommand;
import com.voidexiled.magichygarden.commands.farm.subcommands.sell.FarmSellCommand;
import com.voidexiled.magichygarden.commands.farm.subcommands.sellall.FarmSellAllCommand;
import com.voidexiled.magichygarden.commands.farm.subcommands.shop.FarmShopCommand;
import com.voidexiled.magichygarden.commands.farm.subcommands.stock.FarmStockCommand;
import com.voidexiled.magichygarden.commands.farm.subcommands.perks.FarmPerksCommand;
import com.voidexiled.magichygarden.commands.farm.subcommands.spawn.FarmSpawnCommandCollection;
import com.voidexiled.magichygarden.commands.farm.subcommands.survival.FarmSurvivalSubCommand;
import com.voidexiled.magichygarden.commands.farm.subcommands.visit.FarmVisitSubCommand;

public class FarmCommand extends AbstractCommandCollection {
    public FarmCommand() {
        super("farm", "magichygarden.command.farm.description");

        addSubCommand(new FarmHelpCommand());
        addSubCommand(new FarmHomeSubCommand());
        addSubCommand(new FarmSpawnCommandCollection());
        addSubCommand(new FarmSetSpawnCommand());

        addSubCommand(new FarmFarmsSubCommand());
        addSubCommand(new FarmLobbyCommand());
        addSubCommand(new FarmSurvivalSubCommand());
        addSubCommand(new FarmVisitSubCommand());

        addSubCommand(new FarmInviteSubCommand());
        addSubCommand(new FarmInvitesSubCommand());
        addSubCommand(new FarmAcceptSubCommand());
        addSubCommand(new FarmDenySubCommand());
        addSubCommand(new FarmMembersCommand());
        addSubCommand(new FarmLeaveCommand());
        addSubCommand(new FarmKickCommand());
        addSubCommand(new FarmRoleCommand());
        addSubCommand(new FarmBalanceCommand());
        addSubCommand(new FarmLogCommand());
        addSubCommand(new FarmLeaderboardCommand());
        addSubCommand(new FarmStockCommand());
        addSubCommand(new FarmPerksCommand());
        addSubCommand(new FarmShopCommand());
        addSubCommand(new FarmBuyCommand());
        addSubCommand(new FarmBuyMaxCommand());
        addSubCommand(new FarmSellCommand());
        addSubCommand(new FarmSellAllCommand());
        addSubCommand(new FarmEventCommand());
        addSubCommand(new FarmReloadCommand());
        addSubCommand(new FarmAdminCommandCollection());
    }
}
