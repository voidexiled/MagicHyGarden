package com.voidexiled.magichygarden.commands.farm.subcommands.admin.subcommands.stock.subcommands.restock;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.voidexiled.magichygarden.features.farming.shop.MghgShopStockManager;
import org.jspecify.annotations.NonNull;

public class FarmAdminStockRestockSubCommand extends AbstractPlayerCommand {

    public FarmAdminStockRestockSubCommand() {
        super("restock", "magichygarden.command.farm.admin.stock.restock.description");
    }

    @Override
    protected void execute(@NonNull CommandContext commandContext,
                           @NonNull Store<EntityStore> store,
                           @NonNull Ref<EntityStore> ref,
                           @NonNull PlayerRef playerRef,
                           @NonNull World world) {
        MghgShopStockManager.forceRestockNow();
        commandContext.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("Restock forzado."));
    }
}
