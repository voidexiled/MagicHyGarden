package com.voidexiled.magichygarden.commands.farm.subcommands.stock;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.voidexiled.magichygarden.features.farming.shop.MghgShopConfig;
import com.voidexiled.magichygarden.features.farming.shop.MghgShopStockManager;
import org.jspecify.annotations.NonNull;

import java.util.Locale;

public class FarmStockCommand extends AbstractPlayerCommand {
    public FarmStockCommand() {
        super("stock", "magichygarden.command.farm.stock.description");
    }

    @Override
    protected void execute(
            @NonNull CommandContext ctx,
            @NonNull Store<EntityStore> store,
            @NonNull Ref<EntityStore> playerEntityRef,
            @NonNull PlayerRef playerRef,
            @NonNull World world
    ) {
        MghgShopConfig.ShopItem[] items = MghgShopStockManager.getConfiguredItems();
        if (items.length == 0) {
            ctx.sendMessage(Message.raw("Shop config vacia."));
            return;
        }

        long remaining = MghgShopStockManager.getRemainingRestockSeconds();
        long minutes = remaining / 60L;
        long seconds = remaining % 60L;
        ctx.sendMessage(Message.raw("Restock in " + minutes + "m " + seconds + "s"));

        for (MghgShopConfig.ShopItem item : items) {
            if (item == null || item.getId() == null || item.getId().isBlank()) continue;
            int personal = MghgShopStockManager.getPlayerStock(playerRef.getUuid(), item.getId());
            int global = MghgShopStockManager.getStock(item.getId());
            String buyItemId = item.resolveBuyItemId();
            String line = String.format(
                    Locale.ROOT,
                    "shopId=%s | stock=%d/%d | buyItem=%s | buy=$%.2f | sellBase=$%.2f | chance=%.2f",
                    item.getId(),
                    personal,
                    global,
                    buyItemId == null ? "-" : buyItemId,
                    item.getBuyPrice(),
                    item.getSellPrice(),
                    normalizeChance(item.getRestockChance())
            );
            ctx.sendMessage(Message.raw(line));
        }
        ctx.sendMessage(Message.raw("Usa /farm shop para vista detallada."));
    }

    private static double normalizeChance(double raw) {
        if (Double.isNaN(raw) || raw <= 0.0) return 0.0;
        return raw > 1.0 ? (raw / 100.0) : raw;
    }
}
