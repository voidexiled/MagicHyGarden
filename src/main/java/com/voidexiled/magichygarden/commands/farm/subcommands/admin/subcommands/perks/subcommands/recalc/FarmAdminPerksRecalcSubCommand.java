package com.voidexiled.magichygarden.commands.farm.subcommands.admin.subcommands.perks.subcommands.recalc;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.DefaultArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.voidexiled.magichygarden.commands.farm.subcommands.admin.shared.FarmAdminCommandShared;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcel;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcelManager;
import com.voidexiled.magichygarden.features.farming.perks.MghgFertileSoilReconcileService;
import com.voidexiled.magichygarden.features.farming.perks.MghgFarmPerkManager;
import com.voidexiled.magichygarden.features.farming.storage.MghgPlayerNameManager;
import com.voidexiled.magichygarden.features.farming.worlds.MghgFarmWorldManager;
import javax.annotation.Nonnull;

import java.util.UUID;

public class FarmAdminPerksRecalcSubCommand extends AbstractPlayerCommand {
    private final DefaultArg<String> targetArg;

    public FarmAdminPerksRecalcSubCommand() {
        super("recalc", "magichygarden.command.farm.admin.perks.recalc.description");

        this.targetArg = withDefaultArg(
                "target",
                "magichygarden.command.farm.admin.perks.args.target.description",
                ArgTypes.STRING,
                "self",
                "self"
        );
    }

    @Override
    protected void execute(
            @Nonnull CommandContext commandContext,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef executor,
            @Nonnull World world
    ) {
        UUID targetUuid = FarmAdminCommandShared.resolveUuid(executor, targetArg.get(commandContext));
        if (targetUuid == null) {
            commandContext.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("Invalid target. Use self, UUID or cached/online name."));
            return;
        }

        MghgParcel parcel = MghgParcelManager.getByOwner(targetUuid);
        if (parcel == null) {
            commandContext.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("Target does not have a parcel yet."));
            return;
        }

        Universe universe = Universe.get();
        String worldName = MghgFarmWorldManager.getFarmWorldName(targetUuid);
        World targetWorld = universe == null ? null : universe.getWorld(worldName);
        if (targetWorld == null) {
            commandContext.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text(
                    "Target farm world is not loaded.\n"
                            + "Run /farm admin world ensure <target> and retry."
            ));
            return;
        }

        int before = MghgFarmPerkManager.getTrackedFertileCount(parcel);
        String targetName = MghgPlayerNameManager.resolve(targetUuid);
        commandContext.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("Reconciling fertile tracking for " + targetName + "..."));

        MghgFertileSoilReconcileService.reconcileOwnerNow(targetUuid)
                .whenComplete((removed, error) -> {
                    if (error != null) {
                        executor.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text(
                                "Fertile reconcile failed: " + error.getMessage()
                        ));
                        return;
                    }
                    int after = MghgFarmPerkManager.getTrackedFertileCount(parcel);
                    executor.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text(
                            "Fertile reconcile completed for " + targetName + ".\n"
                                    + "Removed stale entries: " + removed + ".\n"
                                    + "Tracked fertile blocks: " + after + " (was " + before + ")."
                    ));
                });
    }
}
