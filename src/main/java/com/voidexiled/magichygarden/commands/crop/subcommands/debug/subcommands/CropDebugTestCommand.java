package com.voidexiled.magichygarden.commands.crop.subcommands.debug.subcommands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.ItemUtils;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.voidexiled.magichygarden.features.farming.items.MghgCropMeta;
import org.jspecify.annotations.NonNull;

public class CropDebugTestCommand extends AbstractPlayerCommand {
    public CropDebugTestCommand() {
        super("test", "magichygarden.command.crop.debug.test.description");
    }

    @Override
    protected void execute(@NonNull CommandContext commandContext,
                           @NonNull Store<EntityStore> store,
                           @NonNull Ref<EntityStore> ref,
                           @NonNull PlayerRef playerRef,
                           @NonNull World world) {
        ItemStack base = new ItemStack("Plant_Crop_Lettuce_Item");
        ItemStack rain = base.withState("mghg_rain")
                .withMetadata(MghgCropMeta.KEY, MghgCropMeta.fromCropData(77, "RAIN", "NONE", 0.0));
        ItemStack snow = base.withState("mghg_snow")
                .withMetadata(MghgCropMeta.KEY, MghgCropMeta.fromCropData(77, "SNOW", "NONE", 0.0));

        // Mensaje para confirmar IDs
        commandContext.sendMessage(Message.raw("base=" + base.getItemId() + " | rain=" + rain.getItemId()));

        // Dárselo al jugador (origin null = sin animación, va directo a inventario)
        ItemUtils.interactivelyPickupItem(ref, rain, null, store);
    }
}
