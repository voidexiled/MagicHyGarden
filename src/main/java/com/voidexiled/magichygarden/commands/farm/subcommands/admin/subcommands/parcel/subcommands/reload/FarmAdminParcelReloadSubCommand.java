package com.voidexiled.magichygarden.commands.farm.subcommands.admin.subcommands.parcel.subcommands.reload;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcelManager;
import org.jspecify.annotations.NonNull;

public class FarmAdminParcelReloadSubCommand extends AbstractPlayerCommand {
    public FarmAdminParcelReloadSubCommand() {
        super("reload", "magichygarden.command.farm.admin.parcel.reload.description");
    }

    @Override
    protected void execute(@NonNull CommandContext commandContext,
                           @NonNull Store<EntityStore> store,
                           @NonNull Ref<EntityStore> ref,
                           @NonNull PlayerRef playerRef,
                           @NonNull World world) {
        handleParcelReload(commandContext);
    }

    private static void handleParcelReload(@NonNull CommandContext ctx) {
        MghgParcelManager.load();
        ctx.sendMessage(Message.raw("Parcel store recargado desde disco."));
    }
}
