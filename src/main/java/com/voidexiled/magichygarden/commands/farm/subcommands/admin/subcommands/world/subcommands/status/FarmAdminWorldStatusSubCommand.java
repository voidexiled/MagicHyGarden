package com.voidexiled.magichygarden.commands.farm.subcommands.admin.subcommands.world.subcommands.status;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncPlayerCommand;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.voidexiled.magichygarden.commands.farm.subcommands.admin.shared.FarmAdminCommandShared;
import com.voidexiled.magichygarden.features.farming.worlds.MghgFarmWorldManager;
import org.jspecify.annotations.NonNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class FarmAdminWorldStatusSubCommand extends AbstractPlayerCommand {
    public FarmAdminWorldStatusSubCommand() {
        super("status", "magichygarden.command.farm.admin.world.status.description");

        addAliases("list", "ls", "info");
    }

    @Override
    protected void execute(@NonNull CommandContext commandContext,
                           @NonNull Store<EntityStore> store,
                           @NonNull Ref<EntityStore> ref,
                           @NonNull PlayerRef playerRef,
                           @NonNull World world) {
        sendWorldStatus(commandContext);
    }

    private static void sendWorldStatus(@NonNull CommandContext ctx) {
        Universe universe = Universe.get();
        if (universe == null) {
            ctx.sendMessage(Message.raw("Universe no disponible."));
            return;
        }
        int count = 0;
        for (World candidate : universe.getWorlds().values()) {
            if (!MghgFarmWorldManager.isFarmWorld(candidate)) {
                continue;
            }
            count++;
            UUID owner = MghgFarmWorldManager.getOwnerFromFarmWorld(candidate);
            Path backupPath = MghgFarmWorldManager.getBackupWorldPath(owner);
            boolean backupExists = Files.isDirectory(backupPath);
            ctx.sendMessage(Message.raw(String.format(
                    Locale.ROOT,
                    "%s \n owner=%s \n players=%d |\n ticking=%s | backup=%s",
                    FarmAdminCommandShared.fallback(candidate.getName(), "(unnamed)"),
                    owner == null ? "-" : owner.toString(),
                    candidate.getPlayerRefs().size(),
                    candidate.isTicking(),
                    backupExists
            )));
            if (count >= 60) {
                ctx.sendMessage(Message.raw("World list truncada en 60 entradas."));
                break;
            }
        }
        if (count == 0) {
            ctx.sendMessage(Message.raw("No hay farm worlds activas."));
        }
    }
}


