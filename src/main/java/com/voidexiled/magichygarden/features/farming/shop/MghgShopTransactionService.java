package com.voidexiled.magichygarden.features.farming.shop;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackSlotTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.voidexiled.magichygarden.features.farming.economy.MghgEconomyManager;
import com.voidexiled.magichygarden.features.farming.items.MghgCropMeta;
import com.voidexiled.magichygarden.features.farming.perks.MghgFarmPerkManager;
import com.voidexiled.magichygarden.features.farming.storage.MghgPlayerNameManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.UUID;

public final class MghgShopTransactionService {
    private static final String LANG_PREFIX = "server.";

    private MghgShopTransactionService() {
    }

    public static @Nonnull MghgShopTransactionResult buy(
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> playerEntityRef,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world,
            @Nullable String shopId,
            int qty
    ) {
        MghgPlayerNameManager.remember(playerRef);
        String accessError = MghgShopAccessPolicy.validateTransactionContext(store, playerEntityRef, playerRef, world);
        if (accessError != null) {
            return MghgShopTransactionResult.fail(accessError);
        }

        String requested = normalize(shopId);
        int quantity = Math.max(1, qty);

        MghgShopConfig.ShopItem item = MghgShopStockManager.getConfiguredItem(requested);
        if (item == null || item.getId() == null) {
            return MghgShopTransactionResult.fail("shopId no encontrado. Usa /farm stock.");
        }
        String buyItemId = item.resolveBuyItemId();
        if (buyItemId == null || buyItemId.isBlank()) {
            return MghgShopTransactionResult.fail("Item mal configurado (BuyItemId/Id vacio).");
        }
        if (item.getBuyPrice() <= 0.0d) {
            return MghgShopTransactionResult.fail("Este item no se puede comprar.");
        }

        int stock = MghgShopStockManager.getPlayerStock(playerRef.getUuid(), item.getId());
        if (stock < quantity) {
            return MghgShopTransactionResult.fail("Stock personal insuficiente: " + stock + " disponible en este ciclo.");
        }

        double total = item.getBuyPrice() * quantity;
        double balance = MghgEconomyManager.getBalance(playerRef.getUuid());
        if (balance < total) {
            return MghgShopTransactionResult.fail("Balance insuficiente. Necesitas $" + formatMoney(total));
        }

        Player player = store.getComponent(playerEntityRef, Player.getComponentType());
        if (player == null) {
            return MghgShopTransactionResult.fail("No pude obtener el componente de jugador.");
        }
        ItemContainer inventory = player.getInventory().getCombinedStorageFirst();
        ItemStack stack = new ItemStack(buyItemId, quantity);
        if (!inventory.canAddItemStack(stack, false, true)) {
            return MghgShopTransactionResult.fail("No tienes espacio suficiente en inventario.");
        }

        if (!MghgEconomyManager.withdraw(playerRef.getUuid(), total)) {
            return MghgShopTransactionResult.fail("No pude debitar balance.");
        }

        if (!MghgShopStockManager.consumePlayerStock(playerRef.getUuid(), item.getId(), quantity)) {
            MghgEconomyManager.deposit(playerRef.getUuid(), total);
            return MghgShopTransactionResult.fail("No pude consumir stock, compra cancelada.");
        }

        ItemStackTransaction transaction = inventory.addItemStack(stack, true, false, true);
        if (!transaction.succeeded() || !ItemStack.isEmpty(transaction.getRemainder())) {
            MghgEconomyManager.deposit(playerRef.getUuid(), total);
            MghgShopStockManager.releasePlayerStock(playerRef.getUuid(), item.getId(), quantity);
            return MghgShopTransactionResult.fail("Inventario no valido para la compra, compra revertida.");
        }

        double newBalance = MghgEconomyManager.getBalance(playerRef.getUuid());
        int newStock = MghgShopStockManager.getPlayerStock(playerRef.getUuid(), item.getId());
        String buyName = resolveItemDisplayName(buyItemId, playerRef);
        MghgShopUiLogManager.append(
                playerRef.getUuid(),
                buildBuyLogLine(item.getId(), buyItemId, quantity, item.getBuyPrice(), total, newBalance, newStock)
        );
        return MghgShopTransactionResult.ok(String.format(
                Locale.ROOT,
                "Compraste %dx %s.%nTotal: $%s%nBalance: $%s%nStock personal restante: %d",
                quantity,
                buyName,
                formatMoney(total),
                formatMoney(newBalance),
                newStock
        ));
    }

