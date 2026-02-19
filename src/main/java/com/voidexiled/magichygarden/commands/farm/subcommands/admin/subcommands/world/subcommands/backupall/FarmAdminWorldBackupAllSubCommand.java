package com.voidexiled.magichygarden.commands.farm.subcommands.admin.subcommands.world.subcommands.backupall;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.voidexiled.magichygarden.features.farming.worlds.MghgFarmWorldManager;
import javax.annotation.Nonnull;

public class FarmAdminWorldBackupAllSubCommand extends AbstractPlayerCommand {
    public FarmAdminWorldBackupAllSubCommand() {
        super("backupall", "magichygarden.command.farm.admin.world.backupall.description");

        addAliases("snapshotall", "backup-all", "snapshot-all");
    }

    @Override
    protected void execute(@Nonnull CommandContext commandContext,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        MghgFarmWorldManager.forceSnapshotAll();
        commandContext.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("Snapshot forzado para todas las farm worlds."));
    }
}
