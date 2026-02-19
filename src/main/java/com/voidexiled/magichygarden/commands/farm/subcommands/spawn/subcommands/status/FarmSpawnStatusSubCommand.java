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
import javax.annotation.Nonnull;

import java.util.Locale;

public class FarmSpawnStatusSubCommand extends AbstractPlayerCommand {
    public FarmSpawnStatusSubCommand() {
        super("status", "magichygarden.command.farm.spawn.status.description");

        addAliases("info");
    }

    @Override
    protected void execute(@Nonnull CommandContext commandContext,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        showStatus(commandContext, playerRef);
    }

    private static void showStatus(@Nonnull CommandContext ctx, @Nonnull PlayerRef playerRef) {
        MghgParcel parcel = MghgParcelManager.getByOwner(playerRef.getUuid());
        if (parcel == null) {
            ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("No tienes parcela aun. Usa /farm home primero."));
            return;
        }
        MghgParcelBounds bounds = parcel.getBounds();
        if (bounds == null) {
            ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("Tu parcela no tiene bounds validos."));
            return;
        }
        int x = parcel.resolveSpawnX();
        int y = parcel.resolveSpawnY();
        int z = parcel.resolveSpawnZ();
        ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text(String.format(
                Locale.ROOT,
                "Spawn actual: %d, %d, %d | custom=%s",
                x, y, z, parcel.hasCustomSpawn()
        )));
    }
}
