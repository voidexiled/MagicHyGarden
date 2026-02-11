package com.voidexiled.magichygarden.commands.farm.subcommands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.DefaultArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcel;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcelAccess;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcelBounds;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcelManager;
import com.voidexiled.magichygarden.features.farming.worlds.MghgFarmWorldManager;
import org.jspecify.annotations.NonNull;

import java.util.Locale;
import java.util.UUID;

public class FarmSpawnCommand extends AbstractPlayerCommand {
    private final DefaultArg<String> actionArg;

    public FarmSpawnCommand() {
        super("spawn", "magichygarden.command.farm.spawn.description");
        this.actionArg = withDefaultArg(
                "action",
                "magichygarden.command.farm.spawn.args.action.description",
                ArgTypes.STRING,
                "status",
                "status"
        );
    }

    @Override
    protected void execute(
            @NonNull CommandContext ctx,
            @NonNull Store<EntityStore> store,
            @NonNull Ref<EntityStore> playerEntityRef,
            @NonNull PlayerRef playerRef,
            @NonNull World world
    ) {
        String action = normalize(actionArg.get(ctx));
        switch (action) {
            case "status", "info" -> showStatus(ctx, playerRef);
            case "set" -> setSpawn(ctx, playerRef, world);
            case "reset" -> resetSpawn(ctx, playerRef);
            default -> ctx.sendMessage(Message.raw("Uso: /farm spawn [status|set|reset]"));
        }
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

    private static void setSpawn(@NonNull CommandContext ctx, @NonNull PlayerRef playerRef, @NonNull World world) {
        UUID owner = MghgFarmWorldManager.getOwnerFromFarmWorld(world);
        if (owner == null || !owner.equals(playerRef.getUuid())) {
            ctx.sendMessage(Message.raw("Debes estar dentro de tu propia granja para usar /farm spawn set."));
            return;
        }
        MghgParcel parcel = MghgParcelManager.getByOwner(owner);
        if (parcel == null || parcel.getBounds() == null) {
            ctx.sendMessage(Message.raw("No se encontro tu parcela."));
            return;
        }

        Transform transform = playerRef.getTransform();
        Vector3d position = transform.getPosition();
        int x = (int) Math.floor(position.x);
        int y = (int) Math.floor(position.y);
        int z = (int) Math.floor(position.z);
        Vector3i pos = new Vector3i(x, y, z);

        if (!MghgParcelAccess.isInside(parcel.getBounds(), pos)) {
            ctx.sendMessage(Message.raw("Debes estar dentro de tu parcela para fijar el spawn."));
            return;
        }

        Transform safeSpawn = MghgFarmWorldManager.resolveSafeSurfaceSpawn(
                world,
                new Transform(new Vector3d(x, y, z), transform.getRotation())
        );
        int safeX = (int) Math.floor(safeSpawn.getPosition().x);
        int safeY = (int) Math.floor(safeSpawn.getPosition().y);
        int safeZ = (int) Math.floor(safeSpawn.getPosition().z);
        parcel.setCustomSpawn(safeX, safeY, safeZ);
        MghgParcelManager.saveSoon();
        ctx.sendMessage(Message.raw(String.format(
                Locale.ROOT,
                "Spawn de granja actualizado a %d, %d, %d.",
                safeX, safeY, safeZ
        )));
    }

    private static void resetSpawn(@NonNull CommandContext ctx, @NonNull PlayerRef playerRef) {
        MghgParcel parcel = MghgParcelManager.getByOwner(playerRef.getUuid());
        if (parcel == null) {
            ctx.sendMessage(Message.raw("No tienes parcela aun. Usa /farm home primero."));
            return;
        }
        parcel.clearCustomSpawn();
        MghgParcelManager.saveSoon();
        ctx.sendMessage(Message.raw("Spawn custom eliminado. /farm home usara el centro de tu parcela."));
    }

    private static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().toLowerCase(Locale.ROOT);
    }
}
