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
import javax.annotation.Nonnull;

public class FarmAdminParcelReloadSubCommand extends AbstractPlayerCommand {
    public FarmAdminParcelReloadSubCommand() {
        super("reload", "magichygarden.command.farm.admin.parcel.reload.description");
    }

    @Override
    protected void execute(@Nonnull CommandContext commandContext,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        handleParcelReload(commandContext);
    }

    private static void handleParcelReload(@Nonnull CommandContext ctx) {
        MghgParcelManager.load();
        ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("Parcel store recargado desde disco."));
    }
}
