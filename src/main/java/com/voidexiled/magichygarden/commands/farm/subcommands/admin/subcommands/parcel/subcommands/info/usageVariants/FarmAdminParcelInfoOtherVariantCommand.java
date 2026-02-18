package com.voidexiled.magichygarden.commands.farm.subcommands.admin.subcommands.parcel.subcommands.info.usageVariants;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.voidexiled.magichygarden.commands.farm.subcommands.admin.subcommands.parcel.subcommands.info.shared.FarmAdminParcelInfoShared;
import org.jspecify.annotations.NonNull;

public class FarmAdminParcelInfoOtherVariantCommand extends AbstractPlayerCommand {
    private final RequiredArg<PlayerRef> targetPlayerRefArg;

    public FarmAdminParcelInfoOtherVariantCommand() {
        super("magichygarden.command.farm.admin.parcel.info.usageVariants.other.description");

        this.targetPlayerRefArg = withRequiredArg(
                "player",
                "magichygarden.command.farm.admin.parcel.info.usageVariants.other.args.player.description",
                ArgTypes.PLAYER_REF
        );
    }

    @Override
    protected void execute(@NonNull CommandContext commandContext,
                           @NonNull Store<EntityStore> store,
                           @NonNull Ref<EntityStore> ref,
                           @NonNull PlayerRef playerRef,
                           @NonNull World world) {
        PlayerRef targetPlayerRef = commandContext.get(this.targetPlayerRefArg);

        FarmAdminParcelInfoShared.handleParcelInfo(
                commandContext,
                targetPlayerRef
        );

    }
}
