package com.voidexiled.magichygarden.commands.farm.subcommands.reload;

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
import com.voidexiled.magichygarden.features.farming.events.MghgFarmEventScheduler;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcelInviteService;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcelManager;
import com.voidexiled.magichygarden.features.farming.perks.MghgFertileSoilReconcileService;
import com.voidexiled.magichygarden.features.farming.perks.MghgFarmPerkManager;
import com.voidexiled.magichygarden.features.farming.shop.MghgShopStockManager;
import com.voidexiled.magichygarden.features.farming.shop.MghgShopUiLogManager;
import com.voidexiled.magichygarden.features.farming.storage.MghgPlayerNameManager;
import com.voidexiled.magichygarden.features.farming.tooltips.MghgDynamicTooltipsManager;
import com.voidexiled.magichygarden.features.farming.worlds.MghgFarmWorldManager;
import javax.annotation.Nonnull;

import java.util.Locale;

public class FarmReloadCommand extends AbstractPlayerCommand {
    private final DefaultArg<String> targetArg;

    public FarmReloadCommand() {
        super("reload", "magichygarden.command.farm.reload.description");
        this.targetArg = withDefaultArg(
                "target",
                "magichygarden.command.farm.reload.args.target.description",
                ArgTypes.STRING,
                "all",
                "all"
        );
    }

    @Override
    protected void execute(
            @Nonnull CommandContext ctx,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> playerEntityRef,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
    ) {
        String target = normalize(targetArg.get(ctx));
        switch (target) {
            case "all" -> reloadAll(ctx);
            case "events" -> {
                MghgFarmEventScheduler.reload();
                ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("Recargado: farm events."));
            }
            case "worlds", "farmworlds", "world" -> {
                MghgFarmWorldManager.load();
                ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("Recargado: farm world config."));
            }
            case "parcels", "parcel" -> {
                MghgParcelManager.load();
                ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("Recargado: parcel store."));
            }
            case "invites", "invite" -> {
                MghgParcelInviteService.load();
                ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("Recargado: farm invites."));
            }
            case "economy", "eco" -> {
                MghgEconomyManager.load();
                ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("Recargado: economy store."));
            }
            case "perks", "perk" -> {
                MghgFarmPerkManager.reload();
                MghgFertileSoilReconcileService.restart();
                ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("Recargado: farm perks config."));
            }
            case "names", "namecache", "players" -> {
                MghgPlayerNameManager.load();
                ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("Recargado: player name cache."));
            }
            case "shop", "stock" -> {
                MghgShopStockManager.reloadFromDisk();
                MghgShopUiLogManager.load();
                MghgDynamicTooltipsManager.tryRegister();
                MghgDynamicTooltipsManager.refreshAllPlayers();
                ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("Recargado: shop config + stock state + ui logs."));
            }
            default -> ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text(
                    "Uso: /farm reload [all|events|worlds|parcels|invites|economy|perks|shop|names]"
            ));
        }
    }

    private static void reloadAll(@Nonnull CommandContext ctx) {
        MghgFarmWorldManager.load();
        MghgParcelManager.load();
        MghgParcelInviteService.load();
        MghgEconomyManager.load();
        MghgFarmPerkManager.reload();
        MghgFertileSoilReconcileService.restart();
        MghgPlayerNameManager.load();
        MghgShopStockManager.reloadFromDisk();
        MghgShopUiLogManager.load();
        MghgDynamicTooltipsManager.tryRegister();
        MghgDynamicTooltipsManager.refreshAllPlayers();
        MghgFarmEventScheduler.reload();
        ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text(
                "Recargado: worlds, parcels, invites, economy, perks, names, shop(stock+ui logs) y events."
        ));
    }

    private static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().toLowerCase(Locale.ROOT);
    }
}
