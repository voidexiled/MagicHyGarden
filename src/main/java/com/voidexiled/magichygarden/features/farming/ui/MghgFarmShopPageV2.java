package com.voidexiled.magichygarden.features.farming.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.voidexiled.magichygarden.features.farming.economy.MghgEconomyManager;
import com.voidexiled.magichygarden.features.farming.items.MghgCropMeta;
import com.voidexiled.magichygarden.features.farming.perks.MghgFarmPerkManager;
import com.voidexiled.magichygarden.features.farming.shop.MghgShopAccessPolicy;
import com.voidexiled.magichygarden.features.farming.shop.MghgShopConfig;
import com.voidexiled.magichygarden.features.farming.shop.MghgShopPricing;
import com.voidexiled.magichygarden.features.farming.shop.MghgShopStockManager;
import com.voidexiled.magichygarden.features.farming.shop.MghgShopTransactionResult;
import com.voidexiled.magichygarden.features.farming.shop.MghgShopTransactionService;
import com.voidexiled.magichygarden.features.farming.tooltips.MghgDynamicTooltipsManager;
import com.voidexiled.magichygarden.utils.chat.MghgChat;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class MghgFarmShopPageV2 extends InteractiveCustomUIPage<MghgFarmShopPageV2.EventPayload> {
    private static final String UI_PATH = "Pages/Mghg_FarmShopPage_V2.ui";
    private static final String CARD_TEMPLATE_PATH = "Pages/Mghg_FarmShopTradeCard_V2.ui";
    private static final String SELL_SLOT_TEMPLATE_PATH = "Pages/Mghg_FarmShopSellSlot_V2.ui";
    private static final String ROOT = "#MghgFarmShopPageV2";
    private static final String LANG_PREFIX = "server.";

    private static final String ACTION_TAB_BUY = "TabBuy";
    private static final String ACTION_TAB_SELL = "TabSell";
    private static final String ACTION_BUY_CARD = "BuyCard";
    private static final String ACTION_BUY_CARD_MAX = "BuyCardMax";
    private static final String ACTION_SELL_SELECT_ALL = "SellSelectAll";
    private static final String ACTION_SELL_TOGGLE_SELECTION = "SellToggleSelection";
    private static final String ACTION_SELL_TOGGLE_SELECTION_GROUP = "SellToggleSelectionGroup";
    private static final String ACTION_SELL_SELECTED = "SellSelected";
    private static final String ACTION_SELL_CLEAR_SELECTION = "SellClearSelection";
    private static final String ACTION_SELL_SLOT = "SellSlot";
    private static final String ACTION_SELL_SHOP_ALL = "SellShopAll";

    private static final int MAX_CARDS = 96;
    private static final String BUY_HAVE_COLOR_OK = "#3d913f";
    private static final String BUY_HAVE_COLOR_MISSING = "#d95353";

    private ShopTab activeTab = ShopTab.BUY;
    private final ArrayList<SellEntry> renderedSellEntries = new ArrayList<>();
    private final Set<Integer> selectedSellInventorySlots = new HashSet<>();

    public MghgFarmShopPageV2(@Nonnull PlayerRef playerRef) {
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
        player.getPageManager().openCustomPage(playerEntityRef, store, new MghgFarmShopPageV2(playerRef));
        return null;
    }

    public static void closeForPlayer(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> playerEntityRef) {
        Player player = store.getComponent(playerEntityRef, Player.getComponentType());
        if (player == null) {
            return;
        }
        CustomUIPage currentPage = player.getPageManager().getCustomPage();
        if (currentPage instanceof MghgFarmShopPageV2) {
            player.getPageManager().setPage(playerEntityRef, store, Page.None);
        }
    }

    public static void refreshForPlayer(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> playerEntityRef) {
        Player player = store.getComponent(playerEntityRef, Player.getComponentType());
        if (player == null) {
            return;
        }
        CustomUIPage currentPage = player.getPageManager().getCustomPage();
        if (currentPage instanceof MghgFarmShopPageV2 page) {
            page.refresh(playerEntityRef, store);
        }
    }

    public static boolean refreshStatusForPlayer(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> playerEntityRef) {
        Player player = store.getComponent(playerEntityRef, Player.getComponentType());
        if (player == null) {
            return false;
        }
        CustomUIPage currentPage = player.getPageManager().getCustomPage();
        if (currentPage instanceof MghgFarmShopPageV2 page) {
            page.refreshStatus(playerEntityRef, store);
            return true;
        }
        return false;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder, @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store) {
        commandBuilder.append(UI_PATH);
        render(ref, store, commandBuilder, eventBuilder);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull EventPayload data) {
        World world = store.getExternalData().getWorld();
        if (world == null) {
            sendFeedback(false, "No pude resolver mundo para la shop.");
            close();
            return;
        }

        String action = normalize(data.action);
        if (ACTION_TAB_BUY.equalsIgnoreCase(action)) {
            activeTab = ShopTab.BUY;
            refresh(ref, store);
            return;
        }
        if (ACTION_TAB_SELL.equalsIgnoreCase(action)) {
            activeTab = ShopTab.SELL;
            refresh(ref, store);
            return;
        }

        List<MghgShopConfig.ShopItem> validItems = resolveValidItems();
        if (validItems.isEmpty()) {
            sendFeedback(false, tr("mghg.shop.v2.footer.empty", "No offers available."));
            refresh(ref, store);
            return;
        }
        if (ACTION_BUY_CARD.equalsIgnoreCase(action) || ACTION_BUY_CARD_MAX.equalsIgnoreCase(action)) {
            String shopId = resolveShopId(data, validItems);
            if (shopId == null || shopId.isBlank()) {
                sendFeedback(false, tr("mghg.shop.ui.status.empty", "Shop empty."));
                refresh(ref, store);
                return;
            }

            boolean buyMax = ACTION_BUY_CARD_MAX.equalsIgnoreCase(action);
            MghgShopTransactionResult result = buyMax
                    ? MghgShopTransactionService.buyMax(store, ref, playerRef, world, shopId)
                    : MghgShopTransactionService.buy(store, ref, playerRef, world, shopId, 1);
            sendFeedback(result.success(), result.message());
            refresh(ref, store);
            return;
        }

        double sellMultiplier = MghgFarmPerkManager.resolveSellMultiplierForContext(playerRef.getUuid(), world);

        if (ACTION_SELL_TOGGLE_SELECTION.equalsIgnoreCase(action) || ACTION_SELL_TOGGLE_SELECTION_GROUP.equalsIgnoreCase(action)) {
            SellEntry entry = resolveSellEntry(data);
            if (entry != null) {
                boolean groupToggle = ACTION_SELL_TOGGLE_SELECTION_GROUP.equalsIgnoreCase(action);
                if (groupToggle) {
                    Player player = store.getComponent(ref, Player.getComponentType());
                    if (player != null) {
                        ItemContainer inventory = player.getInventory().getCombinedStorageFirst();
                        List<SellEntry> currentEntries = collectSellEntries(inventory, validItems, sellMultiplier);
                        sanitizeSelectedEntries(currentEntries);

                        boolean clickedSelected = selectedSellInventorySlots.contains(entry.inventorySlot());
                        for (SellEntry current : currentEntries) {
                            if (!entry.shopId().equalsIgnoreCase(current.shopId())) {
                                continue;
                            }
                            if (clickedSelected) {
                                selectedSellInventorySlots.remove(current.inventorySlot());
                            } else {
                                selectedSellInventorySlots.add(current.inventorySlot());
                            }
                        }
                    }
                } else {
                    int inventorySlot = entry.inventorySlot();
                    if (!selectedSellInventorySlots.remove(inventorySlot)) {
                        selectedSellInventorySlots.add(inventorySlot);
                    }
                }
            }
            refresh(ref, store);
            return;
        }

        if (ACTION_SELL_SELECT_ALL.equalsIgnoreCase(action)) {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) {
                refresh(ref, store);
                return;
            }

            ItemContainer inventory = player.getInventory().getCombinedStorageFirst();
            List<SellEntry> currentEntries = collectSellEntries(inventory, validItems, sellMultiplier);
            selectedSellInventorySlots.clear();
            for (SellEntry entry : currentEntries) {
                selectedSellInventorySlots.add(entry.inventorySlot());
            }
            refresh(ref, store);
            return;
        }

        if (ACTION_SELL_CLEAR_SELECTION.equalsIgnoreCase(action)) {
            selectedSellInventorySlots.clear();
            refresh(ref, store);
            return;
        }

        if (ACTION_SELL_SELECTED.equalsIgnoreCase(action)) {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) {
                sendFeedback(false, tr("mghg.shop.v2.footer.unavailable", "Shop unavailable."));
                refresh(ref, store);
                return;
            }

            ItemContainer inventory = player.getInventory().getCombinedStorageFirst();
            List<SellEntry> currentEntries = collectSellEntries(inventory, validItems, sellMultiplier);
            sanitizeSelectedEntries(currentEntries);
            if (selectedSellInventorySlots.isEmpty()) {
                sendFeedback(false, tr("mghg.shop.v2.sell.selection.empty", "Select at least one stack to sell."));
                refresh(ref, store);
                return;
            }

            int soldStacks = 0;
            int failedStacks = 0;
            double soldValue = 0.0d;

            for (SellEntry entry : currentEntries) {
                if (!selectedSellInventorySlots.contains(entry.inventorySlot())) {
                    continue;
                }
                MghgShopTransactionResult result = MghgShopTransactionService.sellSlot(
                        store,
                        ref,
                        playerRef,
                        world,
                        entry.shopId(),
                        entry.inventorySlot()
                );
                if (result.success()) {
                    soldStacks++;
                    soldValue += entry.totalPrice();
                    selectedSellInventorySlots.remove(entry.inventorySlot());
                } else {
                    failedStacks++;
                }
            }

            if (soldStacks > 0) {
                sendFeedback(
                        true,
                        tf(
                                "mghg.shop.v2.sell.selection.sold",
                                "Sold %d selected stacks for %s.",
                                soldStacks,
                                formatMoney(soldValue)
                        )
                );
            }
            if (failedStacks > 0) {
                sendFeedback(
                        false,
                        tf(
                                "mghg.shop.v2.sell.selection.partial",
                                "%d selected stacks could not be sold.",
                                failedStacks
                        )
                );
            }

            refresh(ref, store);
            return;
        }

        if (ACTION_SELL_SLOT.equalsIgnoreCase(action) || ACTION_SELL_SHOP_ALL.equalsIgnoreCase(action)) {
            SellEntry entry = resolveSellEntry(data);
            if (entry == null) {
                sendFeedback(false, tr("mghg.shop.v2.sell.footer.empty", "No sellable crops in inventory."));
                refresh(ref, store);
                return;
            }

            boolean sellAllForShop = ACTION_SELL_SHOP_ALL.equalsIgnoreCase(action);
            MghgShopTransactionResult result = sellAllForShop
                    ? MghgShopTransactionService.sellAll(store, ref, playerRef, world, entry.shopId())
                    : MghgShopTransactionService.sellSlot(store, ref, playerRef, world, entry.shopId(), entry.inventorySlot());
            sendFeedback(result.success(), result.message());
            if (result.success()) {
                if (sellAllForShop) {
                    clearSelectedEntriesByShop(entry.shopId());
                } else {
                    selectedSellInventorySlots.remove(entry.inventorySlot());
                }
            }
            refresh(ref, store);
        }
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

    private void render(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull UICommandBuilder commandBuilder, @Nonnull UIEventBuilder eventBuilder) {
        applyLocalizedUiText(commandBuilder);
        applyTabState(commandBuilder);
        bindStaticEvents(eventBuilder);

        World world = store.getExternalData().getWorld();
        Player player = store.getComponent(ref, Player.getComponentType());
        if (world == null || player == null) {
            setUnavailableState(commandBuilder, tr("mghg.shop.v2.footer.unavailable", "Shop unavailable."));
            return;
        }

        String accessError = MghgShopAccessPolicy.validateTransactionContext(store, ref, playerRef, world);
        if (accessError != null) {
            setUnavailableState(commandBuilder, accessError);
            return;
        }

        List<MghgShopConfig.ShopItem> validItems = resolveValidItems();
        if (validItems.isEmpty()) {
            setUnavailableState(commandBuilder, tr("mghg.shop.v2.footer.empty", "No offers available."));
            return;
        }

        ItemContainer inventory = player.getInventory().getCombinedStorageFirst();
        if (activeTab == ShopTab.BUY) {
            commandBuilder.set(ROOT + " #FooterTimer.Text", buildRestockText());
            commandBuilder.set(ROOT + " #SellFooterLabel.Text", "");
            commandBuilder.clear(ROOT + " #SellTradeGrid");
            renderBuyCards(commandBuilder, eventBuilder, validItems);
            return;
        }

        commandBuilder.set(ROOT + " #FooterTimer.Text", "");
        commandBuilder.clear(ROOT + " #BuyTradeGrid");
        double sellMultiplier = MghgFarmPerkManager.resolveSellMultiplierForContext(playerRef.getUuid(), world);
        renderSellCards(commandBuilder, eventBuilder, inventory, validItems, sellMultiplier);
    }

    private void renderStatusOnly(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull UICommandBuilder commandBuilder) {
        applyLocalizedUiText(commandBuilder);
        applyTabState(commandBuilder);

        World world = store.getExternalData().getWorld();
        Player player = store.getComponent(ref, Player.getComponentType());
        if (world == null || player == null) {
            return;
        }

        String accessError = MghgShopAccessPolicy.validateTransactionContext(store, ref, playerRef, world);
        if (accessError != null) {
            if (activeTab == ShopTab.BUY) {
                commandBuilder.set(ROOT + " #FooterTimer.Text", accessError);
            } else {
                commandBuilder.set(ROOT + " #SellFooterLabel.Text", accessError);
            }
            return;
        }

        List<MghgShopConfig.ShopItem> validItems = resolveValidItems();
        if (validItems.isEmpty()) {
            if (activeTab == ShopTab.BUY) {
                commandBuilder.set(ROOT + " #FooterTimer.Text", tr("mghg.shop.v2.footer.empty", "No offers available."));
            } else {
                commandBuilder.set(ROOT + " #SellFooterLabel.Text", tr("mghg.shop.v2.footer.empty", "No offers available."));
            }
            return;
        }

        if (activeTab == ShopTab.BUY) {
            commandBuilder.set(ROOT + " #FooterTimer.Text", buildRestockText());
            return;
        }

        ItemContainer inventory = player.getInventory().getCombinedStorageFirst();
        double sellMultiplier = MghgFarmPerkManager.resolveSellMultiplierForContext(playerRef.getUuid(), world);
        List<SellEntry> sellEntries = collectSellEntries(inventory, validItems, sellMultiplier);
        if (sellEntries.isEmpty()) {
            selectedSellInventorySlots.clear();
            commandBuilder.set(ROOT + " #SellFooterLabel.Text", tr("mghg.shop.v2.sell.footer.empty", "No sellable crops in inventory."));
            commandBuilder.set(ROOT + " #SellSelectAll.Disabled", true);
            commandBuilder.set(ROOT + " #SellSelected.Disabled", true);
            commandBuilder.set(ROOT + " #SellClearSelection.Disabled", true);
            return;
        }
        sanitizeSelectedEntries(sellEntries);
        updateSellFooter(commandBuilder, sellEntries, sellMultiplier);
        commandBuilder.set(ROOT + " #SellSelectAll.Disabled", false);
        boolean hasSelection = countSelectedEntries(sellEntries) > 0;
        commandBuilder.set(ROOT + " #SellSelected.Disabled", !hasSelection);
        commandBuilder.set(ROOT + " #SellClearSelection.Disabled", !hasSelection);
    }
    private void renderBuyCards(
            @Nonnull UICommandBuilder commandBuilder,
            @Nonnull UIEventBuilder eventBuilder,
            @Nonnull List<MghgShopConfig.ShopItem> validItems
    ) {
        commandBuilder.clear(ROOT + " #BuyTradeGrid");
        double balance = MghgEconomyManager.getBalance(playerRef.getUuid());
        int count = Math.min(MAX_CARDS, validItems.size());

        for (int i = 0; i < count; i++) {
            MghgShopConfig.ShopItem item = validItems.get(i);
            commandBuilder.append(ROOT + " #BuyTradeGrid", CARD_TEMPLATE_PATH);
            String selector = ROOT + " #BuyTradeGrid[" + i + "]";

            int stock = Math.max(0, MghgShopStockManager.getPlayerStock(playerRef.getUuid(), item.getId()));
            boolean outOfStock = stock <= 0;
            boolean affordable = balance + 0.00001d >= Math.max(0.0d, item.getBuyPrice());
            String costBorderColor = outOfStock ? "#6a2b2b" : (affordable ? "#2a5a3a" : "#7a5630");
            String haveColor = affordable ? BUY_HAVE_COLOR_OK : BUY_HAVE_COLOR_MISSING;

            commandBuilder.set(selector + " #OutputSlot.ItemId", safeDisplayItemId(item.resolveBuyItemId()));
            commandBuilder.set(selector + " #OutputSlot.Quantity", 1);
            commandBuilder.set(selector + " #OutputQuantity.Text", "");
            commandBuilder.set(selector + " #CostLabel.Text", tf("mghg.shop.v2.card.cost", "Cost:"));
            commandBuilder.set(selector + " #CostValue.Text", formatMoney(item.getBuyPrice()));
            commandBuilder.set(selector + " #HaveNeedLabel.Text", tf("mghg.shop.v2.card.have", "Have: %s", formatMoney(balance)));
            commandBuilder.set(selector + " #HaveNeedLabel.Style.TextColor", haveColor);
            commandBuilder.set(selector + " #Stock.Text", tf("mghg.shop.v2.card.stock", "Stock: %d", stock));
            commandBuilder.set(selector + " #CostBorder.Background", costBorderColor);
            commandBuilder.set(selector + " #OutOfStockOverlay.Visible", outOfStock);
            commandBuilder.set(selector + " #TradeButton.Disabled", outOfStock);

            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, selector + " #TradeButton", new EventData().append("Action", ACTION_BUY_CARD).append("Slot", Integer.toString(i)).append("ShopId", item.getId()), false);
            eventBuilder.addEventBinding(CustomUIEventBindingType.RightClicking, selector + " #TradeButton", new EventData().append("Action", ACTION_BUY_CARD_MAX).append("Slot", Integer.toString(i)).append("ShopId", item.getId()), false);
        }
    }

    private void renderSellCards(
            @Nonnull UICommandBuilder commandBuilder,
            @Nonnull UIEventBuilder eventBuilder,
            @Nonnull ItemContainer inventory,
            @Nonnull List<MghgShopConfig.ShopItem> validItems,
            double sellMultiplier
    ) {
        commandBuilder.clear(ROOT + " #SellTradeGrid");
        renderedSellEntries.clear();

        List<SellEntry> entries = collectSellEntries(inventory, validItems, sellMultiplier);
        if (entries.isEmpty()) {
            selectedSellInventorySlots.clear();
            commandBuilder.set(ROOT + " #SellFooterLabel.Text", tr("mghg.shop.v2.sell.footer.empty", "No sellable crops in inventory."));
            commandBuilder.set(ROOT + " #SellSelectAll.Disabled", true);
            commandBuilder.set(ROOT + " #SellSelected.Disabled", true);
            commandBuilder.set(ROOT + " #SellClearSelection.Disabled", true);
            return;
        }
        sanitizeSelectedEntries(entries);
        int count = Math.min(MAX_CARDS, entries.size());
        for (int i = 0; i < count; i++) {
            SellEntry entry = entries.get(i);
            renderedSellEntries.add(entry);
            boolean selected = selectedSellInventorySlots.contains(entry.inventorySlot());

            commandBuilder.append(ROOT + " #SellTradeGrid", SELL_SLOT_TEMPLATE_PATH);
            String selector = ROOT + " #SellTradeGrid[" + i + "]";

            commandBuilder.set(selector + " #ItemSlot.ItemId", resolveSellDisplayItemId(entry));
            commandBuilder.set(selector + " #ItemSlot.Quantity", 1);
            commandBuilder.set(selector + " #Qty.Text", entry.quantity() > 1 ? Integer.toString(entry.quantity()) : "");
            commandBuilder.set(selector + " #SellButton.Disabled", false);
            commandBuilder.set(selector + " #SelectedTop.Visible", selected);
            commandBuilder.set(selector + " #SelectedBottom.Visible", selected);
            commandBuilder.set(selector + " #SelectedLeft.Visible", selected);
            commandBuilder.set(selector + " #SelectedRight.Visible", selected);

            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, selector + " #SellButton", new EventData().append("Action", ACTION_SELL_TOGGLE_SELECTION).append("Slot", Integer.toString(i)).append("ShopId", entry.shopId()), false);
            eventBuilder.addEventBinding(CustomUIEventBindingType.RightClicking, selector + " #SellButton", new EventData().append("Action", ACTION_SELL_TOGGLE_SELECTION_GROUP).append("Slot", Integer.toString(i)).append("ShopId", entry.shopId()), false);
        }
        updateSellFooter(commandBuilder, entries, sellMultiplier);
        commandBuilder.set(ROOT + " #SellSelectAll.Disabled", false);
        boolean hasSelection = countSelectedEntries(entries) > 0;
        commandBuilder.set(ROOT + " #SellSelected.Disabled", !hasSelection);
        commandBuilder.set(ROOT + " #SellClearSelection.Disabled", !hasSelection);
    }

    private static @Nonnull String resolveSellDisplayItemId(@Nonnull SellEntry entry) {
        String packetItemId = null;
        String metadata = null;
        try {
            var packet = entry.stack().toPacket();
            if (packet != null) {
                packetItemId = packet.itemId;
                metadata = packet.metadata;
            }
        } catch (Throwable ignored) {
            // Optional DynamicTooltips integration; fallback keeps UI stable.
        }
        String fallback = safeSellDisplayItemId(packetItemId != null ? packetItemId : entry.itemId());

        String virtual = MghgDynamicTooltipsManager.resolveVirtualItemIdForUi(fallback, metadata);
        if (virtual == null || virtual.isBlank()) {
            return fallback;
        }
        return virtual;
    }

    private @Nonnull List<SellEntry> collectSellEntries(
            @Nonnull ItemContainer inventory,
            @Nonnull List<MghgShopConfig.ShopItem> validItems,
            double sellMultiplier
    ) {
        ArrayList<SellEntry> entries = new ArrayList<>();
        short capacity = inventory.getCapacity();
        for (short slot = 0; slot < capacity; slot++) {
            ItemStack stack = inventory.getItemStack(slot);
            if (ItemStack.isEmpty(stack)) {
                continue;
            }

            MghgShopConfig.ShopItem match = resolveSellMatch(validItems, stack);
            if (match == null || match.getId() == null || match.getId().isBlank()) {
                continue;
            }

            int quantity = Math.max(0, stack.getQuantity());
            if (quantity <= 0) {
                continue;
            }

            MghgCropMeta meta = stack.getFromMetadataOrNull(MghgCropMeta.KEY);
            double unit = MghgShopPricing.computeUnitSellPrice(match, meta) * sanitizeMultiplier(sellMultiplier);
            double total = unit * quantity;
            if (total <= 0.0d) {
                continue;
            }

            entries.add(new SellEntry(slot, match.getId(), stack.getItemId(), stack, quantity, unit, total, match, meta));
        }
        return entries;
    }

    private @Nullable MghgShopConfig.ShopItem resolveSellMatch(@Nonnull List<MghgShopConfig.ShopItem> validItems, @Nonnull ItemStack stack) {
        for (MghgShopConfig.ShopItem candidate : validItems) {
            if (candidate == null || candidate.getId() == null || candidate.getId().isBlank()) {
                continue;
            }
            if (candidate.getSellPrice() <= 0.0d) {
                continue;
            }
            if (matchesSellItem(stack, candidate.resolveSellItemIds())) {
                return candidate;
            }
        }
        return null;
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

    private @Nullable SellEntry resolveSellEntry(@Nonnull EventPayload payload) {
        int slot = resolveSlot(payload);
        if (slot < 0 || slot >= renderedSellEntries.size()) {
            return null;
        }
        return renderedSellEntries.get(slot);
    }

    private void sanitizeSelectedEntries(@Nonnull List<SellEntry> entries) {
        if (entries.isEmpty()) {
            selectedSellInventorySlots.clear();
            return;
        }
        HashSet<Integer> availableSlots = new HashSet<>();
        for (SellEntry entry : entries) {
            availableSlots.add(entry.inventorySlot());
        }
        selectedSellInventorySlots.retainAll(availableSlots);
    }

    private int countSelectedEntries(@Nonnull List<SellEntry> entries) {
        int count = 0;
        for (SellEntry entry : entries) {
            if (selectedSellInventorySlots.contains(entry.inventorySlot())) {
                count++;
            }
        }
        return count;
    }

    private double computePotentialValue(@Nonnull List<SellEntry> entries) {
        boolean useSelection = !selectedSellInventorySlots.isEmpty();
        double total = 0.0d;
        for (SellEntry entry : entries) {
            if (!useSelection || selectedSellInventorySlots.contains(entry.inventorySlot())) {
                total += entry.totalPrice();
            }
        }
        return total;
    }

    private void updateSellFooter(
            @Nonnull UICommandBuilder commandBuilder,
            @Nonnull List<SellEntry> entries,
            double sellMultiplier
    ) {
        if (entries.isEmpty()) {
            commandBuilder.set(ROOT + " #SellFooterLabel.Text", tr("mghg.shop.v2.sell.footer.empty", "No sellable crops in inventory."));
            return;
        }

        double sanitizedMultiplier = sanitizeMultiplier(sellMultiplier);
        String multiplierSuffix = sanitizedMultiplier > 1.0001d
                ? " | " + tf(
                "mghg.shop.v2.sell.footer.multiplier",
                "Sell multiplier: x%s",
                formatMultiplierValue(sanitizedMultiplier)
        )
                : "";

        int selectedCount = countSelectedEntries(entries);
        double potential = computePotentialValue(entries);
        if (selectedCount > 0) {
            commandBuilder.set(
                    ROOT + " #SellFooterLabel.Text",
                    tf(
                            "mghg.shop.v2.sell.footer.potential.selected",
                            "Potential value (selected %d): %s",
                            selectedCount,
                            formatMoney(potential)
                    ) + multiplierSuffix
            );
            return;
        }

        commandBuilder.set(
                ROOT + " #SellFooterLabel.Text",
                tf("mghg.shop.v2.sell.footer.potential.all", "Potential value (all): %s", formatMoney(potential))
                        + multiplierSuffix
        );
    }

    private void clearSelectedEntriesByShop(@Nonnull String shopId) {
        if (shopId.isBlank() || selectedSellInventorySlots.isEmpty() || renderedSellEntries.isEmpty()) {
            return;
        }
        HashSet<Integer> slotsToRemove = new HashSet<>();
        for (SellEntry entry : renderedSellEntries) {
            if (shopId.equalsIgnoreCase(entry.shopId())) {
                slotsToRemove.add(entry.inventorySlot());
            }
        }
        selectedSellInventorySlots.removeAll(slotsToRemove);
    }

    private static void bindStaticEvents(@Nonnull UIEventBuilder eventBuilder) {
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, ROOT + " #BuyTab", new EventData().append("Action", ACTION_TAB_BUY), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, ROOT + " #SellTab", new EventData().append("Action", ACTION_TAB_SELL), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, ROOT + " #SellSelectAll", new EventData().append("Action", ACTION_SELL_SELECT_ALL), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, ROOT + " #SellSelected", new EventData().append("Action", ACTION_SELL_SELECTED), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, ROOT + " #SellClearSelection", new EventData().append("Action", ACTION_SELL_CLEAR_SELECTION), false);
    }

    private void applyTabState(@Nonnull UICommandBuilder commandBuilder) {
        boolean buy = activeTab == ShopTab.BUY;
        commandBuilder.set(ROOT + " #BuyPanel.Visible", buy);
        commandBuilder.set(ROOT + " #SellPanel.Visible", !buy);
        commandBuilder.set(ROOT + " #ShopTabs.SelectedTab", buy ? "BuyTab" : "SellTab");
    }

    private void setUnavailableState(@Nonnull UICommandBuilder commandBuilder, @Nonnull String statusText) {
        commandBuilder.clear(ROOT + " #BuyTradeGrid");
        commandBuilder.clear(ROOT + " #SellTradeGrid");
        renderedSellEntries.clear();
        selectedSellInventorySlots.clear();
        commandBuilder.set(ROOT + " #SellSelectAll.Disabled", true);
        commandBuilder.set(ROOT + " #SellSelected.Disabled", true);
        commandBuilder.set(ROOT + " #SellClearSelection.Disabled", true);
        if (activeTab == ShopTab.BUY) {
            commandBuilder.set(ROOT + " #FooterTimer.Text", statusText);
            commandBuilder.set(ROOT + " #SellFooterLabel.Text", "");
        } else {
            commandBuilder.set(ROOT + " #FooterTimer.Text", "");
            commandBuilder.set(ROOT + " #SellFooterLabel.Text", statusText);
        }
    }

    private void applyLocalizedUiText(@Nonnull UICommandBuilder commandBuilder) {
        commandBuilder.set(ROOT + " #PageTitle.Text", tr("mghg.shop.v2.ui.title", "Magic HyGarden Shop"));
        commandBuilder.set(ROOT + " #BuyTab.TooltipText", tr("mghg.shop.v2.ui.tab.buy", "Buy"));
        commandBuilder.set(ROOT + " #SellTab.TooltipText", tr("mghg.shop.v2.ui.tab.sell", "Sell"));
        commandBuilder.set(ROOT + " #SellSelectAll.Text", tr("mghg.shop.v2.ui.button.select_all", "Select all"));
        commandBuilder.set(ROOT + " #SellSelected.Text", tr("mghg.shop.v2.ui.button.sell_selected", "Sell Selected"));
        commandBuilder.set(ROOT + " #SellClearSelection.Text", tr("mghg.shop.v2.ui.button.clear_selection", "Clear"));
    }

    private void sendFeedback(boolean success, @Nonnull String rawMessage) {
        String[] lines = safeText(rawMessage, tr("mghg.shop.chat.default", "Operation completed.")).replace('\r', '\n').split("\n");
        MghgChat.Channel channel = success ? MghgChat.Channel.SUCCESS : MghgChat.Channel.ERROR;
        boolean sent = false;

        for (String line : lines) {
            String clean = line == null ? "" : line.trim();
            if (clean.isEmpty()) {
                continue;
            }
            playerRef.sendMessage(MghgChat.format(channel, clean));
            sent = true;
        }

        if (!sent) {
            playerRef.sendMessage(MghgChat.format(channel, tr("mghg.shop.chat.default", "Operation completed.")));
        }
    }

    private @Nullable String resolveShopId(@Nonnull EventPayload payload, @Nonnull List<MghgShopConfig.ShopItem> validItems) {
        if (payload.shopId != null && !payload.shopId.isBlank()) {
            return payload.shopId;
        }
        int slot = resolveSlot(payload);
        if (slot < 0 || slot >= validItems.size()) {
            return null;
        }
        return validItems.get(slot).getId();
    }

    private @Nonnull String buildRestockText() {
        return tf("mghg.shop.v2.footer.restock", "Restocks in %s", formatDuration(MghgShopStockManager.getRemainingRestockSeconds()));
    }

    private static @Nonnull List<MghgShopConfig.ShopItem> resolveValidItems() {
        MghgShopConfig.ShopItem[] configured = MghgShopStockManager.getConfiguredItems();
        ArrayList<MghgShopConfig.ShopItem> validItems = new ArrayList<>();
        for (MghgShopConfig.ShopItem item : configured) {
            if (item != null && item.getId() != null && !item.getId().isBlank()) {
                validItems.add(item);
            }
        }
        return validItems;
    }

    private @Nonnull String tr(@Nonnull String key, @Nonnull String fallback) {
        String translated = translateKey(playerRef, key);
        if (translated.equals(key) || translated.equals(LANG_PREFIX + key) || translated.isBlank()) {
            return fallback;
        }
        return translated;
    }

    private @Nonnull String tf(@Nonnull String key, @Nonnull String fallback, Object... args) {
        String template = tr(key, fallback);
        try {
            return String.format(Locale.ROOT, template, args);
        } catch (Exception ignored) {
            return String.format(Locale.ROOT, fallback, args);
        }
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

    private static String safeDisplayItemId(@Nullable String itemId) {
        String normalized = normalizeItemId(itemId);
        return normalized == null ? "unknown" : normalized;
    }

    private static @Nonnull String safeSellDisplayItemId(@Nullable String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return "unknown";
        }
        String raw = itemId.trim();
        while (!raw.isEmpty() && raw.charAt(0) == '#') {
            raw = raw.substring(1);
        }
        return raw.isBlank() ? "unknown" : raw;
    }

    private static @Nullable String normalizeItemId(@Nullable String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return null;
        }

        String raw = itemId.trim();
        while (!raw.isEmpty() && (raw.charAt(0) == '*' || raw.charAt(0) == '#')) {
            raw = raw.substring(1);
        }
        if (raw.isBlank()) {
            return null;
        }

        int idx = raw.indexOf("_State_");
        if (idx > 0) {
            raw = raw.substring(0, idx);
        }

        return raw;
    }

    private static String normalize(@Nullable String value) {
        return value == null ? "" : value.trim();
    }

    private static int resolveSlot(@Nonnull EventPayload payload) {
        if (payload.slotIndex != null) {
            return payload.slotIndex;
        }
        if (payload.slot != null && !payload.slot.isBlank()) {
            try {
                return Integer.parseInt(payload.slot.trim());
            } catch (NumberFormatException ignored) {
                return -1;
            }
        }
        return -1;
    }

    private static String safeText(@Nullable String value, @Nonnull String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private static String formatMoney(double value) {
        return String.format(Locale.ROOT, "$%.2f", Math.max(0.0d, value));
    }

    private static String formatMultiplierValue(double value) {
        return String.format(Locale.ROOT, "%.2f", Math.max(0.0d, value));
    }

    private static double sanitizeMultiplier(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 1.0d;
        }
        return Math.max(0.0d, value);
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

    private record SellEntry(
            int inventorySlot,
            @Nonnull String shopId,
            @Nonnull String itemId,
            @Nonnull ItemStack stack,
            int quantity,
            double unitPrice,
            double totalPrice,
            @Nonnull MghgShopConfig.ShopItem shopItem,
            @Nullable MghgCropMeta meta
    ) {
    }

    private enum ShopTab {
        BUY,
        SELL
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
                .build();

        private String action;
        private String shopId;
        private String slot;
        private Integer slotIndex;
    }
}
