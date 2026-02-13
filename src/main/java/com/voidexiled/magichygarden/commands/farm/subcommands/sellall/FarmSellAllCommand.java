package com.voidexiled.magichygarden.commands.farm.subcommands.sellall;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.DefaultArg;
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
import org.jspecify.annotations.NonNull;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Locale;

public class FarmSellAllCommand extends AbstractPlayerCommand {
    private final DefaultArg<String> shopIdArg;

    public FarmSellAllCommand() {
        super("sellall", "magichygarden.command.farm.sellall.description");
        this.shopIdArg = withDefaultArg(
                "shopId",
                "magichygarden.command.farm.sellall.args.item.description",
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
        MghgPlayerNameManager.remember(playerRef);
        String accessError = MghgShopAccessPolicy.validateTransactionContext(store, playerEntityRef, playerRef, world);
        if (accessError != null) {
            ctx.sendMessage(Message.raw(accessError));
            return;
        }

        Player player = store.getComponent(playerEntityRef, Player.getComponentType());
        if (player == null) {
            ctx.sendMessage(Message.raw("No pude obtener el componente de jugador."));
            return;
        }
        ItemContainer inventory = player.getInventory().getCombinedStorageFirst();

        String selector = normalize(shopIdArg.get(ctx));
        MghgShopConfig.ShopItem[] selectedItems = resolveSellTargets(selector);
        if (selectedItems.length == 0) {
            if ("all".equals(selector)) {
                ctx.sendMessage(Message.raw("No hay shopIds vendibles configurados."));
            } else {
                ctx.sendMessage(Message.raw("shopId no encontrado o no vendible."));
            }
            return;
        }

        ArrayList<ItemStack> rollbackStacks = new ArrayList<>();
        int soldUnits = 0;
        int soldShops = 0;
        double gain = 0.0d;

        for (MghgShopConfig.ShopItem item : selectedItems) {
            SellResult result = sellAllForItem(inventory, item, rollbackStacks);
            if (result.hasError()) {
                rollbackSell(inventory, rollbackStacks);
                ctx.sendMessage(Message.raw("No pude remover items del inventario, venta revertida."));
                return;
            }
            if (result.units() > 0) {
                soldShops++;
                soldUnits += result.units();
                gain += result.gain();
            }
        }

        if (soldUnits <= 0 || gain <= 0.0d) {
            ctx.sendMessage(Message.raw("No tienes items vendibles para ese shopId."));
            return;
        }

        MghgEconomyManager.deposit(playerRef.getUuid(), gain);
        double newBalance = MghgEconomyManager.getBalance(playerRef.getUuid());

        String label = "all".equals(selector) ? "all" : selector;
        ctx.sendMessage(Message.raw(String.format(
                Locale.ROOT,
                "Vendiste ALL %d items (%d shopIds, selector=%s) por $%s | balance=$%s",
                soldUnits,
                soldShops,
                label,
                formatMoney(gain),
                formatMoney(newBalance)
        )));
        FarmShopCommand.refreshHudForPlayer(store, playerEntityRef, playerRef, world);
    }

    private static MghgShopConfig.ShopItem[] resolveSellTargets(@NonNull String selector) {
        if ("all".equals(selector)) {
            ArrayList<MghgShopConfig.ShopItem> list = new ArrayList<>();
            for (MghgShopConfig.ShopItem item : MghgShopStockManager.getConfiguredItems()) {
                if (item == null || item.getId() == null || item.getId().isBlank()) {
                    continue;
                }
                if (item.getSellPrice() > 0.0) {
                    list.add(item);
                }
            }
            return list.toArray(MghgShopConfig.ShopItem[]::new);
        }
        MghgShopConfig.ShopItem single = MghgShopStockManager.getConfiguredItem(selector);
        if (single == null || single.getSellPrice() <= 0.0) {
            return new MghgShopConfig.ShopItem[0];
        }
        return new MghgShopConfig.ShopItem[]{single};
    }

    private static SellResult sellAllForItem(
            @NonNull ItemContainer inventory,
            MghgShopConfig.ShopItem item,
            @NonNull ArrayList<ItemStack> rollbackStacks
    ) {
        String[] sellItemIds = item.resolveSellItemIds();
        if (sellItemIds.length == 0) {
            return SellResult.empty();
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
            double unitPrice = MghgShopPricing.computeUnitSellPrice(item, meta);

            ItemStackSlotTransaction transaction = inventory.removeItemStackFromSlot(slot, take, true, true);
            if (!transaction.succeeded() || !ItemStack.isEmpty(transaction.getRemainder())) {
                return SellResult.error();
            }

            ItemStack removed = stack.withQuantity(take);
            if (removed != null && !ItemStack.isEmpty(removed)) {
                rollbackStacks.add(removed);
            }
            units += take;
            gain += unitPrice * take;
        }
        return units <= 0 ? SellResult.empty() : SellResult.success(units, gain);
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

    private static void rollbackSell(@NonNull ItemContainer inventory, @NonNull ArrayList<ItemStack> rollbackStacks) {
        for (ItemStack stack : rollbackStacks) {
            if (ItemStack.isEmpty(stack)) {
                continue;
            }
            inventory.addItemStack(stack, true, false, true);
        }
    }

    private static String normalize(@Nullable String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String formatMoney(double value) {
        return String.format(Locale.ROOT, "%.2f", Math.max(0.0, value));
    }

    private record SellResult(boolean hasError, int units, double gain) {
        private static SellResult error() {
            return new SellResult(true, 0, 0.0d);
        }

        private static SellResult empty() {
            return new SellResult(false, 0, 0.0d);
        }

        private static SellResult success(int units, double gain) {
            return new SellResult(false, units, gain);
        }
    }
}
