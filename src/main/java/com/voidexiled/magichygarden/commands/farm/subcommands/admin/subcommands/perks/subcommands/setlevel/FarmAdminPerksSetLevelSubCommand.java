package com.voidexiled.magichygarden.commands.farm.subcommands.admin.subcommands.perks.subcommands.setlevel;

import com.hypixel.hytale.codec.validation.Validators;
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
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcel;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcelManager;
import com.voidexiled.magichygarden.features.farming.perks.MghgFarmPerkManager;
import com.voidexiled.magichygarden.features.farming.storage.MghgPlayerNameManager;
import org.jspecify.annotations.NonNull;

import java.util.UUID;

public class FarmAdminPerksSetLevelSubCommand extends AbstractPlayerCommand {
    private final RequiredArg<String> targetArg;
    private final RequiredArg<Integer> levelArg;

    public FarmAdminPerksSetLevelSubCommand() {
        super("setlevel", "magichygarden.command.farm.admin.perks.setlevel.description");

        this.targetArg = withRequiredArg(
                "target",
                "magichygarden.command.farm.admin.perks.args.target.description",
                ArgTypes.STRING
        );
        this.levelArg = withRequiredArg(
                "level",
                "magichygarden.command.farm.admin.perks.setlevel.args.level.description",
                ArgTypes.INTEGER
        ).addValidator(Validators.greaterThan(0));
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
            commandContext.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("Invalid target. Use self, UUID or cached/online name."));
            return;
        }

        MghgParcel parcel = MghgParcelManager.getByOwner(targetUuid);
        if (parcel == null) {
            commandContext.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("Target does not have a parcel yet."));
            return;
        }

        int requested = levelArg.get(commandContext);
        int applied = MghgFarmPerkManager.setFertileSoilLevel(parcel, requested);
        int used = MghgFarmPerkManager.getTrackedFertileCount(parcel);
        int cap = MghgFarmPerkManager.getFertileSoilCap(parcel);
        String targetName = MghgPlayerNameManager.resolve(targetUuid);

        commandContext.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text(
                "Updated fertile perk level for " + targetName + ".\n"
                        + "Level: " + applied + ".\n"
                        + "Tracked fertile blocks: " + used + " / " + cap + "."
        ));
    }
}
