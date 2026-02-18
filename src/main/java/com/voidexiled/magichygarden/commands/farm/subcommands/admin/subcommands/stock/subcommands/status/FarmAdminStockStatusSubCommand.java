package com.voidexiled.magichygarden.commands.farm.subcommands.admin.subcommands.stock.subcommands.status;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.voidexiled.magichygarden.commands.farm.subcommands.admin.shared.FarmAdminCommandShared;
import com.voidexiled.magichygarden.features.farming.shop.MghgShopConfig;
import com.voidexiled.magichygarden.features.farming.shop.MghgShopStockManager;
import org.jspecify.annotations.NonNull;

import java.util.Locale;

public class FarmAdminStockStatusSubCommand extends AbstractPlayerCommand {

    public FarmAdminStockStatusSubCommand() {
        super("status", "magichygarden.command.farm.admin.stock.status.description");
    }

    @Override
    protected void execute(@NonNull CommandContext commandContext,
                           @NonNull Store<EntityStore> store,
                           @NonNull Ref<EntityStore> ref,
                           @NonNull PlayerRef playerRef,
                           @NonNull World world) {
        handleStockStatus(commandContext);
    }

    private void handleStockStatus(@NonNull CommandContext ctx){
        long remaining = MghgShopStockManager.getRemainingRestockSeconds();
        MghgShopConfig.ShopItem[] items = MghgShopStockManager.getConfiguredItems();
        ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text(String.format(
                Locale.ROOT,
                "Stock status | configured=%d | nextRestock=%s",
                items.length,
                FarmAdminCommandShared.formatDuration(remaining)
        )));
        for (MghgShopConfig.ShopItem item : items) {
            if (item == null || item.getId() == null || item.getId().isBlank()) {
                continue;
            }
            int stock = MghgShopStockManager.getStock(item.getId());
            double chance = FarmAdminCommandShared.normalizeChance(item.getRestockChance()) * 100.0d;
            String buyItemId = item.resolveBuyItemId();
            ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text(String.format(
                    Locale.ROOT,
                    " - %s | stockGlobal=%d | buyItem=%s | buy=%.2f | sellBase=%.2f | restockChance=%.2f%%",
                    item.getId(),
                    stock,
                    buyItemId == null ? "-" : buyItemId,
                    item.getBuyPrice(),
                    item.getSellPrice(),
                    chance
            )));
        }
    }
}
