package com.voidexiled.magichygarden.commands.farm.subcommands.spawn.subcommands.reset;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcel;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcelManager;
import org.jspecify.annotations.NonNull;

public class FarmSpawnResetSubCommand extends AbstractPlayerCommand {
    public FarmSpawnResetSubCommand() {
        super("reset", "magichygarden.command.farm.spawn.reset.description");
    }

    @Override
    protected void execute(@NonNull CommandContext commandContext,
                           @NonNull Store<EntityStore> store,
                           @NonNull Ref<EntityStore> ref,
                           @NonNull PlayerRef playerRef,
                           @NonNull World world) {
        resetSpawn(commandContext, playerRef);
    }

    private static void resetSpawn(@NonNull CommandContext ctx, @NonNull PlayerRef playerRef) {
        MghgParcel parcel = MghgParcelManager.getByOwner(playerRef.getUuid());
        if (parcel == null) {
            ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("No tienes parcela aun. Usa /farm home primero."));
            return;
        }
        parcel.clearCustomSpawn();
        MghgParcelManager.saveSoon();
        ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("Spawn custom eliminado. /farm home usara el centro de tu parcela."));
    }
}
