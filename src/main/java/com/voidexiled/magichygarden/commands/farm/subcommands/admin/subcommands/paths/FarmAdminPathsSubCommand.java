package com.voidexiled.magichygarden.commands.farm.subcommands.admin.subcommands.paths;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.voidexiled.magichygarden.features.farming.economy.MghgEconomyManager;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcelInviteService;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcelManager;
import com.voidexiled.magichygarden.features.farming.shop.MghgShopStockManager;
import com.voidexiled.magichygarden.features.farming.storage.MghgStoragePaths;
import com.voidexiled.magichygarden.features.farming.worlds.MghgFarmWorldManager;
import org.jspecify.annotations.NonNull;

import java.nio.file.Path;


public class FarmAdminPathsSubCommand extends AbstractPlayerCommand {
    public FarmAdminPathsSubCommand() {
        super("paths", "magichygarden.command.farm.admin.paths.description");
    }

    @Override
    protected void execute(@NonNull CommandContext commandContext,
                           @NonNull Store<EntityStore> store,
                           @NonNull Ref<EntityStore> ref,
                           @NonNull PlayerRef playerRef,
                           @NonNull World world) {
        sendPaths(commandContext);
    }

    private static void sendPaths(@NonNull CommandContext ctx) {
        Path root = MghgStoragePaths.dataRoot();
        Path parcelDir = MghgParcelManager.getStoreDirectory();
        Path invitePath = MghgParcelInviteService.getStorePath();
        Path ecoPath = MghgEconomyManager.getStorePath();
        Path shopPath = MghgShopStockManager.getStorePath();
        Path backupRoot = MghgFarmWorldManager.getBackupRootPath();
        ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("Data root: " + root));
        ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("Parcels dir: " + parcelDir));
        ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("Parcel invites file: " + invitePath));
        ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("World backups dir: " + backupRoot));
        ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("Economy file: " + ecoPath));
        ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("Shop stock file: " + shopPath));
    }
}
