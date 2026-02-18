package com.voidexiled.magichygarden.commands.farm.subcommands.survival;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.spawn.ISpawnProvider;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.voidexiled.magichygarden.commands.farm.FarmTeleportUtil;
import com.voidexiled.magichygarden.features.farming.worlds.MghgFarmWorldManager;
import org.jspecify.annotations.NonNull;

public class FarmSurvivalSubCommand extends AbstractPlayerCommand {
    public FarmSurvivalSubCommand() {
        super("survival", "magichygarden.command.farm.survival.description");
    }

    @Override
    protected void execute(
            @NonNull CommandContext ctx,
            @NonNull Store<EntityStore> store,
            @NonNull Ref<EntityStore> playerEntityRef,
            @NonNull PlayerRef playerRef,
            @NonNull World world
    ) {
        World targetWorld = MghgFarmWorldManager.resolveSurvivalWorld();
        if (targetWorld == null) {
            ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("No pude resolver el mundo survival."));
            return;
        }
        Transform spawn = resolveSpawn(targetWorld, playerRef);
        Teleport tp = Teleport.createForPlayer(
                targetWorld,
                spawn.getPosition(),
                FarmTeleportUtil.sanitizeRotation(spawn.getRotation())
        );
        world.execute(() -> {
            store.putComponent(playerEntityRef, Teleport.getComponentType(), tp);
            ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("Teletransportando a survival..."));
        });
    }

    private static Transform resolveSpawn(World world, PlayerRef playerRef) {
        ISpawnProvider provider = world.getWorldConfig().getSpawnProvider();
        if (provider != null) {
            return provider.getSpawnPoint(world, playerRef.getUuid());
        }
        return FarmTeleportUtil.defaultSpawnTransform();
    }
}
