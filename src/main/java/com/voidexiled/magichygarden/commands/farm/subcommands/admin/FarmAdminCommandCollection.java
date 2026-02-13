package com.voidexiled.magichygarden.commands.farm.subcommands.admin;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.DefaultArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.voidexiled.magichygarden.commands.farm.subcommands.admin.subcommands.economy.FarmAdminEconomyCommandCollection;
import com.voidexiled.magichygarden.commands.farm.subcommands.admin.subcommands.parcel.FarmAdminParcelCommandCollection;
import com.voidexiled.magichygarden.commands.farm.subcommands.admin.subcommands.paths.FarmAdminPathsSubCommand;
import com.voidexiled.magichygarden.commands.farm.subcommands.admin.subcommands.status.FarmAdminStatusSubCommand;
import com.voidexiled.magichygarden.commands.farm.subcommands.admin.subcommands.stock.FarmAdminStockCommandCollection;
import com.voidexiled.magichygarden.commands.farm.subcommands.admin.subcommands.world.FarmAdminWorldCommandCollection;
import com.voidexiled.magichygarden.features.farming.economy.MghgEconomyManager;
import com.voidexiled.magichygarden.features.farming.events.MghgFarmEventScheduler;
import com.voidexiled.magichygarden.features.farming.events.MghgGlobalFarmEventState;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcel;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcelBlocks;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcelBounds;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcelInviteService;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcelManager;
import com.voidexiled.magichygarden.features.farming.shop.MghgShopConfig;
import com.voidexiled.magichygarden.features.farming.shop.MghgShopStockManager;
import com.voidexiled.magichygarden.features.farming.storage.MghgStoragePaths;
import com.voidexiled.magichygarden.features.farming.worlds.MghgFarmWorldManager;
import org.jspecify.annotations.NonNull;

import javax.annotation.Nullable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

public class FarmAdminCommandCollection extends AbstractCommandCollection {

    public FarmAdminCommandCollection() {
        super("admin", "magichygarden.command.farm.admin.description");

        addSubCommand(new FarmAdminEconomyCommandCollection());
        addSubCommand(new FarmAdminParcelCommandCollection());
        addSubCommand(new FarmAdminPathsSubCommand());
        addSubCommand(new FarmAdminStatusSubCommand());
        addSubCommand(new FarmAdminStockCommandCollection());
        addSubCommand(new FarmAdminWorldCommandCollection());
    }
}
