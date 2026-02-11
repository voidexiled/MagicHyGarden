package com.voidexiled.magichygarden.commands.farm.subcommands;

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
import com.voidexiled.magichygarden.features.farming.shop.MghgShopStockManager;
import com.voidexiled.magichygarden.features.farming.shop.MghgShopUiLogManager;
import com.voidexiled.magichygarden.features.farming.storage.MghgPlayerNameManager;
import com.voidexiled.magichygarden.features.farming.worlds.MghgFarmWorldManager;
import org.jspecify.annotations.NonNull;

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
            @NonNull CommandContext ctx,
            @NonNull Store<EntityStore> store,
            @NonNull Ref<EntityStore> playerEntityRef,
            @NonNull PlayerRef playerRef,
            @NonNull World world
    ) {
        String target = normalize(targetArg.get(ctx));
        switch (target) {
            case "all" -> reloadAll(ctx);
            case "events" -> {
                MghgFarmEventScheduler.reload();
                ctx.sendMessage(Message.raw("Recargado: farm events."));
            }
            case "worlds", "farmworlds", "world" -> {
                MghgFarmWorldManager.load();
                ctx.sendMessage(Message.raw("Recargado: farm world config."));
            }
            case "parcels", "parcel" -> {
                MghgParcelManager.load();
                ctx.sendMessage(Message.raw("Recargado: parcel store."));
            }
            case "invites", "invite" -> {
                MghgParcelInviteService.load();
                ctx.sendMessage(Message.raw("Recargado: farm invites."));
            }
            case "economy", "eco" -> {
                MghgEconomyManager.load();
                ctx.sendMessage(Message.raw("Recargado: economy store."));
            }
            case "names", "namecache", "players" -> {
                MghgPlayerNameManager.load();
                ctx.sendMessage(Message.raw("Recargado: player name cache."));
            }
            case "shop", "stock" -> {
                MghgShopStockManager.reloadFromDisk();
                MghgShopUiLogManager.load();
                ctx.sendMessage(Message.raw("Recargado: shop config + stock state + ui logs."));
            }
            default -> ctx.sendMessage(Message.raw(
                    "Uso: /farm reload [all|events|worlds|parcels|invites|economy|shop|names]"
            ));
        }
    }

    private static void reloadAll(@NonNull CommandContext ctx) {
        MghgFarmWorldManager.load();
        MghgParcelManager.load();
        MghgParcelInviteService.load();
        MghgEconomyManager.load();
        MghgPlayerNameManager.load();
        MghgShopStockManager.reloadFromDisk();
        MghgShopUiLogManager.load();
        MghgFarmEventScheduler.reload();
        ctx.sendMessage(Message.raw(
                "Recargado: worlds, parcels, invites, economy, names, shop(stock+ui logs) y events."
        ));
    }

    private static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().toLowerCase(Locale.ROOT);
    }
}
