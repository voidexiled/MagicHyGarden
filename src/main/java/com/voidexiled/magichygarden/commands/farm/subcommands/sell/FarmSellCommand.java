package com.voidexiled.magichygarden.commands.farm.subcommands.sell;

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
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackSlotTransaction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.voidexiled.magichygarden.commands.farm.subcommands.shop.FarmShopCommand;
import com.voidexiled.magichygarden.features.farming.economy.MghgEconomyManager;
import com.voidexiled.magichygarden.features.farming.items.MghgCropMeta;
import com.voidexiled.magichygarden.features.farming.shop.MghgShopAccessPolicy;
import com.voidexiled.magichygarden.features.farming.shop.MghgShopConfig;
import com.voidexiled.magichygarden.features.farming.shop.MghgShopPricing;
import com.voidexiled.magichygarden.features.farming.shop.MghgShopStockManager;
import com.voidexiled.magichygarden.features.farming.storage.MghgPlayerNameManager;
import javax.annotation.Nonnull;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Locale;

public class FarmSellCommand extends AbstractPlayerCommand {
    private final RequiredArg<String> shopIdArg;
    private final DefaultArg<Integer> qtyArg;

    public FarmSellCommand() {
        super("sell", "magichygarden.command.farm.sell.description");
        this.shopIdArg = withRequiredArg(
                "shopId",
                "magichygarden.command.farm.sell.args.item.description",
                ArgTypes.STRING
        );
        this.qtyArg = withDefaultArg(
                "qty",
                "magichygarden.command.farm.sell.args.qty.description",
                ArgTypes.INTEGER,
                1,
                "1"
        ).addValidator(Validators.greaterThanOrEqual(1));
    }

    @Override
    protected void execute(
            @Nonnull CommandContext ctx,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> playerEntityRef,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
    ) {
        MghgPlayerNameManager.remember(playerRef);
        String accessError = MghgShopAccessPolicy.validateTransactionContext(store, playerEntityRef, playerRef, world);
        if (accessError != null) {
            ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text(accessError));
            return;
        }

        String requested = shopIdArg.get(ctx);
        int qty = Math.max(1, qtyArg.get(ctx));

        MghgShopConfig.ShopItem item = MghgShopStockManager.getConfiguredItem(requested);
        if (item == null || item.getId() == null) {
            ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("shopId no encontrado. Usa /farm stock."));
            return;
        }
        if (item.getSellPrice() <= 0.0) {
            ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("Este item no se puede vender."));
            return;
        }

        Player player = store.getComponent(playerEntityRef, Player.getComponentType());
        if (player == null) {
            ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("No pude obtener el componente de jugador."));
            return;
        }

        ItemContainer inventory = player.getInventory().getCombinedStorageFirst();
        String[] sellItemIds = item.resolveSellItemIds();
        if (sellItemIds.length == 0) {
            ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("Item mal configurado: no tiene SellItemIds/Id."));
            return;
        }

        ArrayList<SellSelection> selections = collectSelections(inventory, sellItemIds, item, qty);
        int found = countFound(inventory, sellItemIds);
        if (found < qty) {
            ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("No tienes suficiente cantidad vendible. Disponible=" + found));
            return;
        }

        ArrayList<ItemStack> rollbackStacks = new ArrayList<>();
        double gain = 0.0d;
        for (SellSelection selection : selections) {
            ItemStackSlotTransaction transaction = inventory.removeItemStackFromSlot(
                    selection.slot(),
                    selection.quantity(),
                    true,
                    true
            );
            if (!transaction.succeeded() || !ItemStack.isEmpty(transaction.getRemainder())) {
                rollbackSell(inventory, rollbackStacks);
                ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("No pude remover items del inventario, venta revertida."));
                return;
            }
            ItemStack removed = selection.stack().withQuantity(selection.quantity());
            if (removed != null && !ItemStack.isEmpty(removed)) {
                rollbackStacks.add(removed);
            }
            gain += selection.unitPrice() * selection.quantity();
        }

        if (gain <= 0.0d) {
            rollbackSell(inventory, rollbackStacks);
            ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("La venta resulto en $0.00, cancelada."));
            return;
        }

        MghgEconomyManager.deposit(playerRef.getUuid(), gain);
        double newBalance = MghgEconomyManager.getBalance(playerRef.getUuid());

        ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text(String.format(
                Locale.ROOT,
                "Vendiste %dx (shopId=%s) por $%s | balance=$%s",
                qty,
                item.getId(),
                formatMoney(gain),
                formatMoney(newBalance)
        )));
        FarmShopCommand.refreshHudForPlayer(store, playerEntityRef, playerRef, world);
    }

    private static int countFound(@Nonnull ItemContainer inventory, @Nonnull String[] sellItemIds) {
        int found = 0;
        short capacity = inventory.getCapacity();
        for (short slot = 0; slot < capacity; slot++) {
            ItemStack stack = inventory.getItemStack(slot);
            if (ItemStack.isEmpty(stack)) {
                continue;
            }
            if (!matchesSellItem(stack, sellItemIds)) {
                continue;
            }
            found += Math.max(0, stack.getQuantity());
        }
        return found;
    }

    private static ArrayList<SellSelection> collectSelections(
            @Nonnull ItemContainer inventory,
            @Nonnull String[] sellItemIds,
            MghgShopConfig.ShopItem item,
            int quantity
    ) {
        ArrayList<SellSelection> selections = new ArrayList<>();
        int remaining = Math.max(0, quantity);
        short capacity = inventory.getCapacity();
        for (short slot = 0; slot < capacity && remaining > 0; slot++) {
            ItemStack stack = inventory.getItemStack(slot);
            if (ItemStack.isEmpty(stack)) {
                continue;
            }
            if (!matchesSellItem(stack, sellItemIds)) {
                continue;
            }
            int available = Math.max(0, stack.getQuantity());
            if (available <= 0) {
                continue;
            }
            int take = Math.min(remaining, available);
            MghgCropMeta meta = stack.getFromMetadataOrNull(MghgCropMeta.KEY);
            double unitPrice = MghgShopPricing.computeUnitSellPrice(item, meta);
            selections.add(new SellSelection(slot, stack, take, unitPrice));
            remaining -= take;
        }
        return selections;
    }

    private static boolean matchesSellItem(@Nonnull ItemStack stack, @Nonnull String[] acceptedItemIds) {
        String current = normalizeItemId(stack.getItemId());
        for (String accepted : acceptedItemIds) {
            String normalizedAccepted = normalizeItemId(accepted);
            if (normalizedAccepted != null && normalizedAccepted.equalsIgnoreCase(current)) {
                return true;
            }
        }
        return false;
    }

    private static @Nullable String normalizeItemId(@Nullable String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return null;
        }
        String raw = itemId.trim();
        int idx = raw.indexOf("_State_");
        if (idx > 0) {
            if (raw.charAt(0) == '*') {
                return idx > 1 ? raw.substring(1, idx) : null;
            }
            return raw.substring(0, idx);
        }
        if (raw.charAt(0) == '*') {
            return raw.substring(1);
        }
        return raw;
    }

    private static void rollbackSell(@Nonnull ItemContainer inventory, @Nonnull ArrayList<ItemStack> rollbackStacks) {
        for (ItemStack stack : rollbackStacks) {
            if (ItemStack.isEmpty(stack)) {
                continue;
            }
            inventory.addItemStack(stack, true, false, true);
        }
    }

    private record SellSelection(short slot, ItemStack stack, int quantity, double unitPrice) {
    }

    private static String formatMoney(double value) {
        return String.format(Locale.ROOT, "%.2f", Math.max(0.0, value));
    }
}
