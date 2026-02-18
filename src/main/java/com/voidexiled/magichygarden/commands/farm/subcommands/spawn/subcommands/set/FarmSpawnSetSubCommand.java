package com.voidexiled.magichygarden.commands.farm.subcommands.spawn.subcommands.set;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcel;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcelManager;
import com.voidexiled.magichygarden.features.farming.worlds.MghgFarmWorldManager;
import org.jspecify.annotations.NonNull;

import java.util.Locale;
import java.util.UUID;

public class FarmSpawnSetSubCommand extends AbstractPlayerCommand {
    public FarmSpawnSetSubCommand() {
        super("set", "magichygarden.command.farm.spawn.set.description");
    }

    @Override
    protected void execute(@NonNull CommandContext commandContext,
                           @NonNull Store<EntityStore> store,
                           @NonNull Ref<EntityStore> ref,
                           @NonNull PlayerRef playerRef,
                           @NonNull World world) {
        setSpawn(commandContext, playerRef, world);
    }

    public static void setSpawn(@NonNull CommandContext ctx, @NonNull PlayerRef playerRef, @NonNull World world) {
        UUID owner = MghgFarmWorldManager.getOwnerFromFarmWorld(world);
        if (owner == null || !owner.equals(playerRef.getUuid())) {
            ctx.sendMessage(Message.raw("Debes estar dentro de tu propia granja para usar /farm spawn set."));
            return;
        }
        MghgParcel parcel = MghgParcelManager.getByOwner(owner);
        if (parcel == null) {
            ctx.sendMessage(Message.raw("No se encontro tu parcela."));
            return;
        }

        Transform transform = playerRef.getTransform();
        Vector3d position = transform.getPosition();
        int x = (int) Math.floor(position.x);
        int y = (int) Math.floor(position.y);
        int z = (int) Math.floor(position.z);

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
}
