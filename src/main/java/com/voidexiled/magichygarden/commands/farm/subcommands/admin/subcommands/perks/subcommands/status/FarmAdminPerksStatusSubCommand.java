package com.voidexiled.magichygarden.commands.farm.subcommands.admin.subcommands.perks.subcommands.status;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.DefaultArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.voidexiled.magichygarden.commands.farm.subcommands.admin.shared.FarmAdminCommandShared;
import com.voidexiled.magichygarden.features.farming.economy.MghgEconomyManager;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcel;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcelManager;
import com.voidexiled.magichygarden.features.farming.perks.MghgFarmPerkManager;
import com.voidexiled.magichygarden.features.farming.storage.MghgPlayerNameManager;
import org.jspecify.annotations.NonNull;

import java.util.Locale;
import java.util.UUID;

public class FarmAdminPerksStatusSubCommand extends AbstractPlayerCommand {
    private final DefaultArg<String> targetArg;

    public FarmAdminPerksStatusSubCommand() {
        super("status", "magichygarden.command.farm.admin.perks.status.description");

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
            @NonNull CommandContext commandContext,
            @NonNull Store<EntityStore> store,
            @NonNull Ref<EntityStore> ref,
            @NonNull PlayerRef executor,
            @NonNull World world
    ) {
        UUID targetUuid = FarmAdminCommandShared.resolveUuid(executor, targetArg.get(commandContext));
        if (targetUuid == null) {
            commandContext.sendMessage(Message.raw("Invalid target. Use self, UUID or cached/online name."));
            return;
        }

        MghgParcel parcel = MghgParcelManager.getByOwner(targetUuid);
        if (parcel == null) {
            commandContext.sendMessage(Message.raw("Target does not have a parcel yet."));
            return;
        }

        int level = MghgFarmPerkManager.getFertileSoilLevel(parcel);
        int used = MghgFarmPerkManager.getTrackedFertileCount(parcel);
        int cap = MghgFarmPerkManager.getFertileSoilCap(parcel);
        var next = MghgFarmPerkManager.getNextFertileLevel(parcel);
        double balance = MghgEconomyManager.getBalance(targetUuid);
        String targetName = MghgPlayerNameManager.resolve(targetUuid);

        commandContext.sendMessage(Message.raw("Admin Perks - " + targetName));
        commandContext.sendMessage(Message.raw("Fertile Soil level: " + level));
        commandContext.sendMessage(Message.raw("Tracked fertile blocks: " + used + " / " + cap));
        if (next == null) {
            commandContext.sendMessage(Message.raw("Next upgrade: max level reached."));
        } else {
            commandContext.sendMessage(Message.raw(String.format(
                    Locale.ROOT,
                    "Next upgrade: level %d (%d slots) for $%.2f",
                    next.getLevel(),
                    next.getMaxFertileBlocks(),
                    next.getUpgradeCost()
            )));
        }
        commandContext.sendMessage(Message.raw(String.format(Locale.ROOT, "Balance: $%.2f", balance)));
    }
}
