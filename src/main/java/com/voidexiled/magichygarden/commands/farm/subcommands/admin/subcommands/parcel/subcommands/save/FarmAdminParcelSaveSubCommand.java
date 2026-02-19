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
import javax.annotation.Nonnull;

public class FarmAdminParcelSaveSubCommand extends AbstractPlayerCommand {
    public FarmAdminParcelSaveSubCommand() {
        super("save", "magichygarden.command.farm.admin.parcel.save.description");
    }

    @Override
    protected void execute(@Nonnull CommandContext commandContext,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        handleParcelSave(commandContext);
    }

    private static void handleParcelSave(@NotNull CommandContext ctx) {
        MghgParcelManager.save();
        ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("Parcel store guardado a disco."));
    }
}
