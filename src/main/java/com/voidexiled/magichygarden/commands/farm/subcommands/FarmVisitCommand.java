package com.voidexiled.magichygarden.commands.farm.subcommands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.DefaultArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.voidexiled.magichygarden.commands.farm.FarmTeleportUtil;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcel;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcelAccess;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcelBounds;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcelManager;
import com.voidexiled.magichygarden.features.farming.storage.MghgPlayerNameManager;
import com.voidexiled.magichygarden.features.farming.worlds.MghgFarmWorldManager;
import org.jspecify.annotations.NonNull;

import javax.annotation.Nullable;
import java.util.UUID;

public class FarmVisitCommand extends AbstractPlayerCommand {
    private final DefaultArg<String> ownerArg;

    public FarmVisitCommand() {
        super("visit", "magichygarden.command.farm.visit.description");
        this.ownerArg = withDefaultArg(
                "owner",
                "magichygarden.command.farm.visit.args.player.description",
                ArgTypes.STRING,
                "",
                ""
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
        MghgPlayerNameManager.remember(playerRef);
        String rawOwner = normalize(ownerArg.get(ctx));
        if (rawOwner.isBlank()) {
            ctx.sendMessage(Message.raw("Uso: /farm visit <ownerUuid|ownerName>"));
            return;
        }
        UUID targetOwner = resolveOwner(rawOwner);
        if (targetOwner == null) {
            ctx.sendMessage(Message.raw("Owner invalido. Usa UUID o username online/cacheado."));
            return;
        }
        PlayerRef targetRef = Universe.get().getPlayer(targetOwner);
        String targetName = resolveName(targetOwner, targetRef);

        boolean selfVisit = targetOwner.equals(playerRef.getUuid());
        if (!selfVisit) {
            MghgParcel targetParcel = MghgParcelManager.getByOwner(targetOwner);
            if (targetParcel == null) {
                ctx.sendMessage(Message.raw("La granja de ese owner aun no esta inicializada."));
                return;
            }
            if (!MghgParcelAccess.canVisit(targetParcel, playerRef.getUuid())) {
                ctx.sendMessage(Message.raw("No tienes permisos para visitar esa granja."));
                return;
            }
        }

        MghgFarmWorldManager.ensureFarmWorld(targetOwner)
                .thenAccept(targetWorld -> {
                    Transform spawn = resolveParcelSpawn(targetWorld, targetOwner, selfVisit);
                    if (spawn == null) {
                        world.execute(() -> ctx.sendMessage(Message.raw("No pude resolver el spawn de esa granja.")));
                        return;
                    }
                    Teleport tp = Teleport.createForPlayer(
                            targetWorld,
                            spawn.getPosition(),
                            FarmTeleportUtil.sanitizeRotation(spawn.getRotation())
                    );
                    world.execute(() -> {
                        store.putComponent(playerEntityRef, Teleport.getComponentType(), tp);
                        ctx.sendMessage(Message.raw("Visitando granja de " + targetName + "..."));
                    });
                })
                .exceptionally(e -> {
                    world.execute(() -> ctx.sendMessage(Message.raw("No pude abrir esa granja: " + e.getMessage())));
                    return null;
                });
    }

    private static @Nullable UUID resolveOwner(@NonNull String rawOwner) {
        return MghgPlayerNameManager.resolveUuid(rawOwner);
    }

    private static @NonNull String resolveName(@NonNull UUID owner, @Nullable PlayerRef ref) {
        if (ref != null && ref.getUsername() != null && !ref.getUsername().isBlank()) {
            MghgPlayerNameManager.remember(ref);
            return ref.getUsername();
        }
        return MghgPlayerNameManager.resolve(owner);
    }

    private static @NonNull String normalize(@Nullable String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim();
    }

    private static @Nullable Transform resolveParcelSpawn(World world, java.util.UUID uuid, boolean createIfMissing) {
        Transform baseSpawn = MghgFarmWorldManager.resolveFarmSpawn(world, uuid);
        MghgParcel parcel = MghgParcelManager.getByOwner(uuid);
        if (parcel == null && createIfMissing) {
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
        if (parcel == null) {
            return null;
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
