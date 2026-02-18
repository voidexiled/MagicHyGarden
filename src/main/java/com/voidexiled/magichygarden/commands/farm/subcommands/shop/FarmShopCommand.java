package com.voidexiled.magichygarden.commands.farm.subcommands.shop;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.DefaultArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.entity.entities.player.hud.HudManager;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.voidexiled.magichygarden.features.farming.economy.MghgEconomyManager;
import com.voidexiled.magichygarden.features.farming.items.MghgCropMeta;
import com.voidexiled.magichygarden.features.farming.shop.MghgShopConfig;
import com.voidexiled.magichygarden.features.farming.shop.MghgShopPricing;
import com.voidexiled.magichygarden.features.farming.shop.MghgShopStockManager;
import com.voidexiled.magichygarden.features.farming.tooltips.MghgDynamicTooltipsManager;
import com.voidexiled.magichygarden.features.farming.ui.MghgFarmShopPage;
import com.voidexiled.magichygarden.features.farming.ui.MghgFarmShopPageV2;
import com.voidexiled.magichygarden.features.farming.ui.MghgFarmShopHud;
import org.jspecify.annotations.NonNull;

import javax.annotation.Nullable;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FarmShopCommand extends AbstractPlayerCommand {
    private static final int MAX_LINES = 8;
    private static final Map<UUID, MghgFarmShopHud> HUDS = new ConcurrentHashMap<>();

    private final DefaultArg<String> actionArg;

    public FarmShopCommand() {
        super("shop", "magichygarden.command.farm.shop.description");
        this.actionArg = withDefaultArg(
                "action",
                "magichygarden.command.farm.shop.args.action.description",
                ArgTypes.STRING,
                "open",
                "open"
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
        String action = normalize(actionArg.get(ctx));
        if ("close".equals(action) || "hide".equals(action)) {
            closeHudForPlayer(store, playerEntityRef, playerRef);
            ctx.sendMessage(Message.raw("Shop UI cerrada."));
            return;
        }

        if ("hud".equals(action)) {
            String openHudError = openHudForPlayer(store, playerEntityRef, playerRef, world);
            if (openHudError != null) {
                ctx.sendMessage(Message.raw(openHudError));
                return;
            }
            MghgFarmShopPage.closeForPlayer(store, playerEntityRef);
            MghgFarmShopPageV2.closeForPlayer(store, playerEntityRef);
            ctx.sendMessage(Message.raw("Shop HUD abierta. Usa /farm shop close para cerrar."));
            return;
        }

        if ("text".equals(action)) {
            MghgShopConfig.ShopItem[] items = MghgShopStockManager.getConfiguredItems();
            if (items.length == 0) {
                ctx.sendMessage(Message.raw("Shop config vacia."));
                return;
            }
            Player player = store.getComponent(playerEntityRef, Player.getComponentType());
            if (player == null) {
                ctx.sendMessage(Message.raw("No pude obtener el componente de jugador."));
                return;
            }
            ItemContainer inventory = player.getInventory().getCombinedStorageFirst();
            double balance = MghgEconomyManager.getBalance(playerRef.getUuid());
            long remaining = MghgShopStockManager.getRemainingRestockSeconds();
            String[] lines = buildLines(playerRef, inventory, items);
            sendTextView(ctx, balance, remaining, lines);
            return;
        }

        if ("openv2".equals(action) || "v2".equals(action)) {
            MghgDynamicTooltipsManager.tryRegister();
            MghgDynamicTooltipsManager.refreshAllPlayers();
            String openError = MghgFarmShopPageV2.openForPlayer(store, playerEntityRef, playerRef, world);
            if (openError != null) {
                ctx.sendMessage(Message.raw(openError));
                return;
            }
            closeLegacyHudForPlayer(store, playerEntityRef, playerRef);
            MghgFarmShopPage.closeForPlayer(store, playerEntityRef);
            ctx.sendMessage(Message.raw("Shop V2 abierta. Usa /farm shop close para cerrar."));
            return;
        }

        MghgDynamicTooltipsManager.tryRegister();
        MghgDynamicTooltipsManager.refreshAllPlayers();
        String openError = MghgFarmShopPage.openForPlayer(store, playerEntityRef, playerRef, world);
        if (openError != null) {
            ctx.sendMessage(Message.raw(openError));
            return;
        }
        closeLegacyHudForPlayer(store, playerEntityRef, playerRef);
        MghgFarmShopPageV2.closeForPlayer(store, playerEntityRef);
        ctx.sendMessage(Message.raw("Shop page abierta. Usa /farm shop close para cerrar."));
    }

    public static @Nullable String openHudForPlayer(
            @NonNull Store<EntityStore> store,
            @NonNull Ref<EntityStore> playerEntityRef,
            @NonNull PlayerRef playerRef,
            @NonNull World world
    ) {
        String accessError = com.voidexiled.magichygarden.features.farming.shop.MghgShopAccessPolicy
                .validateTransactionContext(store, playerEntityRef, playerRef, world);
        if (accessError != null) {
            return accessError;
        }
        MghgShopConfig.ShopItem[] items = MghgShopStockManager.getConfiguredItems();
        if (items.length == 0) {
            return "Shop config vacia.";
        }
        Player player = store.getComponent(playerEntityRef, Player.getComponentType());
        if (player == null) {
            return "No pude obtener el componente de jugador.";
        }
        ItemContainer inventory = player.getInventory().getCombinedStorageFirst();
        double balance = MghgEconomyManager.getBalance(playerRef.getUuid());
        long remaining = MghgShopStockManager.getRemainingRestockSeconds();
        String[] lines = buildLines(playerRef, inventory, items);
        showHud(playerRef, player, balance, remaining, lines);
        return null;
    }

    public static void refreshHudForPlayer(
            @NonNull Store<EntityStore> store,
            @NonNull Ref<EntityStore> playerEntityRef,
            @NonNull PlayerRef playerRef,
            @NonNull World world
    ) {
        refreshHudForPlayer(store, playerEntityRef, playerRef, world, true);
    }

    public static void refreshHudForPlayer(
            @NonNull Store<EntityStore> store,
            @NonNull Ref<EntityStore> playerEntityRef,
            @NonNull PlayerRef playerRef,
            @NonNull World world,
            boolean fullPageRefresh
    ) {
        Player player = store.getComponent(playerEntityRef, Player.getComponentType());
        if (player == null) {
            return;
        }
        if (fullPageRefresh) {
            MghgFarmShopPage.refreshForPlayer(store, playerEntityRef);
            MghgFarmShopPageV2.refreshForPlayer(store, playerEntityRef);
        } else {
            MghgFarmShopPage.refreshStatusForPlayer(store, playerEntityRef);
            MghgFarmShopPageV2.refreshStatusForPlayer(store, playerEntityRef);
        }

        HudManager hudManager = player.getHudManager();
        CustomUIHud current = hudManager.getCustomHud();
        if (!(current instanceof MghgFarmShopHud)) {
            return;
        }

        String accessError = com.voidexiled.magichygarden.features.farming.shop.MghgShopAccessPolicy
                .validateTransactionContext(store, playerEntityRef, playerRef, world);
        if (accessError != null) {
            ((MghgFarmShopHud) current).hide();
            return;
        }

        MghgShopConfig.ShopItem[] items = MghgShopStockManager.getConfiguredItems();
        if (items.length == 0) {
            ((MghgFarmShopHud) current).hide();
            return;
        }
        ItemContainer inventory = player.getInventory().getCombinedStorageFirst();
        double balance = MghgEconomyManager.getBalance(playerRef.getUuid());
        long remaining = MghgShopStockManager.getRemainingRestockSeconds();
        String[] lines = buildLines(playerRef, inventory, items);
        showHud(
                playerRef,
                player,
                balance,
                remaining,
                lines
        );
    }

    private static void sendTextView(@NonNull CommandContext ctx, double balance, long remaining, @NonNull String[] lines) {
        ctx.sendMessage(Message.raw(String.format(
                Locale.ROOT,
                "Farm Shop | balance=$%.2f | nextRestock=%s",
                balance,
                formatDuration(remaining)
        )));
        for (String line : lines) {
            if (line != null && !line.isBlank()) {
                ctx.sendMessage(Message.raw(line));
            }
        }
        ctx.sendMessage(Message.raw("Uso: /farm buy <shopId> <qty> | /farm buymax <shopId> | /farm sell <shopId> <qty> | /farm sellall [shopId|all]"));
    }

    private static void showHud(
            @NonNull PlayerRef playerRef,
            @NonNull Player player,
            double balance,
            long remaining,
            @NonNull String[] lines
    ) {
        HudManager hudManager = player.getHudManager();
        CustomUIHud current = hudManager.getCustomHud();
        MghgFarmShopHud hud = HUDS.computeIfAbsent(playerRef.getUuid(), id -> new MghgFarmShopHud(playerRef));
        if (current != hud) {
            hudManager.setCustomHud(playerRef, hud);
        }
        hud.updateContent(
                "Farm Shop",
                String.format(Locale.ROOT, "Balance: $%.2f", balance),
                "Next restock: " + formatDuration(remaining),
                lines,
                "Use /farm buy <shopId> <qty> or /farm sell <shopId> <qty>"
        );
    }

    public static void closeHudForPlayer(
            @NonNull Store<EntityStore> store,
            @NonNull Ref<EntityStore> playerEntityRef,
            @NonNull PlayerRef playerRef
    ) {
        closeLegacyHudForPlayer(store, playerEntityRef, playerRef);
        MghgFarmShopPage.closeForPlayer(store, playerEntityRef);
        MghgFarmShopPageV2.closeForPlayer(store, playerEntityRef);
    }

    public static void closeLegacyHudForPlayer(
            @NonNull Store<EntityStore> store,
            @NonNull Ref<EntityStore> playerEntityRef,
            @NonNull PlayerRef playerRef
    ) {
        Player player = store.getComponent(playerEntityRef, Player.getComponentType());
        if (player == null) {
            return;
        }
        HudManager hudManager = player.getHudManager();
        CustomUIHud current = hudManager.getCustomHud();
        if (current instanceof MghgFarmShopHud hud) {
            hud.hide();
        }
    }

    private static String[] buildLines(
            @NonNull PlayerRef playerRef,
            @NonNull ItemContainer inventory,
            MghgShopConfig.ShopItem[] items
    ) {
        String[] lines = new String[MAX_LINES];
        int lineIndex = 0;
        for (MghgShopConfig.ShopItem item : items) {
            if (lineIndex >= MAX_LINES) {
                break;
            }
            if (item == null || item.getId() == null || item.getId().isBlank()) {
                continue;
            }
            String shopId = item.getId();
            int personal = MghgShopStockManager.getPlayerStock(playerRef.getUuid(), shopId);
            int global = MghgShopStockManager.getStock(shopId);
            int sellable = countSellable(inventory, item.resolveSellItemIds());
            double estimated = estimateSellValue(inventory, item);

            lines[lineIndex++] = String.format(
                    Locale.ROOT,
                    "%s | stock %d/%d | buy $%.2f | inv sellable %d (~$%.2f)",
                    shopId,
                    personal,
                    global,
                    item.getBuyPrice(),
                    sellable,
                    estimated
            );
        }
        return lines;
    }

    private static int countSellable(@NonNull ItemContainer inventory, @NonNull String[] sellIds) {
        int total = 0;
        short capacity = inventory.getCapacity();
        for (short slot = 0; slot < capacity; slot++) {
            ItemStack stack = inventory.getItemStack(slot);
            if (ItemStack.isEmpty(stack)) {
                continue;
            }
            if (!matchesSellItem(stack, sellIds)) {
                continue;
            }
            total += Math.max(0, stack.getQuantity());
        }
        return total;
    }

    private static double estimateSellValue(@NonNull ItemContainer inventory, MghgShopConfig.ShopItem item) {
        String[] sellIds = item.resolveSellItemIds();
        double total = 0.0d;
        short capacity = inventory.getCapacity();
        for (short slot = 0; slot < capacity; slot++) {
            ItemStack stack = inventory.getItemStack(slot);
            if (ItemStack.isEmpty(stack)) {
                continue;
            }
            if (!matchesSellItem(stack, sellIds)) {
                continue;
            }
            MghgCropMeta meta = stack.getFromMetadataOrNull(MghgCropMeta.KEY);
            double unit = MghgShopPricing.computeUnitSellPrice(item, meta);
            total += unit * Math.max(0, stack.getQuantity());
        }
        return total;
    }

    private static boolean matchesSellItem(@NonNull ItemStack stack, @NonNull String[] acceptedItemIds) {
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

    private static String normalize(@Nullable String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
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
}
