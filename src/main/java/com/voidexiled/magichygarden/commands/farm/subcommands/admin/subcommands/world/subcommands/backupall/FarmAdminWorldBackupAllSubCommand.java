package com.voidexiled.magichygarden.commands.farm.subcommands.admin.subcommands.world.subcommands.backupall;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncPlayerCommand;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.voidexiled.magichygarden.features.farming.worlds.MghgFarmWorldManager;
import org.jspecify.annotations.NonNull;

import java.util.concurrent.CompletableFuture;

public class FarmAdminWorldBackupAllSubCommand extends AbstractPlayerCommand {
    public FarmAdminWorldBackupAllSubCommand() {
        super("backupall", "magichygarden.command.farm.admin.world.backupall.description");

        addAliases("snapshotall", "backup-all", "snapshot-all");
    }

    @Override
    protected void execute(@NonNull CommandContext commandContext,
                           @NonNull Store<EntityStore> store,
                           @NonNull Ref<EntityStore> ref,
                           @NonNull PlayerRef playerRef,
                           @NonNull World world) {
        MghgFarmWorldManager.forceSnapshotAll();
        commandContext.sendMessage(Message.raw("Snapshot forzado para todas las farm worlds."));
    }
}
