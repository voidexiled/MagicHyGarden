package com.voidexiled.magichygarden.commands.farm.subcommands.admin.subcommands.parcel.subcommands.info.shared;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcel;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcelBlocks;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcelBounds;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcelManager;
import com.voidexiled.magichygarden.features.farming.worlds.MghgFarmWorldManager;
import org.jspecify.annotations.NonNull;

import java.nio.file.Path;
import java.util.UUID;

public final class FarmAdminParcelInfoShared {
    private FarmAdminParcelInfoShared() {
    }

    public static void handleParcelInfo(@NonNull CommandContext ctx, @NonNull PlayerRef playerRef) {
        Universe universe = Universe.get();
        UUID owner = playerRef.getUuid();

        MghgParcel playerParcel = MghgParcelManager.getByOwner(owner);
        if (playerParcel == null) {
            ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("No pude resolver owner. Asegúrate de que el jugador tenga una parcela."));
            return;
        }

        MghgParcelBounds parcelBounds = playerParcel.getBounds();
        MghgParcelBlocks parcelBlocks = playerParcel.getBlocks();
        int entries = (parcelBlocks == null || parcelBlocks.getEntries() == null) ? 0 : parcelBlocks.getEntries().length;
        int members = playerParcel.getMembers() == null ? 0 : playerParcel.getMembers().length;

        boolean customSpawn = playerParcel.hasCustomSpawn();
        String worldName = MghgFarmWorldManager.getFarmWorldName(owner);
        boolean loaded = universe != null && universe.getWorld(worldName) != null;
        Path filePath = MghgParcelManager.getOwnerFile(owner);

        ctx.sendMessage(
                Message.raw(
                        "Parcel Info:\n" +
                        " - Owner: " + owner + "\n" +
                        " - World: " + worldName + (loaded ? " (Loaded)" : " (Not Loaded)") + "\n" +
                        " - Bounds: " + (parcelBounds != null ? parcelBounds.toString() : "null") + "\n" +
                        " - Blocks: " + entries + "\n" +
                        " - Members: " + members + "\n" +
                        " - Custom Spawn: " + customSpawn + "\n" +
                        " - File Path: " + (filePath != null ? filePath.toString() : "null")
                )
        );
    }
}
