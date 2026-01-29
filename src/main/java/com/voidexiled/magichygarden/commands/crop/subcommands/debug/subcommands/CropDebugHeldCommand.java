package com.voidexiled.magichygarden.commands.crop.subcommands.debug.subcommands;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.voidexiled.magichygarden.features.farming.items.MghgCropMeta;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.jspecify.annotations.NonNull;
public class CropDebugHeldCommand extends AbstractPlayerCommand {

    public CropDebugHeldCommand() {
        super("held", "magichygarden.command.crop.debug.held.description");
    }

    @Override
    protected void execute(@NonNull CommandContext ctx,
                           @NonNull Store<EntityStore> store,
                           @NonNull Ref<EntityStore> ref,
                           @NonNull PlayerRef playerRef,
                           @NonNull World world) {

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            ctx.sendMessage(Message.raw("No se pudo obtener Player component."));
            return;
        }

        Inventory inv = player.getInventory();
        byte slot = inv.getActiveHotbarSlot();
        if (slot == -1) {
            ctx.sendMessage(Message.raw("No tienes ítem en mano (activeHotbarSlot = -1)."));
            return;
        }

        ItemContainer hotbar = inv.getHotbar();
        ItemStack item = hotbar.getItemStack(slot);
        if (item == null || ItemStack.isEmpty(item)) {
            ctx.sendMessage(Message.raw("No tienes ítem en mano (slot=" + slot + ")."));
            return;
        }

        // Metadata raw (debug)
        BsonDocument raw = item.getMetadata(); // deprecated, OK para debug
        boolean hasKey = raw != null && raw.containsKey(MghgCropMeta.KEY.getKey());

        // Metadata decoded (lo que realmente nos importa)
        MghgCropMeta meta = item.getFromMetadataOrNull(MghgCropMeta.KEY);

        String rawJson = raw == null ? "null" : raw.toJson();
        String metaJson;
        if (meta == null) {
            metaJson = "null";
        } else {
            BsonValue encoded = MghgCropMeta.CODEC.encode(meta); // deprecated encode, OK para debug
            metaJson = (encoded != null && encoded.isDocument()) ? encoded.asDocument().toJson() : String.valueOf(encoded);
        }

        ctx.sendMessage(Message.raw(
                "Held item\n" +
                        "- slot: " + slot + "\n" +
                        "- itemId: " + item.getItemId() + "\n" +
                        "- qty: " + item.getQuantity() + "\n" +
                        "- has '" + MghgCropMeta.KEY.getKey() + "': " + hasKey + "\n" +
                        "- decoded MghgCropMeta: " + metaJson + "\n" +
                        "- raw metadata: " + rawJson
        ));
    }
}