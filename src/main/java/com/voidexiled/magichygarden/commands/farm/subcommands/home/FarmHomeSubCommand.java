package com.voidexiled.magichygarden.commands.farm.subcommands.home;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.voidexiled.magichygarden.commands.farm.FarmTeleportUtil;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcel;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcelBounds;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcelManager;
import com.voidexiled.magichygarden.features.farming.worlds.MghgFarmWorldManager;
import org.jspecify.annotations.NonNull;

public class FarmHomeSubCommand extends AbstractPlayerCommand {
    public FarmHomeSubCommand() {
        super("home", "magichygarden.command.farm.home.description");
    }

    @Override
    protected void execute(
            @NonNull CommandContext ctx,
            @NonNull Store<EntityStore> store,
            @NonNull Ref<EntityStore> playerEntityRef,
            @NonNull PlayerRef playerRef,
            @NonNull World world
    ) {
        MghgFarmWorldManager.ensureFarmWorld(playerRef.getUuid())
                .thenAccept(targetWorld -> {
                    Transform spawn = resolveParcelSpawn(targetWorld, playerRef.getUuid());
                    Teleport tp = Teleport.createForPlayer(
                            targetWorld,
                            spawn.getPosition(),
                            FarmTeleportUtil.sanitizeRotation(spawn.getRotation())
                    );
                    world.execute(() -> {
                        store.putComponent(playerEntityRef, Teleport.getComponentType(), tp);
                        ctx.sendMessage(Message.raw("Teletransportando a tu granja..."));
                    });
                })
                .exceptionally(e -> {
                    world.execute(() -> ctx.sendMessage(Message.raw("No pude crear/abrir tu granja: " + e.getMessage())));
                    return null;
                });
    }

    // TODO redundant function with FarmVisitCommand
    private static Transform resolveParcelSpawn(World world, java.util.UUID uuid) {
        Transform baseSpawn = MghgFarmWorldManager.resolveFarmSpawn(world, uuid);
        MghgParcel parcel = MghgParcelManager.getByOwner(uuid);
        if (parcel == null) {
            int sizeX = MghgFarmWorldManager.getConfiguredParcelSizeX();
            int sizeY = MghgFarmWorldManager.getConfiguredParcelSizeY();
            int sizeZ = MghgFarmWorldManager.getConfiguredParcelSizeZ();
            int originX = (int) Math.floor(baseSpawn.getPosition().x) - (sizeX / 2);
            int originY = ((int) Math.floor(baseSpawn.getPosition().y)) - 1;
            int originZ = (int) Math.floor(baseSpawn.getPosition().z) - (sizeZ / 2);
            parcel = MghgParcelManager.getOrCreate(uuid, originX, originY, originZ);
            parcel.setBounds(new MghgParcelBounds(originX, originY, originZ, sizeX, sizeY, sizeZ));
            MghgParcelManager.save();
        }

        MghgParcelBounds bounds = parcel.getBounds();
        if (bounds == null) {
            return MghgFarmWorldManager.resolveSafeSurfaceSpawn(world, baseSpawn);
        }
        double x = parcel.resolveSpawnX();
        double y = parcel.resolveSpawnY();
        double z = parcel.resolveSpawnZ();
        Transform preferred = FarmTeleportUtil.createTransform(x, y, z, baseSpawn.getRotation());
        return MghgFarmWorldManager.resolveSafeSurfaceSpawn(world, preferred);
    }
}
