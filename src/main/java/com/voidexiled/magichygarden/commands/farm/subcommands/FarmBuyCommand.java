package com.voidexiled.magichygarden.commands.farm.subcommands;

import com.hypixel.hytale.codec.validation.Validators;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.DefaultArg;
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
import com.voidexiled.magichygarden.features.farming.economy.MghgEconomyManager;
import com.voidexiled.magichygarden.features.farming.shop.MghgShopAccessPolicy;
import com.voidexiled.magichygarden.features.farming.shop.MghgShopConfig;
import com.voidexiled.magichygarden.features.farming.shop.MghgShopStockManager;
import com.voidexiled.magichygarden.features.farming.storage.MghgPlayerNameManager;
import org.jspecify.annotations.NonNull;

import java.util.Locale;

public class FarmBuyCommand extends AbstractPlayerCommand {
    private final RequiredArg<String> shopIdArg;
    private final DefaultArg<Integer> qtyArg;

    public FarmBuyCommand() {
        super("buy", "magichygarden.command.farm.buy.description");
        this.shopIdArg = withRequiredArg(
                "shopId",
                "magichygarden.command.farm.buy.args.item.description",
                ArgTypes.STRING
        );
        this.qtyArg = withDefaultArg(
                "qty",
                "magichygarden.command.farm.buy.args.qty.description",
                ArgTypes.INTEGER,
                1,
                "1"
        ).addValidator(Validators.greaterThanOrEqual(1));
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
        int qty = Math.max(1, qtyArg.get(ctx));

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

        int stock = MghgShopStockManager.getPlayerStock(playerRef.getUuid(), item.getId());
        if (stock < qty) {
            ctx.sendMessage(Message.raw("Stock personal insuficiente: " + stock + " disponible en este ciclo."));
            return;
        }

        double total = item.getBuyPrice() * qty;
        double balance = MghgEconomyManager.getBalance(playerRef.getUuid());
        if (balance < total) {
            ctx.sendMessage(Message.raw("Balance insuficiente. Necesitas $" + formatMoney(total)));
            return;
        }

        Player player = store.getComponent(playerEntityRef, Player.getComponentType());
        if (player == null) {
            ctx.sendMessage(Message.raw("No pude obtener el componente de jugador."));
            return;
        }
        ItemContainer inventory = player.getInventory().getCombinedStorageFirst();
        ItemStack stack = new ItemStack(buyItemId, qty);
        if (!inventory.canAddItemStack(stack, false, true)) {
            ctx.sendMessage(Message.raw("No tienes espacio suficiente en inventario."));
            return;
        }

        if (!MghgEconomyManager.withdraw(playerRef.getUuid(), total)) {
            ctx.sendMessage(Message.raw("No pude debitar balance."));
            return;
        }

        if (!MghgShopStockManager.consumePlayerStock(playerRef.getUuid(), item.getId(), qty)) {
            MghgEconomyManager.deposit(playerRef.getUuid(), total);
            ctx.sendMessage(Message.raw("No pude consumir stock, compra cancelada."));
            return;
        }

        ItemStackTransaction transaction = inventory.addItemStack(stack, true, false, true);
        if (!transaction.succeeded() || !ItemStack.isEmpty(transaction.getRemainder())) {
            MghgEconomyManager.deposit(playerRef.getUuid(), total);
            MghgShopStockManager.releasePlayerStock(playerRef.getUuid(), item.getId(), qty);
            ctx.sendMessage(Message.raw("Inventario no valido para la compra, compra revertida."));
            return;
        }

        double newBalance = MghgEconomyManager.getBalance(playerRef.getUuid());
        int newStock = MghgShopStockManager.getPlayerStock(playerRef.getUuid(), item.getId());
        ctx.sendMessage(Message.raw(String.format(
                Locale.ROOT,
                "Compraste %dx %s (shopId=%s) por $%s | balance=$%s | stock personal=%d",
                qty,
                buyItemId,
                item.getId(),
                formatMoney(total),
                formatMoney(newBalance),
                newStock
        )));
        FarmShopCommand.refreshHudForPlayer(store, playerEntityRef, playerRef, world);
    }

    private static String formatMoney(double value) {
        return String.format(Locale.ROOT, "%.2f", Math.max(0.0, value));
    }
}
