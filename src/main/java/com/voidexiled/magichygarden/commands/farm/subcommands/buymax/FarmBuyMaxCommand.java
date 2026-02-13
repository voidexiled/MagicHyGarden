package com.voidexiled.magichygarden.commands.farm.subcommands.buymax;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.voidexiled.magichygarden.commands.farm.subcommands.shop.FarmShopCommand;
import com.voidexiled.magichygarden.features.farming.economy.MghgEconomyManager;
import com.voidexiled.magichygarden.features.farming.shop.MghgShopAccessPolicy;
import com.voidexiled.magichygarden.features.farming.shop.MghgShopConfig;
import com.voidexiled.magichygarden.features.farming.shop.MghgShopStockManager;
import com.voidexiled.magichygarden.features.farming.storage.MghgPlayerNameManager;
import org.jspecify.annotations.NonNull;

import java.util.Locale;

public class FarmBuyMaxCommand extends AbstractPlayerCommand {
    private final RequiredArg<String> shopIdArg;

    public FarmBuyMaxCommand() {
        super("buymax", "magichygarden.command.farm.buymax.description");
        this.shopIdArg = withRequiredArg(
                "shopId",
                "magichygarden.command.farm.buymax.args.item.description",
                ArgTypes.STRING
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
        MghgPlayerNameManager.remember(playerRef);
        String accessError = MghgShopAccessPolicy.validateTransactionContext(store, playerEntityRef, playerRef, world);
        if (accessError != null) {
            ctx.sendMessage(Message.raw(accessError));
            return;
        }

        String requested = shopIdArg.get(ctx);
        MghgShopConfig.ShopItem item = MghgShopStockManager.getConfiguredItem(requested);
        if (item == null || item.getId() == null) {
            ctx.sendMessage(Message.raw("shopId no encontrado. Usa /farm stock."));
            return;
        }

        String buyItemId = item.resolveBuyItemId();
        if (buyItemId == null || buyItemId.isBlank()) {
            ctx.sendMessage(Message.raw("Item mal configurado (BuyItemId/Id vacio)."));
            return;
        }
        if (item.getBuyPrice() <= 0.0) {
            ctx.sendMessage(Message.raw("Este item no se puede comprar."));
            return;
        }

        Player player = store.getComponent(playerEntityRef, Player.getComponentType());
        if (player == null) {
            ctx.sendMessage(Message.raw("No pude obtener el componente de jugador."));
            return;
        }
        ItemContainer inventory = player.getInventory().getCombinedStorageFirst();

        int stock = MghgShopStockManager.getPlayerStock(playerRef.getUuid(), item.getId());
        if (stock <= 0) {
            ctx.sendMessage(Message.raw("No tienes stock personal disponible para este item en este ciclo."));
            return;
        }

        double balance = MghgEconomyManager.getBalance(playerRef.getUuid());
        int byBalance = maxAffordable(balance, item.getBuyPrice());
        if (byBalance <= 0) {
            ctx.sendMessage(Message.raw("Balance insuficiente para comprar este item."));
            return;
        }

        int cap = Math.min(stock, byBalance);
        int maxQty = resolveMaxInventoryFit(inventory, buyItemId, cap);
        if (maxQty <= 0) {
            ctx.sendMessage(Message.raw("No tienes espacio suficiente en inventario."));
            return;
        }

        double total = item.getBuyPrice() * maxQty;
        if (!MghgEconomyManager.withdraw(playerRef.getUuid(), total)) {
            ctx.sendMessage(Message.raw("No pude debitar balance."));
            return;
        }
        if (!MghgShopStockManager.consumePlayerStock(playerRef.getUuid(), item.getId(), maxQty)) {
            MghgEconomyManager.deposit(playerRef.getUuid(), total);
            ctx.sendMessage(Message.raw("No pude consumir stock, compra cancelada."));
            return;
        }

        ItemStackTransaction transaction = inventory.addItemStack(new ItemStack(buyItemId, maxQty), true, false, true);
        if (!transaction.succeeded() || !ItemStack.isEmpty(transaction.getRemainder())) {
            MghgEconomyManager.deposit(playerRef.getUuid(), total);
            MghgShopStockManager.releasePlayerStock(playerRef.getUuid(), item.getId(), maxQty);
            ctx.sendMessage(Message.raw("Inventario no valido para la compra, compra revertida."));
            return;
        }

        double newBalance = MghgEconomyManager.getBalance(playerRef.getUuid());
        int newStock = MghgShopStockManager.getPlayerStock(playerRef.getUuid(), item.getId());
        ctx.sendMessage(Message.raw(String.format(
                Locale.ROOT,
                "Compraste MAX %dx %s (shopId=%s) por $%s | balance=$%s | stock personal=%d",
                maxQty,
                buyItemId,
                item.getId(),
                formatMoney(total),
                formatMoney(newBalance),
                newStock
        )));
        FarmShopCommand.refreshHudForPlayer(store, playerEntityRef, playerRef, world);
    }

    private static int resolveMaxInventoryFit(@NonNull ItemContainer inventory, @NonNull String itemId, int cap) {
        if (cap <= 0) {
            return 0;
        }
        if (!inventory.canAddItemStack(new ItemStack(itemId, 1), false, true)) {
            return 0;
        }

        int lo = 1;
        int hi = cap;
        int best = 0;
        while (lo <= hi) {
            int mid = lo + ((hi - lo) / 2);
            if (inventory.canAddItemStack(new ItemStack(itemId, mid), false, true)) {
                best = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return best;
    }

    private static int maxAffordable(double balance, double unitPrice) {
        if (unitPrice <= 0.0 || balance <= 0.0) {
            return 0;
        }
        double raw = Math.floor(balance / unitPrice);
        if (raw <= 0.0) {
            return 0;
        }
        if (raw >= Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) raw;
    }

    private static String formatMoney(double value) {
        return String.format(Locale.ROOT, "%.2f", Math.max(0.0, value));
    }
}