    public static @Nonnull MghgShopTransactionResult buyMax(
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> playerEntityRef,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world,
            @Nullable String shopId
    ) {
        MghgPlayerNameManager.remember(playerRef);
        String accessError = MghgShopAccessPolicy.validateTransactionContext(store, playerEntityRef, playerRef, world);
        if (accessError != null) {
            return MghgShopTransactionResult.fail(accessError);
        }

        String requested = normalize(shopId);
        MghgShopConfig.ShopItem item = MghgShopStockManager.getConfiguredItem(requested);
        if (item == null || item.getId() == null) {
            return MghgShopTransactionResult.fail("shopId no encontrado. Usa /farm stock.");
        }
        String buyItemId = item.resolveBuyItemId();
        if (buyItemId == null || buyItemId.isBlank()) {
            return MghgShopTransactionResult.fail("Item mal configurado (BuyItemId/Id vacio).");
        }
        if (item.getBuyPrice() <= 0.0d) {
            return MghgShopTransactionResult.fail("Este item no se puede comprar.");
        }

        Player player = store.getComponent(playerEntityRef, Player.getComponentType());
        if (player == null) {
            return MghgShopTransactionResult.fail("No pude obtener el componente de jugador.");
        }
        ItemContainer inventory = player.getInventory().getCombinedStorageFirst();

        int stock = MghgShopStockManager.getPlayerStock(playerRef.getUuid(), item.getId());
        if (stock <= 0) {
            return MghgShopTransactionResult.fail("No tienes stock personal disponible para este item en este ciclo.");
        }

        double balance = MghgEconomyManager.getBalance(playerRef.getUuid());
        int byBalance = maxAffordable(balance, item.getBuyPrice());
        if (byBalance <= 0) {
            return MghgShopTransactionResult.fail("Balance insuficiente para comprar este item.");
        }

        int cap = Math.min(stock, byBalance);
        int maxQty = resolveMaxInventoryFit(inventory, buyItemId, cap);
        if (maxQty <= 0) {
            return MghgShopTransactionResult.fail("No tienes espacio suficiente en inventario.");
        }

        double total = item.getBuyPrice() * maxQty;
        if (!MghgEconomyManager.withdraw(playerRef.getUuid(), total)) {
            return MghgShopTransactionResult.fail("No pude debitar balance.");
        }
        if (!MghgShopStockManager.consumePlayerStock(playerRef.getUuid(), item.getId(), maxQty)) {
            MghgEconomyManager.deposit(playerRef.getUuid(), total);
            return MghgShopTransactionResult.fail("No pude consumir stock, compra cancelada.");
        }

        ItemStackTransaction transaction = inventory.addItemStack(new ItemStack(buyItemId, maxQty), true, false, true);
        if (!transaction.succeeded() || !ItemStack.isEmpty(transaction.getRemainder())) {
            MghgEconomyManager.deposit(playerRef.getUuid(), total);
            MghgShopStockManager.releasePlayerStock(playerRef.getUuid(), item.getId(), maxQty);
            return MghgShopTransactionResult.fail("Inventario no valido para la compra, compra revertida.");
        }

        double newBalance = MghgEconomyManager.getBalance(playerRef.getUuid());
        int newStock = MghgShopStockManager.getPlayerStock(playerRef.getUuid(), item.getId());
        String buyName = resolveItemDisplayName(buyItemId, playerRef);
        MghgShopUiLogManager.append(
                playerRef.getUuid(),
                buildBuyLogLine(item.getId(), buyItemId, maxQty, item.getBuyPrice(), total, newBalance, newStock)
        );
        return MghgShopTransactionResult.ok(String.format(
                Locale.ROOT,
                "Compraste MAX %dx %s.%nTotal: $%s%nBalance: $%s%nStock personal restante: %d",
                maxQty,
                buyName,
                formatMoney(total),
                formatMoney(newBalance),
                newStock
        ));
    }

