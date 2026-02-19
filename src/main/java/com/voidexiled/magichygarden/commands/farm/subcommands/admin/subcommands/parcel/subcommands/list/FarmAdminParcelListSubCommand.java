package com.voidexiled.magichygarden.commands.farm.subcommands.admin.subcommands.parcel.subcommands.list;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.voidexiled.magichygarden.commands.farm.subcommands.admin.shared.FarmAdminCommandShared;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcel;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcelBounds;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcelManager;
import com.voidexiled.magichygarden.features.farming.worlds.MghgFarmWorldManager;
import javax.annotation.Nonnull;

import java.util.Locale;

public class FarmAdminParcelListSubCommand extends AbstractPlayerCommand {
    public FarmAdminParcelListSubCommand() {
        super("list", "magichygarden.command.farm.admin.parcel.list.description");
    }

    @Override
    protected void execute(@Nonnull CommandContext commandContext,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {

        handleParcelList(commandContext);
    }

    private static void handleParcelList(@Nonnull CommandContext ctx) {
        int count = 0;
        for (MghgParcel parcel : MghgParcelManager.all()) {
            if (parcel == null || parcel.getOwner() == null) {
                continue;
            }
            count++;
            String worldName = MghgFarmWorldManager.getFarmWorldName(parcel.getOwner());
            MghgParcelBounds bounds = parcel.getBounds();
            ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text(String.format(
                    Locale.ROOT,
                    "%s | owner=%s | world=%s | spawn=(%d,%d,%d) | origin=(%d,%d,%d)",
                    FarmAdminCommandShared.fallback(parcel.getId() == null ? null : parcel.getId().toString(), "-"),
                    parcel.getOwner(),
                    worldName,
                    parcel.resolveSpawnX(),
                    parcel.resolveSpawnY(),
                    parcel.resolveSpawnZ(),
                    bounds == null ? 0 : bounds.getOriginX(),
                    bounds == null ? 0 : bounds.getOriginY(),
                    bounds == null ? 0 : bounds.getOriginZ()
            )));
            if (count >= 40) {
                ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("Parcel list truncada en 40 entradas."));
                break;
            }
        }
        ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("Total parcels: " + MghgParcelManager.all().size()));
    }
}
