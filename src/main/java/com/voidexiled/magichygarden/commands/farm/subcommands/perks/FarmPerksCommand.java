package com.voidexiled.magichygarden.commands.farm.subcommands.perks;

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
import com.voidexiled.magichygarden.features.farming.economy.MghgEconomyManager;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcel;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcelManager;
import com.voidexiled.magichygarden.features.farming.perks.MghgFarmPerkManager;
import com.voidexiled.magichygarden.features.farming.ui.MghgFarmPerksPage;
import org.jspecify.annotations.NonNull;

import java.util.Locale;

public class FarmPerksCommand extends AbstractPlayerCommand {
    private final DefaultArg<String> actionArg;
    private final DefaultArg<String> perkArg;

    public FarmPerksCommand() {
        super("perks", "magichygarden.command.farm.perks.description");

        this.actionArg = withDefaultArg(
                "action",
                "magichygarden.command.farm.perks.args.action.description",
                ArgTypes.STRING,
                "open",
                "open"
        );
        this.perkArg = withDefaultArg(
                "perkId",
                "magichygarden.command.farm.perks.args.perkId.description",
                ArgTypes.STRING,
                "fertile_soil",
                "fertile_soil"
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
        String action = normalize(actionArg.get(ctx));
        switch (action) {
            case "", "open", "ui" -> openUi(ctx, store, playerEntityRef, playerRef, world);
            case "close", "hide" -> {
                closeUi(store, playerEntityRef);
                ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("Perks UI cerrada."));
            }
            case "status", "show", "info" -> sendStatus(ctx, playerRef);
            case "upgrade", "up", "buy" -> handleUpgrade(ctx, store, playerEntityRef, playerRef);
            default -> ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text(
                    "Usage:\n"
                            + "/farm perks\n"
                            + "/farm perks open\n"
                            + "/farm perks close\n"
                            + "/farm perks status\n"
                            + "/farm perks upgrade [fertile_soil|sell_multiplier]"
            ));
        }
    }

    private void openUi(
            @NonNull CommandContext ctx,
            @NonNull Store<EntityStore> store,
            @NonNull Ref<EntityStore> playerEntityRef,
            @NonNull PlayerRef playerRef,
            @NonNull World world
    ) {
        String error = MghgFarmPerksPage.openForPlayer(store, playerEntityRef, playerRef, world);
        if (error != null) {
            ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text(error));
            return;
        }
        ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("Perks UI abierta. Usa /farm perks close para cerrar."));
    }

    private void closeUi(@NonNull Store<EntityStore> store, @NonNull Ref<EntityStore> playerEntityRef) {
        MghgFarmPerksPage.closeForPlayer(store, playerEntityRef);
    }

    private void sendStatus(@NonNull CommandContext ctx, @NonNull PlayerRef playerRef) {
        MghgParcel parcel = MghgParcelManager.getByOwner(playerRef.getUuid());
        if (parcel == null) {
            ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text(
                    "You do not have a farm parcel yet.\n"
                            + "Use /farm home first."
            ));
            return;
        }

        int level = MghgFarmPerkManager.getFertileSoilLevel(parcel);
        int used = MghgFarmPerkManager.getTrackedFertileCount(parcel);
        int cap = MghgFarmPerkManager.getFertileSoilCap(parcel);
        int sellLevel = MghgFarmPerkManager.getSellMultiplierLevel(parcel);
        double sellMultiplier = MghgFarmPerkManager.getSellMultiplier(parcel);
        double balance = MghgEconomyManager.getBalance(playerRef.getUuid());
        var next = MghgFarmPerkManager.getNextFertileLevel(parcel);
        var nextSell = MghgFarmPerkManager.getNextSellMultiplierLevel(parcel);

        ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("Perks - Fertile Soil"));
        ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("Level: " + level));
        ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("Fertile slots used: " + used + " / " + cap));
        if (next == null) {
            ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("Next upgrade: max level reached."));
        } else {
            ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text(String.format(
                    Locale.ROOT,
                    "Next upgrade: level %d (%d slots) for $%.2f",
                    next.getLevel(),
                    next.getMaxFertileBlocks(),
                    next.getUpgradeCost()
            )));
        }
        ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text(String.format(
                Locale.ROOT,
                "Sell Multiplier: level %d (x%.2f)",
                sellLevel,
                sellMultiplier
        )));
        if (nextSell == null) {
            ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("Next sell upgrade: max level reached."));
        } else {
            ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text(String.format(
                    Locale.ROOT,
                    "Next sell upgrade: level %d (x%.2f) for $%.2f",
                    nextSell.getLevel(),
                    nextSell.getMultiplier(),
                    nextSell.getUpgradeCost()
            )));
        }
        ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text(String.format(Locale.ROOT, "Balance: $%.2f", balance)));
    }

    private void handleUpgrade(
            @NonNull CommandContext ctx,
            @NonNull Store<EntityStore> store,
            @NonNull Ref<EntityStore> playerEntityRef,
            @NonNull PlayerRef playerRef
    ) {
        String perkId = normalize(perkArg.get(ctx));
        if (!isKnownPerk(perkId)) {
            ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("Unknown perk id. Supported: fertile_soil, sell_multiplier"));
            return;
        }

        MghgParcel parcel = MghgParcelManager.getByOwner(playerRef.getUuid());
        if (parcel == null) {
            ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text(
                    "You do not have a farm parcel yet.\n"
                            + "Use /farm home first."
            ));
            return;
        }

        MghgFarmPerkManager.UpgradeResult result = isSellPerk(perkId)
                ? MghgFarmPerkManager.tryUpgradeSellMultiplier(playerRef.getUuid(), parcel)
                : MghgFarmPerkManager.tryUpgradeFertileSoil(playerRef.getUuid(), parcel);

        String perkLabel = isSellPerk(perkId) ? "Sell Multiplier" : "Fertile Soil";

        switch (result.getStatus()) {
            case SUCCESS -> {
                if (isSellPerk(perkId)) {
                    double multiplier = MghgFarmPerkManager.getSellMultiplier(parcel);
                    ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text(String.format(
                            Locale.ROOT,
                            "Upgrade successful (%s).\n"
                                    + "Level %d.\n"
                                    + "Current multiplier: x%.2f.\n"
                                    + "Cost: $%.2f",
                            perkLabel,
                            result.getCurrentLevel(),
                            multiplier,
                            result.getUpgradeCost()
                    )));
                } else {
                    ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text(String.format(
                            Locale.ROOT,
                            "Upgrade successful (%s).\n"
                                    + "Level %d.\n"
                                    + "Cap: %d slots.\n"
                                    + "Cost: $%.2f",
                            perkLabel,
                            result.getCurrentLevel(),
                            result.getCurrentCap(),
                            result.getUpgradeCost()
                    )));
                }
                ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text(String.format(
                        Locale.ROOT,
                        "New balance: $%.2f",
                        result.getBalanceAfter()
                )));
            }
            case MAX_LEVEL -> ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text(perkLabel + " is already at max level."));
            case INSUFFICIENT_FUNDS -> ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text(String.format(
                    Locale.ROOT,
                    "Not enough balance.\nRequired: $%.2f\nCurrent: $%.2f",
                    result.getUpgradeCost(),
                    result.getBalanceAfter()
            )));
            case INVALID_TARGET ->
                    ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("Could not upgrade perk for your parcel."));
        }
        MghgFarmPerksPage.refreshForPlayer(store, playerEntityRef);
    }

    private static boolean isFertilePerk(String perkId) {
        return "fertile_soil".equals(perkId)
                || "fertilesoil".equals(perkId)
                || "soil".equals(perkId)
                || "farmland".equals(perkId);
    }

    private static boolean isSellPerk(String perkId) {
        return "sell_multiplier".equals(perkId)
                || "sellmultiplier".equals(perkId)
                || "sell".equals(perkId)
                || "multiplier".equals(perkId);
    }

    private static boolean isKnownPerk(String perkId) {
        return isFertilePerk(perkId) || isSellPerk(perkId);
    }

    private static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().toLowerCase(Locale.ROOT);
    }
}
