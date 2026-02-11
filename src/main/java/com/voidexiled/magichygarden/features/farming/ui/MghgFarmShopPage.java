package com.voidexiled.magichygarden.features.farming.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.voidexiled.magichygarden.features.farming.economy.MghgEconomyManager;
import com.voidexiled.magichygarden.features.farming.items.MghgCropMeta;
import com.voidexiled.magichygarden.features.farming.shop.MghgShopAccessPolicy;
import com.voidexiled.magichygarden.features.farming.shop.MghgShopConfig;
import com.voidexiled.magichygarden.features.farming.shop.MghgShopPricing;
import com.voidexiled.magichygarden.features.farming.shop.MghgShopStockManager;
import com.voidexiled.magichygarden.features.farming.shop.MghgShopTransactionResult;
import com.voidexiled.magichygarden.features.farming.shop.MghgShopTransactionService;
import com.voidexiled.magichygarden.features.farming.ui.MghgMutationUiPalette;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

public final class MghgFarmShopPage extends InteractiveCustomUIPage<MghgFarmShopPage.EventPayload> {
    private static final String UI_PATH = "Pages/Mghg_FarmShopPage.ui";
    private static final String SLOT_TEMPLATE_PATH = "Pages/Mghg_FarmShopGridSlot.ui";
    private static final String ROW_TEMPLATE_PATH = "Pages/Mghg_FarmShopGridRow.ui";
    private static final String ROOT = "#MghgFarmShopPage";

    private static final String ACTION_SELECT_BUY_SLOT = "SelectBuySlot";
    private static final String ACTION_SELECT_SELL_SLOT = "SelectSellSlot";
    private static final String ACTION_BUY = "Buy1";
    private static final String ACTION_BUY_10 = "Buy10";
    private static final String ACTION_BUY_MAX = "BuyMax";
    private static final String ACTION_SELL = "Sell1";
    private static final String ACTION_SELL_SELECTED = "SellSelected";
    private static final String ACTION_SELL_ALL = "SellAll";
    private static final String ACTION_SELECT_ALL_SELL = "SelectAllSell";
    private static final String ACTION_UNSELECT_ALL_SELL = "UnselectAllSell";
    private static final String ACTION_CLOSE = "Close";

    private static final int MAX_BUY_GRID_SLOTS = 72;
    private static final int MAX_SELL_GRID_SLOTS = 72;
    private static final int GRID_SLOTS_PER_ROW = 8;
    private static final String LANG_PREFIX = "server.";

    @Nullable
    private String selectedShopId;
    private final LinkedHashSet<Integer> selectedSellInventorySlots = new LinkedHashSet<>();
    private final ArrayList<Integer> renderedSellInventorySlots = new ArrayList<>();
    @Nullable
    private String lastStatusMessage;

