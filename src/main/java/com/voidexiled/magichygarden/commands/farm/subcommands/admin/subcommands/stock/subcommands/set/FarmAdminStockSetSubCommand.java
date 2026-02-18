package com.voidexiled.magichygarden.commands.farm.subcommands.admin.subcommands.stock.subcommands.set;

import com.hypixel.hytale.codec.validation.Validators;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.voidexiled.magichygarden.features.farming.shop.MghgShopStockManager;
import org.jspecify.annotations.NonNull;

public class FarmAdminStockSetSubCommand extends AbstractPlayerCommand {
    private final RequiredArg<String> shopIdArg;
    private final RequiredArg<Integer> qtyArg;

    public FarmAdminStockSetSubCommand() {
        super("set", "magichygarden.command.farm.admin.stock.set.description");

        this.shopIdArg = withRequiredArg(
                "shopId",
                "magichygarden.command.farm.admin.stock.set.args.shopId.description",
                ArgTypes.STRING
        ).addValidator(Validators.nonEmptyString());

        this.qtyArg = withRequiredArg(
                "quantity",
                "magichygarden.command.farm.admin.stock.set.args.quantity.description",
                ArgTypes.INTEGER
        ).addValidator(Validators.greaterThanOrEqual(0));
    }

    @Override
    protected void execute(@NonNull CommandContext commandContext,
                           @NonNull Store<EntityStore> store,
                           @NonNull Ref<EntityStore> ref,
                           @NonNull PlayerRef playerRef,
                           @NonNull World world) {
        String shopId = commandContext.get(shopIdArg);
        int qty = commandContext.get(qtyArg);

        handleStockSet(commandContext, shopId, qty);
    }

    private void handleStockSet(@NonNull CommandContext ctx, @NonNull String shopId, int qty){
        boolean ok = MghgShopStockManager.setStock(shopId, qty);
        if (!ok) {
            ctx.sendMessage(Message.raw("No pude actualizar stock para '" + shopId + "'."));
            return;
        }
        ctx.sendMessage(Message.raw("Stock actualizado: " + shopId + "=" + qty));
    }
}
