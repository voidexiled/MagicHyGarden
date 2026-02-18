package com.voidexiled.magichygarden.commands.farm.subcommands.home;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.HytaleServer;
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

import java.awt.*;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class FarmHomeSubCommand extends AbstractPlayerCommand {
    private static final int TELEPORT_MAX_ATTEMPTS = 8;
    private static final long TELEPORT_RETRY_DELAY_MILLIS = 250L;
    private static final int OPEN_WORLD_MAX_ATTEMPTS = 6;
    private static final long OPEN_WORLD_RETRY_DELAY_MILLIS = 400L;

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
        openAndTeleport(ctx, playerRef, world, 1);
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

    private static void scheduleTeleport(
            @NonNull CommandContext ctx,
            @NonNull PlayerRef playerRef,
            @NonNull World targetWorld,
            @NonNull Transform spawn,
            int attempt
    ) {

        Ref<EntityStore> liveRef = playerRef.getReference();
        if (liveRef == null || !liveRef.isValid()) {
            retryTeleport(ctx, playerRef, targetWorld, spawn, attempt);
            return;
        }
        Store<EntityStore> liveStore = liveRef.getStore();
        if (liveStore == null || liveStore.getExternalData() == null || liveStore.getExternalData().getWorld() == null) {
            retryTeleport(ctx, playerRef, targetWorld, spawn, attempt);
            return;
        }
        World playerWorld = liveStore.getExternalData().getWorld();
        playerWorld.execute(() -> {
            Ref<EntityStore> currentRef = playerRef.getReference();
            if (currentRef == null || !currentRef.isValid()) {
                retryTeleport(ctx, playerRef, targetWorld, spawn, attempt);
                return;
            }
            Store<EntityStore> currentStore = currentRef.getStore();
            Teleport tp = Teleport.createForPlayer(
                    targetWorld,
                    spawn.getPosition(),
                    FarmTeleportUtil.sanitizeRotation(spawn.getRotation())
            );
            currentStore.putComponent(currentRef, Teleport.getComponentType(), tp);
            ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.format(
                    com.voidexiled.magichygarden.utils.chat.MghgChat.Channel.SUCCESS,
                    "Teletransportando a tu granja..."
            ));
        });
    }

    private static void retryTeleport(
            @NonNull CommandContext ctx,
            @NonNull PlayerRef playerRef,
            @NonNull World targetWorld,
            @NonNull Transform spawn,
            int attempt
    ) {
        if (attempt >= TELEPORT_MAX_ATTEMPTS) {
            ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("No pude teletransportarte ahora. Intenta /farm home nuevamente."));
            return;
        }
        HytaleServer.SCHEDULED_EXECUTOR.schedule(
                () -> scheduleTeleport(ctx, playerRef, targetWorld, spawn, attempt + 1),
                TELEPORT_RETRY_DELAY_MILLIS,
                TimeUnit.MILLISECONDS
        );
    }

    private static void openAndTeleport(
            @NonNull CommandContext ctx,
            @NonNull PlayerRef playerRef,
            @NonNull World callbackWorld,
            int attempt
    ) {
        ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.format(
                com.voidexiled.magichygarden.utils.chat.MghgChat.Channel.WARNING,
                "Comando recibido..."
        ));
        MghgFarmWorldManager.ensureFarmWorld(playerRef.getUuid())
                .thenAccept(targetWorld -> {
                    ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.format(
                            com.voidexiled.magichygarden.utils.chat.MghgChat.Channel.WARNING,
                            "Preparando teletransporte a tu granja..."
                    ));
                    Transform spawn = resolveParcelSpawn(targetWorld, playerRef.getUuid());
                    scheduleTeleport(ctx, playerRef, targetWorld, spawn, 1);
                })
                .exceptionally(error -> {
                    Throwable root = unwrap(error);
                    if (attempt < OPEN_WORLD_MAX_ATTEMPTS && isTransientOpenError(root)) {
                        HytaleServer.SCHEDULED_EXECUTOR.schedule(
                                () -> openAndTeleport(ctx, playerRef, callbackWorld, attempt + 1),
                                OPEN_WORLD_RETRY_DELAY_MILLIS,
                                TimeUnit.MILLISECONDS
                        );
                        return null;
                    }
                    String message = root == null || root.getMessage() == null
                            ? String.valueOf(error)
                            : root.getClass().getSimpleName() + ": " + root.getMessage();
                    callbackWorld.execute(() -> ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("No pude crear/abrir tu granja: " + message)));
                    return null;
                });
    }

    private static boolean isTransientOpenError(@NonNull Throwable error) {
        String message = error.getMessage();
        if (error instanceof IllegalMonitorStateException) {
            return true;
        }
        if (message == null || message.isBlank()) {
            return false;
        }
        String normalized = message.toLowerCase();
        return normalized.contains("unlock read lock")
                || normalized.contains("read lock")
                || normalized.contains("store is currently processing")
                || normalized.contains("currently processing")
                || normalized.contains("chunk ticking");
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while (current instanceof CompletionException || current instanceof ExecutionException) {
            Throwable cause = current.getCause();
            if (cause == null) {
                break;
            }
            current = cause;
        }
        return current;
    }
}
