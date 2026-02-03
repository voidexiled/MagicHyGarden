package com.voidexiled.magichygarden.commands.crop.subcommands.add.subcommands;

import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.DefaultArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.voidexiled.magichygarden.commands.shared.Targeting;
import com.voidexiled.magichygarden.features.farming.components.MghgCropData;
import com.voidexiled.magichygarden.features.farming.state.RarityMutation;
import org.jspecify.annotations.NonNull;

import javax.annotation.Nullable;
import java.time.Instant;
import java.util.Locale;

public class CropAddRarityCommand extends AbstractPlayerCommand {

    private final DefaultArg<String> nameArg;

    public CropAddRarityCommand() {
        super("rarity", "magichygarden.command.crop.add.rarity.description");

        nameArg = withDefaultArg(
                "name",
                "magichygarden.command.crop.add.rarity.args.name.description",
                ArgTypes.STRING,
                "",
                ""
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
        String rawName = nameArg.get(ctx);
        if (rawName == null || rawName.trim().isEmpty()) {
            ctx.sendMessage(Message.raw("Uso: /crop add rarity --name=<gold|rainbow|none>"));
            return;
        }

        RarityMutation add = parseRarity(rawName);
        if (add == null) {
            ctx.sendMessage(Message.raw("Rareza inválida: " + rawName + " (usa gold, rainbow o none)"));
            return;
        }

        Vector3i target = Targeting.getTargetBlock(playerEntityRef, store, 6.0);
        if (target == null) {
            ctx.sendMessage(Message.raw("No estás mirando ningún bloque (rango 6)."));
            return;
        }

        final int x = target.getX();
        final int y = target.getY();
        final int z = target.getZ();

        ChunkStore chunkStore = world.getChunkStore();
        Store<ChunkStore> cs = chunkStore.getStore();

        long chunkIndex = ChunkUtil.indexChunkFromBlock(x, z);
        Ref<ChunkStore> chunkRef = chunkStore.getChunkReference(chunkIndex);
        if (chunkRef == null || !chunkRef.isValid()) {
            ctx.sendMessage(Message.raw("El chunk objetivo no está disponible."));
            return;
        }

        WorldChunk worldChunk = cs.getComponent(chunkRef, WorldChunk.getComponentType());
        if (worldChunk == null) {
            ctx.sendMessage(Message.raw("No pude leer WorldChunk del chunk objetivo."));
            return;
        }

        BlockChunk blockChunk = cs.getComponent(chunkRef, BlockChunk.getComponentType());
        BlockComponentChunk blockComponentChunk = cs.getComponent(chunkRef, BlockComponentChunk.getComponentType());

        int blockIndex = ChunkUtil.indexBlockInColumn(x, y, z);
        Ref<ChunkStore> blockRef = blockComponentChunk != null ? blockComponentChunk.getEntityReference(blockIndex) : null;
        Holder<ChunkStore> blockHolder = blockComponentChunk != null ? blockComponentChunk.getEntityHolder(blockIndex) : null;
        Holder<ChunkStore> stateHolder = worldChunk.getBlockComponentHolder(x, y, z);

        MghgCropData data = null;
        boolean fromStateHolder = false;

        if (blockRef != null && blockRef.isValid()) {
            data = cs.ensureAndGetComponent(blockRef, MghgCropData.getComponentType());
        } else if (blockHolder != null) {
            data = blockHolder.getComponent(MghgCropData.getComponentType());
            if (data == null) {
                data = new MghgCropData();
                blockHolder.putComponent(MghgCropData.getComponentType(), data);
            }
        } else if (stateHolder != null) {
            data = stateHolder.getComponent(MghgCropData.getComponentType());
            if (data == null) {
                data = new MghgCropData();
                stateHolder.putComponent(MghgCropData.getComponentType(), data);
            }
            fromStateHolder = true;
        }

        if (data == null) {
            ctx.sendMessage(Message.raw("El bloque objetivo no tiene MGHG data."));
            return;
        }

        RarityMutation before = data.getRarity();
        data.setRarity(add);
        WorldTimeResource time = store.getResource(WorldTimeResource.getResourceType());
        if (time != null) {
            Instant now = time.getGameTime();
            data.setLastSpecialRoll(now);
        }

        if (blockComponentChunk != null) blockComponentChunk.markNeedsSaving();
        if (blockChunk != null) blockChunk.markNeedsSaving();

        if (fromStateHolder && stateHolder != null) {
            worldChunk.setState(x, y, z, stateHolder);
        }

        ctx.sendMessage(Message.raw(
                "Rarity: " + before.name() + " -> " + add.name() + " (add=" + add.name() + ")"
        ));
    }

    private static @Nullable RarityMutation parseRarity(String raw) {
        if (raw == null) return null;
        String key = raw.trim().toUpperCase(Locale.ROOT);
        return switch (key) {
            case "GOLD" -> RarityMutation.GOLD;
            case "RAINBOW" -> RarityMutation.RAINBOW;
            case "NONE" -> RarityMutation.NONE;
            default -> null;
        };
    }
}
