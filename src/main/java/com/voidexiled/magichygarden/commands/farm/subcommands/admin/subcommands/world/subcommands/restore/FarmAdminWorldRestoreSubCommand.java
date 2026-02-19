package com.voidexiled.magichygarden.commands.farm.subcommands.admin.subcommands.world.subcommands.restore;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.voidexiled.magichygarden.commands.farm.subcommands.admin.shared.FarmAdminCommandShared;
import com.voidexiled.magichygarden.features.farming.storage.MghgPlayerNameManager;
import com.voidexiled.magichygarden.features.farming.worlds.MghgFarmWorldManager;
import javax.annotation.Nonnull;

import java.util.UUID;

public class FarmAdminWorldRestoreSubCommand extends AbstractPlayerCommand {
    private final RequiredArg<String> targetArg;

    public FarmAdminWorldRestoreSubCommand() {
        super("restore", "magichygarden.command.farm.admin.world.restore.description");

        this.targetArg = withRequiredArg(
                "target",
                "magichygarden.command.farm.admin.world.args.target.description",
                ArgTypes.STRING
        );
    }

    @Override
    protected void execute(@Nonnull CommandContext commandContext,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef executor,
                           @Nonnull World world) {
        MghgPlayerNameManager.remember(executor);
        String targetToken = targetArg.get(commandContext);
        UUID targetUuid = FarmAdminCommandShared.resolveUuid(executor, targetToken);
        if (targetUuid == null) {
            commandContext.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("Target invalido. Usa self, UUID o username cacheado/online."));
            return;
        }
        forceWorldRestore(commandContext, targetUuid);
    }

    private static void forceWorldRestore(
            @Nonnull CommandContext ctx,
            @Nonnull UUID owner
    ) {
        String targetName = MghgPlayerNameManager.resolve(owner);

        boolean restored = MghgFarmWorldManager.restoreOwnerFromSnapshot(owner);
        ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text(restored
                ? "Restore desde snapshot aplicado para " + targetName + " (" + owner + ")"
                : "No habia snapshot/restauracion para " + targetName + " (" + owner + ")"));
    }
}
