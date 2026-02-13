package com.voidexiled.magichygarden.commands.farm.subcommands.admin.subcommands.world.subcommands.ensure;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractTargetPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.voidexiled.magichygarden.commands.farm.subcommands.admin.shared.FarmAdminCommandShared;
import com.voidexiled.magichygarden.features.farming.worlds.MghgFarmWorldManager;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

public class FarmAdminWorldEnsureSubCommand extends AbstractTargetPlayerCommand {

    public FarmAdminWorldEnsureSubCommand() {
        super("ensure", "magichygarden.command.farm.admin.world.ensure.description");

    }
    @Override
    protected void execute(@NonNull CommandContext commandContext,
                           @Nullable Ref<EntityStore> ref,
                           @NonNull Ref<EntityStore> ref1,
                           @NonNull PlayerRef playerRef,
                           @NonNull World world,
                           @NonNull Store<EntityStore> store) {
        ensureWorld(
                commandContext,
                playerRef
        );
    }

    private static void ensureWorld(
            @NonNull CommandContext ctx,
            @NonNull PlayerRef playerRef
    ) {
        UUID owner = playerRef.getUuid();

        try {
            World world = MghgFarmWorldManager.ensureFarmWorld(owner).join();
            ctx.sendMessage(Message.raw("Farm world asegurada: " + FarmAdminCommandShared.fallback(world.getName(), "-")));
        } catch (Exception e) {
            ctx.sendMessage(Message.raw("No pude asegurar farm world: " + e.getMessage()));
        }
    }
}