    public static @Nonnull MghgShopTransactionResult sell(
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> playerEntityRef,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world,
            @Nullable String shopId,
            int qty
    ) {
        MghgPlayerNameManager.remember(playerRef);
        String accessError = MghgShopAccessPolicy.validateTransactionContext(store, playerEntityRef, playerRef, world);
        if (accessError != null) {
            return MghgShopTransactionResult.fail(accessError);
        }

        String requested = normalize(shopId);
        int quantity = Math.max(1, qty);
        MghgShopConfig.ShopItem item = MghgShopStockManager.getConfiguredItem(requested);
        if (item == null || item.getId() == null) {
            return MghgShopTransactionResult.fail("shopId no encontrado. Usa /farm stock.");
        }
        if (item.getSellPrice() <= 0.0d) {
            return MghgShopTransactionResult.fail("Este item no se puede vender.");
        }

        Player player = store.getComponent(playerEntityRef, Player.getComponentType());
        if (player == null) {
            return MghgShopTransactionResult.fail("No pude obtener el componente de jugador.");
        }
        ItemContainer inventory = player.getInventory().getCombinedStorageFirst();
        String[] sellItemIds = item.resolveSellItemIds();
        if (sellItemIds.length == 0) {
            return MghgShopTransactionResult.fail("Item mal configurado: no tiene SellItemIds/Id.");
        }

        double sellMultiplier = resolveSellMultiplier(playerRef, world);
        ArrayList<SellSelection> selections = collectSelections(inventory, sellItemIds, item, quantity, sellMultiplier);
        int found = countSellable(inventory, sellItemIds);
        if (found < quantity) {
            return MghgShopTransactionResult.fail("No tienes suficiente cantidad vendible. Disponible=" + found);
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
                return MghgShopTransactionResult.fail("No pude remover items del inventario, venta revertida.");
            }
            ItemStack removed = selection.stack().withQuantity(selection.quantity());
            if (removed != null && !ItemStack.isEmpty(removed)) {
                rollbackStacks.add(removed);
            }
            gain += selection.unitPrice() * selection.quantity();
        }

        if (gain <= 0.0d) {
            rollbackSell(inventory, rollbackStacks);
            return MghgShopTransactionResult.fail("La venta resulto en $0.00, cancelada.");
        }

