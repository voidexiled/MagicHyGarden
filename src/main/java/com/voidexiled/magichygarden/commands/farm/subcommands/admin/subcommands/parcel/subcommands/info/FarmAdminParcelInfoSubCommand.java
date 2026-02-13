package com.voidexiled.magichygarden.commands.farm.subcommands.admin.subcommands.parcel.subcommands.info;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.DefaultArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.voidexiled.magichygarden.commands.farm.subcommands.admin.subcommands.parcel.subcommands.info.shared.FarmAdminParcelInfoShared;
import com.voidexiled.magichygarden.commands.farm.subcommands.admin.subcommands.parcel.subcommands.info.usageVariants.FarmAdminParcelInfoOtherVariantCommand;
import com.voidexiled.magichygarden.commands.farm.subcommands.admin.subcommands.parcel.subcommands.reload.FarmAdminParcelReloadSubCommand;
import org.jspecify.annotations.NonNull;

public class FarmAdminParcelInfoSubCommand extends AbstractPlayerCommand {

    public FarmAdminParcelInfoSubCommand() {
        super("info", "magichygarden.command.farm.admin.parcel.info.description");

        addUsageVariant(new FarmAdminParcelInfoOtherVariantCommand());
    }

    @Override
    protected void execute(@NonNull CommandContext commandContext,
                           @NonNull Store<EntityStore> store,
                           @NonNull Ref<EntityStore> ref,
                           @NonNull PlayerRef playerRef,
                           @NonNull World world) {

        FarmAdminParcelInfoShared.handleParcelInfo(commandContext, playerRef);
    }
}