    public MghgFarmShopPage(@Nonnull PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismiss, EventPayload.CODEC);
    }

    public static @Nullable String openForPlayer(
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> playerEntityRef,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
    ) {
        if (MghgShopStockManager.getConfiguredItems().length == 0) {
            return "Shop config vacia.";
        }

        Player player = store.getComponent(playerEntityRef, Player.getComponentType());
        if (player == null) {
            return "No pude obtener el componente de jugador.";
        }

        player.getPageManager().openCustomPage(playerEntityRef, store, new MghgFarmShopPage(playerRef));
        return null;
    }

    public static void closeForPlayer(
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> playerEntityRef
    ) {
        Player player = store.getComponent(playerEntityRef, Player.getComponentType());
        if (player == null) {
            return;
        }
        CustomUIPage currentPage = player.getPageManager().getCustomPage();
        if (currentPage instanceof MghgFarmShopPage) {
            player.getPageManager().setPage(playerEntityRef, store, Page.None);
        }
    }

    public static void refreshForPlayer(
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> playerEntityRef
    ) {
        Player player = store.getComponent(playerEntityRef, Player.getComponentType());
        if (player == null) {
            return;
        }
        CustomUIPage currentPage = player.getPageManager().getCustomPage();
        if (currentPage instanceof MghgFarmShopPage shopPage) {
            shopPage.refresh(playerEntityRef, store);
        }
    }

    public static boolean refreshStatusForPlayer(
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> playerEntityRef
    ) {
        Player player = store.getComponent(playerEntityRef, Player.getComponentType());
        if (player == null) {
            return false;
        }
        CustomUIPage currentPage = player.getPageManager().getCustomPage();
        if (currentPage instanceof MghgFarmShopPage shopPage) {
            shopPage.refreshStatus(playerEntityRef, store);
            return true;
        }
        return false;
    }

    @Override
    public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder commandBuilder,
            @Nonnull UIEventBuilder eventBuilder,
            @Nonnull Store<EntityStore> store
    ) {
        commandBuilder.append(UI_PATH);
        render(ref, store, commandBuilder, eventBuilder);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull EventPayload data) {
        World world = store.getExternalData().getWorld();
        if (world == null) {
            playerRef.sendMessage(Message.raw("No pude resolver mundo para la shop."));
            close();
            return;
        }

        String action = normalize(data.action);
        if (ACTION_CLOSE.equalsIgnoreCase(action)) {
            close();
            return;
        }

        if (ACTION_SELECT_BUY_SLOT.equalsIgnoreCase(action)) {
            int clickedSlot = resolveClickedSlot(data);
            if (clickedSlot >= 0) {
                List<MghgShopConfig.ShopItem> validItems = resolveValidItems();
                if (clickedSlot < validItems.size()) {
                    selectedShopId = validItems.get(clickedSlot).getId();
                    selectedSellInventorySlots.clear();
                }
            } else if (data.shopId != null && !data.shopId.isBlank()) {
                selectedShopId = data.shopId;
                selectedSellInventorySlots.clear();
            }
            lastStatusMessage = null;
            refresh(ref, store);
            return;
        }
        if (ACTION_SELECT_SELL_SLOT.equalsIgnoreCase(action)) {
            int clickedSlot = resolveClickedSlot(data);
            if (clickedSlot >= 0 && clickedSlot < renderedSellInventorySlots.size()) {
                int inventorySlot = renderedSellInventorySlots.get(clickedSlot);
                if (selectedSellInventorySlots.contains(inventorySlot)) {
                    selectedSellInventorySlots.remove(inventorySlot);
                } else {
                    selectedSellInventorySlots.add(inventorySlot);
                }
            }
            lastStatusMessage = null;
            refresh(ref, store);
            return;
        }
        if (ACTION_SELECT_ALL_SELL.equalsIgnoreCase(action)) {
            selectedSellInventorySlots.clear();
            selectedSellInventorySlots.addAll(renderedSellInventorySlots);
            refresh(ref, store);
            return;
        }
        if (ACTION_UNSELECT_ALL_SELL.equalsIgnoreCase(action)) {
            selectedSellInventorySlots.clear();
            refresh(ref, store);
            return;
        }

        MghgShopTransactionResult result;
        if (ACTION_BUY.equalsIgnoreCase(action)) {
            result = MghgShopTransactionService.buy(store, ref, playerRef, world, selectedShopId, 1);
        } else if (ACTION_BUY_10.equalsIgnoreCase(action)) {
            result = MghgShopTransactionService.buy(store, ref, playerRef, world, selectedShopId, 10);
        } else if (ACTION_BUY_MAX.equalsIgnoreCase(action)) {
            result = MghgShopTransactionService.buyMax(store, ref, playerRef, world, selectedShopId);
        } else if (ACTION_SELL.equalsIgnoreCase(action)) {
            if (!selectedSellInventorySlots.isEmpty()) {
                int inventorySlot = selectedSellInventorySlots.iterator().next();
                result = MghgShopTransactionService.sellSlot(
                        store,
                        ref,
                        playerRef,
                        world,
                        selectedShopId,
                        inventorySlot
                );
            } else {
                result = MghgShopTransactionService.sell(store, ref, playerRef, world, selectedShopId, 1);
            }
        } else if (ACTION_SELL_SELECTED.equalsIgnoreCase(action)) {
            if (selectedSellInventorySlots.isEmpty()) {
                result = MghgShopTransactionResult.fail("No hay items seleccionados.");
            } else {
                result = MghgShopTransactionService.sellSlots(
                        store,
                        ref,
                        playerRef,
                        world,
                        selectedShopId,
                        selectedSellInventorySlots.stream().mapToInt(Integer::intValue).toArray()
                );
            }
        } else if (ACTION_SELL_ALL.equalsIgnoreCase(action)) {
            result = MghgShopTransactionService.sellAll(store, ref, playerRef, world, "all");
        } else {
            return;
        }

        playerRef.sendMessage(Message.raw(result.message()));
        lastStatusMessage = result.message();
        if (result.success() && (ACTION_SELL.equalsIgnoreCase(action)
                || ACTION_SELL_SELECTED.equalsIgnoreCase(action)
                || ACTION_SELL_ALL.equalsIgnoreCase(action))) {
            selectedSellInventorySlots.clear();
        }
        refresh(ref, store);
    }

    public void refresh(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        UICommandBuilder commandBuilder = new UICommandBuilder();
        UIEventBuilder eventBuilder = new UIEventBuilder();
        render(ref, store, commandBuilder, eventBuilder);
        sendUpdate(commandBuilder, eventBuilder, false);
    }

    public void refreshStatus(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        UICommandBuilder commandBuilder = new UICommandBuilder();
        renderStatusOnly(ref, store, commandBuilder);
        sendUpdate(commandBuilder, new UIEventBuilder(), false);
    }

    private void render(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            @Nonnull UICommandBuilder commandBuilder,
            @Nonnull UIEventBuilder eventBuilder
    ) {
        World world = store.getExternalData().getWorld();
        Player player = store.getComponent(ref, Player.getComponentType());
        if (world == null || player == null) {
            commandBuilder.set(ROOT + " #Status.Text", "Shop unavailable.");
            commandBuilder.set(ROOT + " #LastStatus.Text", "");
            commandBuilder.set(ROOT + " #SellPotential.Text", "Potential value: $0.00");
            clearDetail(commandBuilder);
            clearGridHosts(commandBuilder);
            return;
        }

        String accessError = MghgShopAccessPolicy.validateTransactionContext(store, ref, playerRef, world);
        if (accessError != null) {
            commandBuilder.set(ROOT + " #Status.Text", accessError);
            commandBuilder.set(ROOT + " #LastStatus.Text", "");
            commandBuilder.set(ROOT + " #SellPotential.Text", "Potential value: $0.00");
            clearDetail(commandBuilder);
            clearGridHosts(commandBuilder);
            bindStaticEvents(eventBuilder);
            return;
        }

        ItemContainer inventory = player.getInventory().getCombinedStorageFirst();
        List<MghgShopConfig.ShopItem> validItems = resolveValidItems();

        if (validItems.isEmpty()) {
            commandBuilder.set(ROOT + " #Status.Text", "Shop vacia.");
            commandBuilder.set(ROOT + " #LastStatus.Text", "");
            commandBuilder.set(ROOT + " #SellPotential.Text", "Potential value: $0.00");
            clearDetail(commandBuilder);
            clearGridHosts(commandBuilder);
            bindStaticEvents(eventBuilder);
            return;
        }

        if (selectedShopId == null || validItems.stream().noneMatch(i -> i.getId().equalsIgnoreCase(selectedShopId))) {
            selectedShopId = validItems.get(0).getId();
        }

        commandBuilder.set(ROOT + " #Status.Text", buildStatusText());
        commandBuilder.set(ROOT + " #LastStatus.Text", buildLastStatusText());

        int buyCount = renderBuyGrid(commandBuilder, eventBuilder, playerRef, inventory, validItems);

        MghgShopConfig.ShopItem selectedItem = validItems.stream()
                .filter(i -> i.getId().equalsIgnoreCase(selectedShopId))
                .findFirst()
                .orElse(validItems.get(0));
        selectedShopId = selectedItem.getId();
        commandBuilder.set(
                ROOT + " #SellPotential.Text",
                "Potential value: $" + formatMoney(computeSelectedPotentialValue(inventory, selectedItem))
        );
        renderDetail(commandBuilder, selectedItem, playerRef);
        int sellCount = renderSellGrid(commandBuilder, eventBuilder, inventory, selectedItem);
        if (buyCount <= 0) {
            commandBuilder.clear(ROOT + " #BuyGridSlots");
        }
        if (sellCount <= 0) {
            commandBuilder.clear(ROOT + " #SellGridSlots");
        }
        bindStaticEvents(eventBuilder);
    }

    private void renderStatusOnly(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            @Nonnull UICommandBuilder commandBuilder
    ) {
        World world = store.getExternalData().getWorld();
        Player player = store.getComponent(ref, Player.getComponentType());
        if (world == null || player == null) {
            return;
        }
        String accessError = MghgShopAccessPolicy.validateTransactionContext(store, ref, playerRef, world);
        if (accessError != null) {
            commandBuilder.set(ROOT + " #Status.Text", accessError);
            commandBuilder.set(ROOT + " #LastStatus.Text", "");
            return;
        }
        List<MghgShopConfig.ShopItem> validItems = resolveValidItems();
        if (validItems.isEmpty()) {
            commandBuilder.set(ROOT + " #Status.Text", "Shop vacia.");
            commandBuilder.set(ROOT + " #LastStatus.Text", "");
            return;
        }
        commandBuilder.set(ROOT + " #Status.Text", buildStatusText());
        commandBuilder.set(ROOT + " #LastStatus.Text", buildLastStatusText());
        MghgShopConfig.ShopItem selectedItem = resolveSelectedItem(validItems);
        if (selectedItem == null) {
            return;
        }
        ItemContainer inventory = player.getInventory().getCombinedStorageFirst();
        commandBuilder.set(
                ROOT + " #SellPotential.Text",
                "Potential value: $" + formatMoney(computeSelectedPotentialValue(inventory, selectedItem))
        );
    }

    private @Nullable MghgShopConfig.ShopItem resolveSelectedItem(@Nonnull List<MghgShopConfig.ShopItem> validItems) {
        if (validItems.isEmpty()) {
            return null;
        }
        if (selectedShopId == null || validItems.stream().noneMatch(i -> i.getId().equalsIgnoreCase(selectedShopId))) {
            selectedShopId = validItems.get(0).getId();
        }
        for (MghgShopConfig.ShopItem item : validItems) {
            if (item.getId().equalsIgnoreCase(selectedShopId)) {
                return item;
            }
        }
        selectedShopId = validItems.get(0).getId();
        return validItems.get(0);
    }

    private @Nonnull String buildStatusText() {
        double balance = MghgEconomyManager.getBalance(playerRef.getUuid());
        long remaining = MghgShopStockManager.getRemainingRestockSeconds();
        return String.format(
                Locale.ROOT,
                "Balance $%s. Restock in %s.",
                formatMoney(balance),
                formatDuration(remaining)
        );
    }

    private @Nonnull String buildLastStatusText() {
        if (lastStatusMessage == null || lastStatusMessage.isBlank()) {
            return "";
        }
        return compactStatus(lastStatusMessage);
    }

    private static void bindStaticEvents(@Nonnull UIEventBuilder eventBuilder) {
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, ROOT + " #Buy1", new EventData().append("Action", ACTION_BUY), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, ROOT + " #Buy10", new EventData().append("Action", ACTION_BUY_10), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, ROOT + " #BuyMax", new EventData().append("Action", ACTION_BUY_MAX), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, ROOT + " #SellSelectAll", new EventData().append("Action", ACTION_SELECT_ALL_SELL), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, ROOT + " #SellUnselectAll", new EventData().append("Action", ACTION_UNSELECT_ALL_SELL), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, ROOT + " #Sell1", new EventData().append("Action", ACTION_SELL), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, ROOT + " #SellSelected", new EventData().append("Action", ACTION_SELL_SELECTED), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, ROOT + " #SellAll", new EventData().append("Action", ACTION_SELL_ALL), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, ROOT + " #Close", new EventData().append("Action", ACTION_CLOSE), false);
    }

    private static List<MghgShopConfig.ShopItem> resolveValidItems() {
        MghgShopConfig.ShopItem[] configured = MghgShopStockManager.getConfiguredItems();
        ArrayList<MghgShopConfig.ShopItem> validItems = new ArrayList<>();
        for (MghgShopConfig.ShopItem item : configured) {
            if (item != null && item.getId() != null && !item.getId().isBlank()) {
                validItems.add(item);
            }
        }
        return validItems;
    }

    private double computeSelectedPotentialValue(
            @Nonnull ItemContainer inventory,
            @Nonnull MghgShopConfig.ShopItem item
    ) {
        if (selectedSellInventorySlots.isEmpty()) {
            return 0.0d;
        }
        double total = 0.0d;
        String[] sellIds = item.resolveSellItemIds();
        short capacity = inventory.getCapacity();
        for (int slot : selectedSellInventorySlots) {
            if (slot < 0 || slot >= capacity) {
                continue;
            }
            ItemStack stack = inventory.getItemStack((short) slot);
            if (ItemStack.isEmpty(stack) || !matchesSellItem(stack, sellIds)) {
                continue;
            }
            int quantity = Math.max(0, stack.getQuantity());
            if (quantity <= 0) {
                continue;
            }
            MghgCropMeta meta = stack.getFromMetadataOrNull(MghgCropMeta.KEY);
            double unit = MghgShopPricing.computeUnitSellPrice(item, meta);
            total += unit * quantity;
        }
        return total;
    }

    private int renderBuyGrid(
            @Nonnull UICommandBuilder commandBuilder,
            @Nonnull UIEventBuilder eventBuilder,
            @Nonnull PlayerRef playerRef,
            @Nonnull ItemContainer inventory,
            @Nonnull List<MghgShopConfig.ShopItem> validItems
    ) {
        commandBuilder.clear(ROOT + " #BuyGridSlots");
        int count = Math.min(MAX_BUY_GRID_SLOTS, validItems.size());
        int rowIndex = -1;
        for (int i = 0; i < count; i++) {
            MghgShopConfig.ShopItem item = validItems.get(i);
            String buyItemId = item.resolveBuyItemId();
            int personalStock = MghgShopStockManager.getPlayerStock(playerRef.getUuid(), item.getId());
            int shownQty = Math.max(1, Math.min(9999, personalStock));
            boolean selected = selectedShopId != null && selectedShopId.equalsIgnoreCase(item.getId());
            String displayName = resolveItemDisplayName(buyItemId, playerRef);

            int col = i % GRID_SLOTS_PER_ROW;
            if (col == 0) {
                rowIndex++;
                commandBuilder.append(ROOT + " #BuyGridSlots", ROW_TEMPLATE_PATH);
            }

            String rowSelector = ROOT + " #BuyGridSlots[" + rowIndex + "] #RowSlots";
            String slotSelector = rowSelector + "[" + col + "]";
            commandBuilder.append(rowSelector, SLOT_TEMPLATE_PATH);

            commandBuilder.set(slotSelector + " #Item.ItemId", safeDisplayItemId(buyItemId));
            commandBuilder.set(slotSelector + " #Qty.Text", shownQty > 1 ? Integer.toString(shownQty) : "");
            commandBuilder.set(slotSelector + " #SelectedFill.Visible", selected);
            commandBuilder.set(slotSelector + " #SelectedTop.Visible", selected);
            commandBuilder.set(slotSelector + " #SelectedBottom.Visible", selected);
            commandBuilder.set(slotSelector + " #SelectedLeft.Visible", selected);
            commandBuilder.set(slotSelector + " #SelectedRight.Visible", selected);
            commandBuilder.set(slotSelector + " #Button.Disabled", personalStock <= 0);
            commandBuilder.set(
                    slotSelector + " #Button.TooltipTextSpans",
                    buildBuySlotDescription(playerRef, inventory, item, displayName)
            );

            eventBuilder.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    slotSelector + " #Button",
                    new EventData()
                            .append("Action", ACTION_SELECT_BUY_SLOT)
                            .append("Slot", Integer.toString(i))
                            .append("ShopId", item.getId()),
                    false
            );
        }
        return count;
    }

    private static Message buildBuySlotDescription(
            @Nonnull PlayerRef playerRef,
            @Nonnull ItemContainer inventory,
            @Nonnull MghgShopConfig.ShopItem item,
            @Nonnull String displayName
    ) {
        int personal = MghgShopStockManager.getPlayerStock(playerRef.getUuid(), item.getId());
        int sellable = MghgShopTransactionService.countSellable(inventory, item.resolveSellItemIds());
        Message root = Message.empty();
        root.insert(Message.raw(displayName).bold(true).color("#e7f3ff"));
        root.insert("\n");
        root.insert("\n");
        root.insert(Message.raw("Buy price: ").color("#f2d896"));
        root.insert(Message.raw("$" + formatMoney(item.getBuyPrice())).bold(true).color("#ffffff"));
        root.insert("\n");
        root.insert(Message.raw("Cycle stock: ").color("#9fb6d1"));
        root.insert(Message.raw(Integer.toString(personal)).color("#d7e5f7"));
        root.insert("\n");
        root.insert(Message.raw("Sellable in inventory: ").color("#9fb6d1"));
        root.insert(Message.raw(Integer.toString(sellable)).color("#d7e5f7"));
        return root;
    }

    private int renderSellGrid(
            @Nonnull UICommandBuilder commandBuilder,
            @Nonnull UIEventBuilder eventBuilder,
            @Nonnull ItemContainer inventory,
            @Nonnull MghgShopConfig.ShopItem item
    ) {
        commandBuilder.clear(ROOT + " #SellGridSlots");
        renderedSellInventorySlots.clear();
        short capacity = inventory.getCapacity();
        String[] sellIds = item.resolveSellItemIds();

        int count = 0;
        int rowIndex = -1;
        for (short invSlot = 0; invSlot < capacity && count < MAX_SELL_GRID_SLOTS; invSlot++) {
            ItemStack stack = inventory.getItemStack(invSlot);
            if (ItemStack.isEmpty(stack)) {
                continue;
            }
            if (!matchesSellItem(stack, sellIds)) {
                continue;
            }

            int qty = Math.max(0, stack.getQuantity());
            if (qty <= 0) {
                continue;
            }

            int gridIndex = count;
            count++;
            int col = gridIndex % GRID_SLOTS_PER_ROW;
            if (col == 0) {
                rowIndex++;
                commandBuilder.append(ROOT + " #SellGridSlots", ROW_TEMPLATE_PATH);
            }

            renderedSellInventorySlots.add((int) invSlot);
            MghgCropMeta meta = stack.getFromMetadataOrNull(MghgCropMeta.KEY);
            double unit = MghgShopPricing.computeUnitSellPrice(item, meta);
            double total = unit * qty;
            double sizeMultiplier = meta == null ? 1.0d : MghgShopPricing.computeSizeMultiplier(item, meta.getSize());
            boolean selected = selectedSellInventorySlots.contains((int) invSlot);

            String rowSelector = ROOT + " #SellGridSlots[" + rowIndex + "] #RowSlots";
            String slotSelector = rowSelector + "[" + col + "]";
            commandBuilder.append(rowSelector, SLOT_TEMPLATE_PATH);

            String itemName = resolveItemDisplayName(safeDisplayItemId(stack.getItemId()), playerRef);
            commandBuilder.set(slotSelector + " #Item.ItemId", safeDisplayItemId(stack.getItemId()));
            commandBuilder.set(slotSelector + " #Qty.Text", qty > 1 ? Integer.toString(qty) : "");
            commandBuilder.set(slotSelector + " #SelectedFill.Visible", selected);
            commandBuilder.set(slotSelector + " #SelectedTop.Visible", selected);
            commandBuilder.set(slotSelector + " #SelectedBottom.Visible", selected);
            commandBuilder.set(slotSelector + " #SelectedLeft.Visible", selected);
            commandBuilder.set(slotSelector + " #SelectedRight.Visible", selected);
            commandBuilder.set(
                    slotSelector + " #Button.TooltipTextSpans",
                    buildSellSlotDescription(playerRef, item, meta, itemName, qty, unit, total, sizeMultiplier)
            );

            eventBuilder.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    slotSelector + " #Button",
                    new EventData()
                            .append("Action", ACTION_SELECT_SELL_SLOT)
                            .append("Slot", Integer.toString(gridIndex)),
                    false
            );
        }

        selectedSellInventorySlots.retainAll(new TreeSet<>(renderedSellInventorySlots));
        return count;
    }

    private static Message buildSellSlotDescription(
            @Nullable PlayerRef playerRef,
            @Nonnull MghgShopConfig.ShopItem item,
            @Nullable MghgCropMeta meta,
            @Nonnull String itemName,
            int quantity,
            double unitPrice,
            double totalPrice,
            double sizeMultiplier
    ) {
        Message root = Message.empty();
        root.insert(Message.raw(itemName + " x" + Math.max(1, quantity)).bold(true).color("#f6fbff"));
        root.insert("\n");
        root.insert("\n");
        root.insert(Message.raw("Unit value: ").color("#f2d896"));
        root.insert(Message.raw("$" + formatMoney(unitPrice)).bold(true).color("#ffffff"));
        root.insert("\n");
        root.insert(Message.raw("Stack value: ").color("#f2d896"));
        root.insert(Message.raw("$" + formatMoney(totalPrice)).bold(true).color("#ffffff"));
        if (meta == null) {
            root.insert("\n");
            root.insert("\n");
            root.insert(Message.raw("No crop metadata.").color("#9fb6d1"));
            return root;
        }

        double base = Math.max(0.0d, item.getSellPrice());
        String climateKey = meta.getClimate();
        String lunarKey = meta.getLunar();
        String rarityKey = meta.getRarity();

        double climate = sanitizeMultiplierValue(item.getClimateMultiplier(climateKey));
        double lunar = sanitizeMultiplierValue(item.getLunarMultiplier(lunarKey));
        double rarity = sanitizeMultiplierValue(item.getRarityMultiplier(rarityKey));
        String climateName = resolveMutationDisplay(playerRef, climateKey, "climate");
        String lunarName = resolveMutationDisplay(playerRef, lunarKey, "lunar");
        String rarityName = resolveMutationDisplay(playerRef, rarityKey, "rarity");
        String climateNameColor = colorOrDefault(MghgMutationUiPalette.colorForClimate(climateKey), "#d7e5f7");
        String lunarNameColor = colorOrDefault(MghgMutationUiPalette.colorForLunar(lunarKey), "#d7e5f7");
        String rarityNameColor = colorOrDefault(MghgMutationUiPalette.colorForRarity(rarityKey), "#d7e5f7");

        root.insert("\n");
        root.insert("\n");
        root.insert(Message.raw("Multipliers").bold(true).color("#f2d896"));
        root.insert("\n");
        root.insert(Message.raw("Size: ").color("#9fb6d1"));
        root.insert(Message.raw(Integer.toString(meta.getSize()) + " ").color("#d7e5f7"));
        root.insert(Message.raw("(x" + formatMoney(sizeMultiplier) + ")").color(MghgMutationUiPalette.colorForMultiplier(sizeMultiplier)));
        root.insert("\n");
        root.insert(Message.raw("Climate: ").color("#9fb6d1"));
        root.insert(Message.raw(climateName + " ").color(climateNameColor));
        root.insert(Message.raw("(x" + formatMoney(climate) + ")").color(MghgMutationUiPalette.colorForMultiplier(climate)));
        root.insert("\n");
        root.insert(Message.raw("Lunar: ").color("#9fb6d1"));
        root.insert(Message.raw(lunarName + " ").color(lunarNameColor));
        root.insert(Message.raw("(x" + formatMoney(lunar) + ")").color(MghgMutationUiPalette.colorForMultiplier(lunar)));
        root.insert("\n");
        root.insert(Message.raw("Rarity: ").color("#9fb6d1"));
        root.insert(Message.raw(rarityName + " ").color(rarityNameColor));
        root.insert(Message.raw("(x" + formatMoney(rarity) + ")").color(MghgMutationUiPalette.colorForMultiplier(rarity)));
        root.insert("\n");
        root.insert("\n");
        root.insert(Message.raw("Formula").bold(true).color("#f2d896"));
        root.insert("\n");
        root.insert(Message.raw("Base x Size x Climate x Lunar x Rarity").color("#9fb6d1"));
        root.insert("\n");
        root.insert(Message.raw(
                "$" + formatMoney(base)
                        + " x " + formatMoney(sizeMultiplier)
                        + " x " + formatMoney(climate)
                        + " x " + formatMoney(lunar)
                        + " x " + formatMoney(rarity)
        ).color("#d7e5f7"));
        return root;
    }

    private static String colorOrDefault(@Nullable String color, @Nonnull String fallback) {
        if (color == null || color.isBlank()) {
            return fallback;
        }
        return color;
    }

    private static double sanitizeMultiplierValue(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 1.0d;
        }
        return Math.max(0.0d, value);
    }

    private static void renderDetail(
            @Nonnull UICommandBuilder commandBuilder,
            @Nonnull MghgShopConfig.ShopItem item,
            @Nullable PlayerRef playerRef
    ) {
        String[] sellIds = item.resolveSellItemIds();
        String[] sellNames = new String[sellIds.length];
        for (int i = 0; i < sellIds.length; i++) {
            sellNames[i] = resolveItemDisplayName(sellIds[i], playerRef);
        }
        String sellText = sellNames.length == 0 ? "-" : String.join(", ", sellNames);
        if (sellText.length() > 120) {
            sellText = sellText.substring(0, 120) + "...";
        }

        commandBuilder.set(ROOT + " #DetailTitle.Text", "Pricing details: " + resolveItemDisplayName(item.resolveBuyItemId(), playerRef));
        commandBuilder.set(
                ROOT + " #DetailLine1.Text",
                String.format(
                        Locale.ROOT,
                        "Buy item: %s  |  Price: $%s",
                        resolveItemDisplayName(safeText(item.resolveBuyItemId(), "-"), playerRef),
                        formatMoney(item.getBuyPrice())
                )
        );
        commandBuilder.set(ROOT + " #DetailLine2.Text", "Sell targets: " + sellText);
        commandBuilder.set(
                ROOT + " #DetailLine3.Text",
                String.format(
                        Locale.ROOT,
                        "Base sell: $%s  |  Meta pricing: %s",
                        formatMoney(item.getSellPrice()),
                        item.isEnableMetaSellPricing() ? "ON" : "OFF"
                )
        );
        commandBuilder.set(
                ROOT + " #DetailLine4.Text",
                "Formula: Unit = Base x Size x Climate x Lunar x Rarity"
        );
        commandBuilder.set(
                ROOT + " #DetailLine5.Text",
                String.format(
                        Locale.ROOT,
                        "Size x curve: size %.0f -> x%s, size %.0f -> x%s",
                        item.getSellSizeMultiplierMinSize(),
                        formatMoney(item.getSellSizeMultiplierAtMin()),
                        item.getSellSizeMultiplierMaxSize(),
                        formatMoney(item.getSellSizeMultiplierAtMax())
                )
        );
        commandBuilder.set(
                ROOT + " #DetailLine6.Text",
                "Hover sell items to view exact multipliers and final unit value."
        );
    }

    private static void clearDetail(@Nonnull UICommandBuilder commandBuilder) {
        commandBuilder.set(ROOT + " #DetailTitle.Text", "Item Details");
        commandBuilder.set(ROOT + " #DetailLine1.Text", "");
        commandBuilder.set(ROOT + " #DetailLine2.Text", "");
        commandBuilder.set(ROOT + " #DetailLine3.Text", "");
        commandBuilder.set(ROOT + " #DetailLine4.Text", "");
        commandBuilder.set(ROOT + " #DetailLine5.Text", "");
        commandBuilder.set(ROOT + " #DetailLine6.Text", "");
    }

    private static void clearGridHosts(@Nonnull UICommandBuilder commandBuilder) {
        commandBuilder.clear(ROOT + " #BuyGridSlots");
        commandBuilder.clear(ROOT + " #SellGridSlots");
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
            return raw.charAt(0) == '*' ? (idx > 1 ? raw.substring(1, idx) : null) : raw.substring(0, idx);
        }
        return raw.charAt(0) == '*' ? raw.substring(1) : raw;
    }

    private static String safeDisplayItemId(@Nullable String itemId) {
        String normalized = normalizeItemId(itemId);
        return normalized == null ? "unknown" : normalized;
    }

    private static String resolveMutationDisplay(
            @Nullable PlayerRef playerRef,
            @Nullable String value,
            @Nonnull String domain
    ) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank() || "none".equals(normalized)) {
            String translatedNone = translateKey(playerRef, "mghg.hud.crop.mutations.none");
            if (!translatedNone.equals("mghg.hud.crop.mutations.none")
                    && !translatedNone.equals(LANG_PREFIX + "mghg.hud.crop.mutations.none")) {
                return translatedNone;
            }
            return "Normal";
        }
        String key = switch (domain.toLowerCase(Locale.ROOT)) {
            case "climate" -> "mghg.hud.climate." + normalized;
            case "lunar" -> "mghg.hud.lunar." + normalized;
            case "rarity" -> "mghg.hud.rarity." + normalized;
            default -> null;
        };
        if (key != null) {
            String translated = translateKey(playerRef, key);
            if (!translated.equals(key) && !translated.equals(LANG_PREFIX + key)) {
                return translated;
            }
        }
        return toDisplayName(value);
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

    private static String trimSuffixToken(@Nonnull String input, @Nonnull String suffix) {
        if (input.length() <= suffix.length()) {
            return input;
        }
        if (input.toLowerCase(Locale.ROOT).endsWith(suffix.toLowerCase(Locale.ROOT))) {
            return input.substring(0, input.length() - suffix.length());
        }
        return input;
    }

    private static String formatMoney(double value) {
        return String.format(Locale.ROOT, "%.2f", Math.max(0.0d, value));
    }

    private static int parseSlot(@Nullable String input) {
        if (input == null || input.isBlank()) {
            return -1;
        }
        try {
            return Integer.parseInt(input.trim());
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static int resolveClickedSlot(@Nonnull EventPayload data) {
        if (data.slotIndex != null && data.slotIndex >= 0) {
            return data.slotIndex;
        }
        if (data.index != null && data.index >= 0) {
            return data.index;
        }
        return parseSlot(data.slot);
    }

    private static String compactStatus(@Nonnull String input) {
        String value = input.trim().replace('\n', ' ');
        if (value.length() <= 96) {
            return value;
        }
        return value.substring(0, 96) + "...";
    }

    private static String normalize(@Nullable String value) {
        return value == null ? "" : value.trim();
    }

    private static String safeText(@Nullable String value, @Nonnull String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private static String formatDuration(long seconds) {
        if (seconds <= 0L) {
            return "0s";
        }
        long h = seconds / 3600L;
        long m = (seconds % 3600L) / 60L;
        long s = seconds % 60L;
        if (h > 0L) {
            return String.format(Locale.ROOT, "%dh %dm %ds", h, m, s);
        }
        if (m > 0L) {
            return String.format(Locale.ROOT, "%dm %ds", m, s);
        }
        return String.format(Locale.ROOT, "%ds", s);
    }

    public static final class EventPayload {
        public static final BuilderCodec<EventPayload> CODEC = BuilderCodec.builder(EventPayload.class, EventPayload::new)
                .append(new KeyedCodec<>("Action", Codec.STRING), (e, v) -> e.action = v, e -> e.action)
                .add()
                .append(new KeyedCodec<>("ShopId", Codec.STRING, true), (e, v) -> e.shopId = v, e -> e.shopId)
                .add()
                .append(new KeyedCodec<>("Slot", Codec.STRING, true), (e, v) -> e.slot = v, e -> e.slot)
                .add()
                .append(new KeyedCodec<>("SlotIndex", Codec.INTEGER, true), (e, v) -> e.slotIndex = v, e -> e.slotIndex)
                .add()
                .append(new KeyedCodec<>("Index", Codec.INTEGER, true), (e, v) -> e.index = v, e -> e.index)
                .add()
                .build();

        private String action;
        private String shopId;
        private String slot;
        private Integer slotIndex;
        private Integer index;
    }
}
