package com.voidexiled.magichygarden.commands.farm.subcommands.admin.subcommands.world.subcommands.restore;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractTargetPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.voidexiled.magichygarden.features.farming.worlds.MghgFarmWorldManager;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

public class FarmAdminWorldRestoreSubCommand extends AbstractTargetPlayerCommand {


    public FarmAdminWorldRestoreSubCommand() {
        super("restore", "magichygarden.command.farm.admin.world.restore.description");
    }
    @Override
    protected void execute(@NonNull CommandContext commandContext,
                           @Nullable Ref<EntityStore> ref,
                           @NonNull Ref<EntityStore> ref1,
                           @NonNull PlayerRef playerRef,
                           @NonNull World world,
                           @NonNull Store<EntityStore> store) {
        forceWorldRestore(commandContext, playerRef);
    }

    private static void forceWorldRestore(
            @NonNull CommandContext ctx,
            @NonNull PlayerRef playerRef
    ) {
        UUID owner = playerRef.getUuid();

        boolean restored = MghgFarmWorldManager.restoreOwnerFromSnapshot(owner);
        ctx.sendMessage(Message.raw(restored
                ? "Restore desde snapshot aplicado para " + owner
                : "No habia snapshot/restauracion para " + owner));
    }

}
