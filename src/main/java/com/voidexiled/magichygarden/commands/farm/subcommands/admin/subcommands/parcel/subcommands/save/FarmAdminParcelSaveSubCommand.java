package com.voidexiled.magichygarden.commands.farm.subcommands.admin.subcommands.parcel.subcommands.save;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcelManager;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

public class FarmAdminParcelSaveSubCommand extends AbstractPlayerCommand {
    public FarmAdminParcelSaveSubCommand() {
        super("save", "magichygarden.command.farm.admin.parcel.save.description");
    }

    @Override
    protected void execute(@NonNull CommandContext commandContext,
                           @NonNull Store<EntityStore> store,
                           @NonNull Ref<EntityStore> ref,
                           @NonNull PlayerRef playerRef,
                           @NonNull World world) {
        handleParcelSave(commandContext);
    }

    private static void handleParcelSave(@NotNull CommandContext ctx) {
        MghgParcelManager.save();
        ctx.sendMessage(Message.raw("Parcel store guardado a disco."));
    }
}
