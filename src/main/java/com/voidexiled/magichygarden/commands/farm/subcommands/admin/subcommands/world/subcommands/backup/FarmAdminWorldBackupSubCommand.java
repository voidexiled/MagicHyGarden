package com.voidexiled.magichygarden.commands.farm.subcommands.admin.subcommands.world.subcommands.backup;

import com.hypixel.hytale.codec.validation.Validators;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncPlayerCommand;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractTargetPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.voidexiled.magichygarden.features.farming.worlds.MghgFarmWorldManager;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class FarmAdminWorldBackupSubCommand extends AbstractTargetPlayerCommand {

    public FarmAdminWorldBackupSubCommand() {
        super("backup", "magichygarden.command.farm.admin.world.backup.description");

        addAliases("snapshot");
    }

    @Override
    protected void execute(@NonNull CommandContext commandContext,
                           @Nullable Ref<EntityStore> ref,
                           @NonNull Ref<EntityStore> ref1,
                           @NonNull PlayerRef playerRef,
                           @NonNull World world,
                           @NonNull Store<EntityStore> store) {
        forceWorldBackup(commandContext, playerRef);
    }


    private static void forceWorldBackup(
            @NonNull CommandContext ctx,
            @NonNull PlayerRef ownerRef
    ) {

        UUID owner = ownerRef.getUuid();

        boolean ok = MghgFarmWorldManager.forceSnapshotOwner(owner);
        if (!ok) {
            ctx.sendMessage(Message.raw("No pude forzar snapshot (world/universe no disponible)."));
            return;
        }
        ctx.sendMessage(Message.raw("Snapshot forzado para " + owner));
    }
}