        MghgEconomyManager.deposit(playerRef.getUuid(), gain);
        double newBalance = MghgEconomyManager.getBalance(playerRef.getUuid());
        ArrayList<String> logLines = new ArrayList<>();
        for (SellSelection selection : selections) {
            MghgCropMeta meta = selection.stack().getFromMetadataOrNull(MghgCropMeta.KEY);
            logLines.add(buildSellLogLine(
                    item,
                    selection.stack().getItemId(),
                    selection.quantity(),
                    selection.unitPrice(),
                    meta,
                    sellMultiplier
            ));
        }
        MghgShopUiLogManager.appendAll(playerRef.getUuid(), logLines.toArray(String[]::new));
        MghgShopUiLogManager.append(playerRef.getUuid(), "Balance after sell: $" + formatMoney(newBalance));
        String soldName = selections.isEmpty()
                ? resolveItemDisplayName(firstSellTarget(item), playerRef)
                : resolveItemDisplayName(selections.get(0).stack().getItemId(), playerRef);
        return MghgShopTransactionResult.ok(String.format(
                Locale.ROOT,
                "Vendiste %dx %s.%nTotal: $%s%nBalance: $%s",
                quantity,
                soldName,
                formatMoney(gain),
                formatMoney(newBalance)
        ));
    }

    public static @Nonnull MghgShopTransactionResult sellSlot(
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> playerEntityRef,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world,
            @Nullable String shopId,
            int slot
    ) {
        MghgPlayerNameManager.remember(playerRef);
        String accessError = MghgShopAccessPolicy.validateTransactionContext(store, playerEntityRef, playerRef, world);
        if (accessError != null) {
            return MghgShopTransactionResult.fail(accessError);
        }

        String requested = normalize(shopId);
        MghgShopConfig.ShopItem item = MghgShopStockManager.getConfiguredItem(requested);
        if (item == null || item.getId() == null) {
            return MghgShopTransactionResult.fail("shopId no encontrado. Usa /farm stock.");
        }
        if (item.getSellPrice() <= 0.0d) {
            return MghgShopTransactionResult.fail("Este item no se puede vender.");
        }

        Player player = store.getComponent(playerEntityRef, Player.getComponentType());
        if (player == null) {
            return MghgShopTransactionResult.fail("No pude obtener el componente de jugador.");
        }

        ItemContainer inventory = player.getInventory().getCombinedStorageFirst();
        short capacity = inventory.getCapacity();
        if (slot < 0 || slot >= capacity) {
            return MghgShopTransactionResult.fail("Slot invalido para vender.");
        }

        ItemStack stack = inventory.getItemStack((short) slot);
        if (ItemStack.isEmpty(stack)) {
            return MghgShopTransactionResult.fail("Slot vacio.");
        }

        String[] sellItemIds = item.resolveSellItemIds();
        if (sellItemIds.length == 0) {
            return MghgShopTransactionResult.fail("Item mal configurado: no tiene SellItemIds/Id.");
        }
        if (!matchesSellItem(stack, sellItemIds)) {
            return MghgShopTransactionResult.fail("Ese slot no corresponde al item seleccionado.");
        }

        int quantity = Math.max(0, stack.getQuantity());
        if (quantity <= 0) {
            return MghgShopTransactionResult.fail("Cantidad invalida en slot.");
        }
        double sellMultiplier = resolveSellMultiplier(playerRef, world);
        MghgCropMeta meta = stack.getFromMetadataOrNull(MghgCropMeta.KEY);
        double unitPrice = MghgShopPricing.computeUnitSellPrice(item, meta) * sellMultiplier;
        double gain = unitPrice * quantity;
        if (gain <= 0.0d) {
            return MghgShopTransactionResult.fail("La venta resulto en $0.00, cancelada.");
        }

        ItemStackSlotTransaction transaction = inventory.removeItemStackFromSlot((short) slot, quantity, true, true);
        if (!transaction.succeeded() || !ItemStack.isEmpty(transaction.getRemainder())) {
            return MghgShopTransactionResult.fail("No pude remover items del inventario.");
        }

        MghgEconomyManager.deposit(playerRef.getUuid(), gain);
        double newBalance = MghgEconomyManager.getBalance(playerRef.getUuid());
        MghgShopUiLogManager.append(
                playerRef.getUuid(),
                buildSellLogLine(item, stack.getItemId(), quantity, unitPrice, meta, sellMultiplier)
        );
        MghgShopUiLogManager.append(playerRef.getUuid(), "Balance after sell: $" + formatMoney(newBalance));
        String soldName = resolveItemDisplayName(stack.getItemId(), playerRef);
        return MghgShopTransactionResult.ok(String.format(
                Locale.ROOT,
                "Vendiste %dx %s.%nTotal: $%s%nBalance: $%s",
                quantity,
                soldName,
                formatMoney(gain),
                formatMoney(newBalance)
        ));
    }

    public static @Nonnull MghgShopTransactionResult sellSlots(
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> playerEntityRef,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world,
            @Nullable String shopId,
            @Nonnull int[] slots
    ) {
        MghgPlayerNameManager.remember(playerRef);
        String accessError = MghgShopAccessPolicy.validateTransactionContext(store, playerEntityRef, playerRef, world);
        if (accessError != null) {
            return MghgShopTransactionResult.fail(accessError);
        }

        String requested = normalize(shopId);
        MghgShopConfig.ShopItem item = MghgShopStockManager.getConfiguredItem(requested);
        if (item == null || item.getId() == null) {
            return MghgShopTransactionResult.fail("shopId no encontrado. Usa /farm stock.");
        }
        if (item.getSellPrice() <= 0.0d) {
            return MghgShopTransactionResult.fail("Este item no se puede vender.");
        }
        if (slots.length == 0) {
            return MghgShopTransactionResult.fail("No hay slots seleccionados para vender.");
        }

        Player player = store.getComponent(playerEntityRef, Player.getComponentType());
        if (player == null) {
            return MghgShopTransactionResult.fail("No pude obtener el componente de jugador.");
        }
        ItemContainer inventory = player.getInventory().getCombinedStorageFirst();
        short capacity = inventory.getCapacity();
        String[] sellItemIds = item.resolveSellItemIds();
        if (sellItemIds.length == 0) {
            return MghgShopTransactionResult.fail("Item mal configurado: no tiene SellItemIds/Id.");
        }

        LinkedHashSet<Integer> unique = new LinkedHashSet<>();
        for (int slot : slots) {
            if (slot >= 0 && slot < capacity) {
                unique.add(slot);
            }
        }
        if (unique.isEmpty()) {
            return MghgShopTransactionResult.fail("No hay slots validos para vender.");
        }

        ArrayList<ItemStack> rollbackStacks = new ArrayList<>();
        ArrayList<String> logLines = new ArrayList<>();
        int soldUnits = 0;
        double gain = 0.0d;
        double sellMultiplier = resolveSellMultiplier(playerRef, world);
        for (int slot : unique) {
            ItemStack stack = inventory.getItemStack((short) slot);
            if (ItemStack.isEmpty(stack) || !matchesSellItem(stack, sellItemIds)) {
                continue;
            }
            int quantity = Math.max(0, stack.getQuantity());
            if (quantity <= 0) {
                continue;
            }
            MghgCropMeta meta = stack.getFromMetadataOrNull(MghgCropMeta.KEY);
            double unit = MghgShopPricing.computeUnitSellPrice(item, meta) * sellMultiplier;
            double slotGain = unit * quantity;
            if (slotGain <= 0.0d) {
                continue;
            }

            ItemStackSlotTransaction transaction = inventory.removeItemStackFromSlot((short) slot, quantity, true, true);
            if (!transaction.succeeded() || !ItemStack.isEmpty(transaction.getRemainder())) {
                rollbackSell(inventory, rollbackStacks);
                return MghgShopTransactionResult.fail("No pude remover items del inventario, venta revertida.");
            }

            ItemStack removed = stack.withQuantity(quantity);
            if (removed != null && !ItemStack.isEmpty(removed)) {
                rollbackStacks.add(removed);
            }
            soldUnits += quantity;
            gain += slotGain;
            logLines.add(buildSellLogLine(item, stack.getItemId(), quantity, unit, meta, sellMultiplier));
        }

        if (soldUnits <= 0 || gain <= 0.0d) {
            return MghgShopTransactionResult.fail("No hay items vendibles en la seleccion actual.");
        }

        MghgEconomyManager.deposit(playerRef.getUuid(), gain);
        double newBalance = MghgEconomyManager.getBalance(playerRef.getUuid());
        MghgShopUiLogManager.appendAll(playerRef.getUuid(), logLines.toArray(String[]::new));
        MghgShopUiLogManager.append(playerRef.getUuid(), "Balance after sell: $" + formatMoney(newBalance));
        String soldName = unique.isEmpty()
                ? resolveItemDisplayName(firstSellTarget(item), playerRef)
                : resolveFirstSelectedName(inventory, unique, sellItemIds, playerRef, item);
        return MghgShopTransactionResult.ok(String.format(
                Locale.ROOT,
                "Vendiste seleccion: %dx %s.%nTotal: $%s%nBalance: $%s",
                soldUnits,
                soldName,
                formatMoney(gain),
                formatMoney(newBalance)
        ));
    }

    public static @Nonnull MghgShopTransactionResult sellAll(
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> playerEntityRef,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world,
            @Nullable String selector
    ) {
        MghgPlayerNameManager.remember(playerRef);
        String accessError = MghgShopAccessPolicy.validateTransactionContext(store, playerEntityRef, playerRef, world);
        if (accessError != null) {
            return MghgShopTransactionResult.fail(accessError);
        }

        Player player = store.getComponent(playerEntityRef, Player.getComponentType());
        if (player == null) {
            return MghgShopTransactionResult.fail("No pude obtener el componente de jugador.");
        }
        ItemContainer inventory = player.getInventory().getCombinedStorageFirst();

        String normalizedSelector = normalize(selector);
        MghgShopConfig.ShopItem[] selectedItems = resolveSellTargets(normalizedSelector);
        if (selectedItems.length == 0) {
            if ("all".equals(normalizedSelector)) {
                return MghgShopTransactionResult.fail("No hay shopIds vendibles configurados.");
            }
            return MghgShopTransactionResult.fail("shopId no encontrado o no vendible.");
        }

        ArrayList<ItemStack> rollbackStacks = new ArrayList<>();
        ArrayList<String> logLines = new ArrayList<>();
        int soldUnits = 0;
        int soldShops = 0;
        double gain = 0.0d;
        double sellMultiplier = resolveSellMultiplier(playerRef, world);
        for (MghgShopConfig.ShopItem item : selectedItems) {
            SellAllResult result = sellAllForItem(inventory, item, rollbackStacks, logLines, sellMultiplier);
            if (result.hasError()) {
                rollbackSell(inventory, rollbackStacks);
                return MghgShopTransactionResult.fail("No pude remover items del inventario, venta revertida.");
            }
            if (result.units() > 0) {
                soldShops++;
                soldUnits += result.units();
                gain += result.gain();
            }
        }

        if (soldUnits <= 0 || gain <= 0.0d) {
            return MghgShopTransactionResult.fail("No tienes items vendibles para ese shopId.");
        }

        MghgEconomyManager.deposit(playerRef.getUuid(), gain);
        double newBalance = MghgEconomyManager.getBalance(playerRef.getUuid());
        MghgShopUiLogManager.appendAll(playerRef.getUuid(), logLines.toArray(String[]::new));
        MghgShopUiLogManager.append(playerRef.getUuid(), "Balance after sell: $" + formatMoney(newBalance));
        String label = "all".equals(normalizedSelector) ? "all" : normalizedSelector;
        return MghgShopTransactionResult.ok(String.format(
                Locale.ROOT,
                "Vendiste ALL %d items en %d categorias (%s).%nTotal: $%s%nBalance: $%s",
                soldUnits,
                soldShops,
                label,
                formatMoney(gain),
                formatMoney(newBalance)
        ));
    }

    public static int countSellable(@Nonnull ItemContainer inventory, @Nonnull String[] acceptedItemIds) {
        int found = 0;
        short capacity = inventory.getCapacity();
        for (short slot = 0; slot < capacity; slot++) {
            ItemStack stack = inventory.getItemStack(slot);
            if (ItemStack.isEmpty(stack)) {
                continue;
            }
            if (!matchesSellItem(stack, acceptedItemIds)) {
                continue;
            }
            found += Math.max(0, stack.getQuantity());
        }
        return found;
    }

    public static double estimateSellValue(@Nonnull ItemContainer inventory, @Nonnull MghgShopConfig.ShopItem item) {
        return estimateSellValue(inventory, item, 1.0d);
    }

    public static double estimateSellValue(
            @Nonnull ItemContainer inventory,
            @Nonnull MghgShopConfig.ShopItem item,
            double sellMultiplier
    ) {
        String[] sellItemIds = item.resolveSellItemIds();
        double total = 0.0d;
        short capacity = inventory.getCapacity();
        for (short slot = 0; slot < capacity; slot++) {
            ItemStack stack = inventory.getItemStack(slot);
            if (ItemStack.isEmpty(stack)) {
                continue;
            }
            if (!matchesSellItem(stack, sellItemIds)) {
                continue;
            }
            MghgCropMeta meta = stack.getFromMetadataOrNull(MghgCropMeta.KEY);
            double unit = MghgShopPricing.computeUnitSellPrice(item, meta) * sanitizeMultiplier(sellMultiplier);
            total += unit * Math.max(0, stack.getQuantity());
        }
        return total;
    }

    private static @Nonnull String resolveFirstSelectedName(
            @Nonnull ItemContainer inventory,
            @Nonnull LinkedHashSet<Integer> slots,
            @Nonnull String[] sellItemIds,
            @Nonnull PlayerRef playerRef,
            @Nonnull MghgShopConfig.ShopItem item
    ) {
        short capacity = inventory.getCapacity();
        for (int slot : slots) {
            if (slot < 0 || slot >= capacity) {
                continue;
            }
            ItemStack stack = inventory.getItemStack((short) slot);
            if (ItemStack.isEmpty(stack) || !matchesSellItem(stack, sellItemIds)) {
                continue;
            }
            return resolveItemDisplayName(stack.getItemId(), playerRef);
        }
        return resolveItemDisplayName(firstSellTarget(item), playerRef);
    }

    private static @Nullable String firstSellTarget(@Nonnull MghgShopConfig.ShopItem item) {
        String[] sellItemIds = item.resolveSellItemIds();
        if (sellItemIds.length == 0) {
            return item.resolveBuyItemId();
        }
        return sellItemIds[0];
    }

    private static ArrayList<SellSelection> collectSelections(
            @Nonnull ItemContainer inventory,
            @Nonnull String[] sellItemIds,
            @Nonnull MghgShopConfig.ShopItem item,
            int quantity,
            double sellMultiplier
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
            double unitPrice = MghgShopPricing.computeUnitSellPrice(item, meta) * sanitizeMultiplier(sellMultiplier);
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

    private static int resolveMaxInventoryFit(@Nonnull ItemContainer inventory, @Nonnull String itemId, int cap) {
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

    private static MghgShopConfig.ShopItem[] resolveSellTargets(@Nonnull String selector) {
        if ("all".equals(selector)) {
            ArrayList<MghgShopConfig.ShopItem> list = new ArrayList<>();
            for (MghgShopConfig.ShopItem item : MghgShopStockManager.getConfiguredItems()) {
                if (item == null || item.getId() == null || item.getId().isBlank()) {
                    continue;
                }
                if (item.getSellPrice() > 0.0d) {
                    list.add(item);
                }
            }
            return list.toArray(MghgShopConfig.ShopItem[]::new);
        }
        MghgShopConfig.ShopItem single = MghgShopStockManager.getConfiguredItem(selector);
        if (single == null || single.getSellPrice() <= 0.0d) {
            return new MghgShopConfig.ShopItem[0];
        }
        return new MghgShopConfig.ShopItem[]{single};
    }

    private static SellAllResult sellAllForItem(
            @Nonnull ItemContainer inventory,
            @Nonnull MghgShopConfig.ShopItem item,
            @Nonnull ArrayList<ItemStack> rollbackStacks,
            @Nonnull ArrayList<String> logLines,
            double sellMultiplier
    ) {
        String[] sellItemIds = item.resolveSellItemIds();
        if (sellItemIds.length == 0) {
            return SellAllResult.empty();
        }

        int units = 0;
        double gain = 0.0d;
        short capacity = inventory.getCapacity();
        for (short slot = 0; slot < capacity; slot++) {
            ItemStack stack = inventory.getItemStack(slot);
            if (ItemStack.isEmpty(stack)) {
                continue;
            }
            if (!matchesSellItem(stack, sellItemIds)) {
                continue;
            }
            int take = Math.max(0, stack.getQuantity());
            if (take <= 0) {
                continue;
            }

            MghgCropMeta meta = stack.getFromMetadataOrNull(MghgCropMeta.KEY);
            double unitPrice = MghgShopPricing.computeUnitSellPrice(item, meta) * sanitizeMultiplier(sellMultiplier);

            ItemStackSlotTransaction transaction = inventory.removeItemStackFromSlot(slot, take, true, true);
            if (!transaction.succeeded() || !ItemStack.isEmpty(transaction.getRemainder())) {
                return SellAllResult.error();
            }

            ItemStack removed = stack.withQuantity(take);
            if (removed != null && !ItemStack.isEmpty(removed)) {
                rollbackStacks.add(removed);
            }
            logLines.add(buildSellLogLine(item, stack.getItemId(), take, unitPrice, meta, sellMultiplier));
            units += take;
            gain += unitPrice * take;
        }
        return units <= 0 ? SellAllResult.empty() : SellAllResult.success(units, gain);
    }

    private static String normalize(@Nullable String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static double resolveSellMultiplier(@Nonnull PlayerRef playerRef, @Nonnull World world) {
        return sanitizeMultiplier(
                MghgFarmPerkManager.resolveSellMultiplierForContext(playerRef.getUuid(), world)
        );
    }

    private static String buildBuyLogLine(
            @Nullable String shopId,
            @Nullable String itemId,
            int quantity,
            double unitPrice,
            double totalPrice,
            double newBalance,
            int personalStock
    ) {
        return String.format(
                Locale.ROOT,
                "BUY | %s x%d | unit $%s | total $%s | shop=%s | stock=%d | balance=$%s",
                toDisplayName(itemId),
                Math.max(1, quantity),
                formatMoney(unitPrice),
                formatMoney(totalPrice),
                safeText(shopId),
                Math.max(0, personalStock),
                formatMoney(newBalance)
        );
    }

    private static String buildSellLogLine(
            @Nonnull MghgShopConfig.ShopItem shopItem,
            @Nullable String itemId,
            int quantity,
            double unitPrice,
            @Nullable MghgCropMeta meta,
            double sellMultiplier
    ) {
        double sizeMultiplier = meta == null ? 1.0d : MghgShopPricing.computeSizeMultiplier(shopItem, meta.getSize());
        double climateMultiplier = meta == null ? 1.0d : sanitizeMultiplier(shopItem.getClimateMultiplier(meta.getClimate()));
        double lunarMultiplier = meta == null ? 1.0d : sanitizeMultiplier(shopItem.getLunarMultiplier(meta.getLunar()));
        double rarityMultiplier = meta == null ? 1.0d : sanitizeMultiplier(shopItem.getRarityMultiplier(meta.getRarity()));
        String size = meta == null ? "-" : Integer.toString(meta.getSize());
        String climate = meta == null ? "none" : safeText(meta.getClimate()).toLowerCase(Locale.ROOT);
        String lunar = meta == null ? "none" : safeText(meta.getLunar()).toLowerCase(Locale.ROOT);
        String rarity = meta == null ? "none" : safeText(meta.getRarity()).toLowerCase(Locale.ROOT);
        return String.format(
                Locale.ROOT,
                "SELL | %s x%d | unit $%s | total $%s | shop=%s | sell_perk=x%s | size=%s(x%s) climate=%s(x%s) lunar=%s(x%s) rarity=%s(x%s)",
                toDisplayName(itemId),
                Math.max(1, quantity),
                formatMoney(unitPrice),
                formatMoney(unitPrice * Math.max(1, quantity)),
                safeText(shopItem.getId()),
                formatMoney(sanitizeMultiplier(sellMultiplier)),
                size,
                formatMoney(sizeMultiplier),
                climate,
                formatMoney(climateMultiplier),
                lunar,
                formatMoney(lunarMultiplier),
                rarity,
                formatMoney(rarityMultiplier)
        );
    }

    private static String toDisplayName(@Nullable String rawId) {
        String id = normalizeItemId(rawId);
        if (id == null || id.isBlank()) {
            return "Unknown";
        }
        String clean = id;
        if (clean.startsWith("Mghg_")) {
            clean = clean.substring(5);
        }
        if (clean.startsWith("Plant_Seeds_")) {
            clean = clean.substring("Plant_Seeds_".length());
        } else if (clean.startsWith("Plant_Crop_")) {
            clean = clean.substring("Plant_Crop_".length());
        }
        clean = trimSuffixToken(clean, "_Item");
        clean = trimSuffixToken(clean, "_Block");
        clean = trimSuffixToken(clean, "_Seeds");
        String[] parts = clean.split("_");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            String lower = part.toLowerCase(Locale.ROOT);
            out.append(Character.toUpperCase(lower.charAt(0))).append(lower.substring(1));
        }
        return out.isEmpty() ? id : out.toString();
    }

    private static String resolveItemDisplayName(@Nullable String rawId, @Nullable PlayerRef playerRef) {
        String id = normalizeItemId(rawId);
        if (id == null || id.isBlank()) {
            return "Unknown";
        }
        Item item = Item.getAssetMap().getAsset(id);
        if (item != null) {
            String translationKey = item.getTranslationKey();
            if (translationKey != null && !translationKey.isBlank()) {
                String translated = translateKey(playerRef, translationKey);
                if (!translated.isBlank()
                        && !translated.equals(translationKey)
                        && !translated.equals(LANG_PREFIX + translationKey)) {
                    return translated;
                }
            }
        }
        return toDisplayName(id);
    }

    private static String translateKey(@Nullable PlayerRef playerRef, @Nonnull String key) {
        I18nModule i18n = I18nModule.get();
        if (i18n == null) {
            return key;
        }
        String language = playerRef != null ? playerRef.getLanguage() : null;
        String normalized = key.startsWith(LANG_PREFIX) ? key : (LANG_PREFIX + key);
        String direct = i18n.getMessages(language).get(key);
        if (direct != null && !direct.isBlank()) {
            return direct;
        }
        String prefixed = i18n.getMessages(language).get(normalized);
        if (prefixed != null && !prefixed.isBlank()) {
            return prefixed;
        }
        return key;
    }

    private static String trimSuffixToken(@Nonnull String input, @Nonnull String suffix) {
        if (input.length() <= suffix.length()) {
            return input;
        }
        if (input.toLowerCase(Locale.ROOT).endsWith(suffix.toLowerCase(Locale.ROOT))) {
            return input.substring(0, input.length() - suffix.length());
        }
        return input;
    }

    private static String safeText(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value.trim();
    }

    private static double sanitizeMultiplier(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 1.0d;
        }
        return Math.max(0.0d, value);
    }

    private static String formatMoney(double value) {
        return String.format(Locale.ROOT, "%.2f", Math.max(0.0d, value));
    }

    private record SellSelection(short slot, ItemStack stack, int quantity, double unitPrice) {
    }

    private record SellAllResult(boolean hasError, int units, double gain) {
        private static SellAllResult error() {
            return new SellAllResult(true, 0, 0.0d);
        }

        private static SellAllResult empty() {
            return new SellAllResult(false, 0, 0.0d);
        }

        private static SellAllResult success(int units, double gain) {
            return new SellAllResult(false, units, gain);
        }
    }
}
