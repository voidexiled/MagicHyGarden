package com.voidexiled.magichygarden.commands.crop.subcommands.reload;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.DefaultArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.voidexiled.magichygarden.features.farming.modifiers.MghgCropGrowthModifierAsset;
import com.voidexiled.magichygarden.features.farming.registry.MghgCropRegistry;
import com.voidexiled.magichygarden.features.farming.state.MghgMutationRules;
import javax.annotation.Nonnull;

import java.util.Locale;

public class CropReloadCommand extends AbstractPlayerCommand {
    private final DefaultArg<String> targetArg;

    public CropReloadCommand() {
        super("reload", "magichygarden.command.crop.reload.description");

        targetArg = withDefaultArg(
                "target",
                "magichygarden.command.crop.reload.args.target.description",
                ArgTypes.STRING,
                "all",
                "all"
        );
    }

    @Override
    protected void execute(
            @Nonnull CommandContext ctx,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> playerEntityRef,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
    ) {
        String raw = targetArg.get(ctx);
        String key = raw == null ? "all" : raw.trim().toLowerCase(Locale.ROOT);
        if (key.isBlank()) key = "all";

        boolean did = true;
        switch (key) {
            case "all", "*", "everything" -> {
                MghgCropRegistry.reload();
                MghgCropGrowthModifierAsset.reloadFromDisk();
                MghgMutationRules.reload();
                ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("Reload completo: crops + size + mutation rules."));
            }
            case "crops", "registry" -> {
                MghgCropRegistry.reload();
                ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("Reload: crop registry (Mghg_Crops.json)."));
            }
            case "mutations", "rules" -> {
                MghgMutationRules.reload();
                ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("Reload: mutation rules (Farming/Mutations asset)."));
            }
            case "growth", "size", "config", "modifier" -> {
                MghgCropGrowthModifierAsset.reloadFromDisk();
                ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("Reload: growth/size config (Size.json)."));
            }
            default -> did = false;
        }

        if (!did) {
            ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text(
                    "Uso: /crop reload [all|crops|mutations|growth]"
            ));
        }
    }
}
