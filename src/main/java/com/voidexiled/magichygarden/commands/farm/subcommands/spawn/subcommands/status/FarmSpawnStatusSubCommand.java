package com.voidexiled.magichygarden.commands.farm.subcommands.spawn.subcommands.status;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcel;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcelBounds;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcelManager;
import org.jspecify.annotations.NonNull;

import java.util.Locale;

public class FarmSpawnStatusSubCommand extends AbstractPlayerCommand {
    public FarmSpawnStatusSubCommand() {
        super("status", "magichygarden.command.farm.spawn.status.description");

        addAliases("info");
    }

    @Override
    protected void execute(@NonNull CommandContext commandContext,
                           @NonNull Store<EntityStore> store,
                           @NonNull Ref<EntityStore> ref,
                           @NonNull PlayerRef playerRef,
                           @NonNull World world) {
        showStatus(commandContext, playerRef);
    }

    private static void showStatus(@NonNull CommandContext ctx, @NonNull PlayerRef playerRef) {
        MghgParcel parcel = MghgParcelManager.getByOwner(playerRef.getUuid());
        if (parcel == null) {
            ctx.sendMessage(Message.raw("No tienes parcela aun. Usa /farm home primero."));
            return;
        }
        MghgParcelBounds bounds = parcel.getBounds();
        if (bounds == null) {
            ctx.sendMessage(Message.raw("Tu parcela no tiene bounds validos."));
            return;
        }
        int x = parcel.resolveSpawnX();
        int y = parcel.resolveSpawnY();
        int z = parcel.resolveSpawnZ();
        ctx.sendMessage(Message.raw(String.format(
                Locale.ROOT,
                "Spawn actual: %d, %d, %d | custom=%s",
                x, y, z, parcel.hasCustomSpawn()
        )));
    }
}
