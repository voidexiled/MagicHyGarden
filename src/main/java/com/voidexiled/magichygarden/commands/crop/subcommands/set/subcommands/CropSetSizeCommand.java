package com.voidexiled.magichygarden.commands.crop.subcommands.set.subcommands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.codec.validation.Validators;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.DefaultArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.voidexiled.magichygarden.commands.shared.Targeting;
import com.voidexiled.magichygarden.features.farming.components.MghgCropData;
import com.voidexiled.magichygarden.features.farming.logic.MghgCropDataAccess;
import com.voidexiled.magichygarden.features.farming.registry.MghgCropRegistry;
import org.jspecify.annotations.NonNull;

public class CropSetSizeCommand extends AbstractPlayerCommand {
    private final DefaultArg<Integer> valueArg;

    public CropSetSizeCommand() {
        super("size", "magichygarden.command.crop.set.size.description");

        valueArg = withDefaultArg(
                "value",
                "magichygarden.command.crop.set.size.args.value.description",
                ArgTypes.INTEGER,
                80,
                "80"
        ).addValidator(Validators.greaterThanOrEqual(1));
    }

    @Override
    protected void execute(
            @NonNull CommandContext ctx,
            @NonNull Store<EntityStore> store,
            @NonNull Ref<EntityStore> playerEntityRef,
            @NonNull PlayerRef playerRef,
            @NonNull World world
    ) {
        int size = valueArg.get(ctx);

        Vector3i target = Targeting.getTargetBlock(playerEntityRef, store, 6.0);
        if (target == null) {
            ctx.sendMessage(Message.raw("No estas mirando ningun bloque (rango 6)."));
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
            ctx.sendMessage(Message.raw("El chunk objetivo no esta disponible."));
            return;
        }

        WorldChunk worldChunk = cs.getComponent(chunkRef, WorldChunk.getComponentType());
        if (worldChunk == null) {
            ctx.sendMessage(Message.raw("No pude leer WorldChunk del chunk objetivo."));
            return;
        }

        BlockType blockType = worldChunk.getBlockType(x, y, z);
        if (blockType == null) {
            ctx.sendMessage(Message.raw("No pude obtener BlockType del bloque objetivo."));
            return;
        }
        if (!MghgCropRegistry.isMghgCropBlock(blockType)) {
            ctx.sendMessage(Message.raw("Ese bloque no es un crop MGHG."));
            return;
        }

        BlockComponentChunk blockComponentChunk = cs.getComponent(chunkRef, BlockComponentChunk.getComponentType());

        BlockChunk blockChunk = cs.getComponent(chunkRef, BlockChunk.getComponentType());
        MghgCropDataAccess.CropDataHandle handle =
                MghgCropDataAccess.getOrCreateCropData(cs, worldChunk, blockComponentChunk, x, y, z);
        MghgCropData data = handle != null ? handle.data() : null;

        if (data == null) {
            ctx.sendMessage(Message.raw("El bloque objetivo no tiene MGHG data."));
            return;
        }

        int before = data.getSize();
        data.setSize(size);

        if (blockComponentChunk != null) blockComponentChunk.markNeedsSaving();
        if (blockChunk != null) blockChunk.markNeedsSaving();

        if (handle != null && handle.fromStateHolder() && handle.stateHolder() != null) {
            worldChunk.setState(x, y, z, handle.stateHolder());
        }

        ctx.sendMessage(Message.raw(
                "Size: " + before + " -> " + size
        ));
    }
}
