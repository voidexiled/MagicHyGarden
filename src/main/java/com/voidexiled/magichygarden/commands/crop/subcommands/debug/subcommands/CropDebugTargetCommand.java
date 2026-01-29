package com.voidexiled.magichygarden.commands.crop.subcommands.debug.subcommands;

import com.hypixel.hytale.builtin.adventure.farming.states.FarmingBlock;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.HarvestingDropType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.farming.FarmingData;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.farming.FarmingStageData;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.voidexiled.magichygarden.commands.shared.Targeting;
import com.voidexiled.magichygarden.features.farming.components.MghgCropData;
import org.jspecify.annotations.NonNull;

import java.awt.*;

public class CropDebugTargetCommand extends AbstractPlayerCommand {
    public CropDebugTargetCommand() {
        super("target", "magichygarden.command.crop.debug.target.description");
    }

    @Override
    protected void execute(
            @NonNull CommandContext ctx,
            @NonNull Store<EntityStore> entityStore,
            @NonNull Ref<EntityStore> playerEntityRef,
            @NonNull PlayerRef playerRef,
            @NonNull World world
    ) {

        final double range = 6.0;
        Vector3i target = Targeting.getTargetBlock(playerEntityRef, entityStore, range);

        if (target == null) {
            ctx.sendMessage(Message.raw("No estás mirando ningún bloque (rango " + range + ")."));
            return;
        }

        final int x = target.getX();
        final int y = target.getY();
        final int z = target.getZ();

        ChunkStore chunkStore = world.getChunkStore();
        Store<ChunkStore> cs = chunkStore.getStore();

        long chunkIndex = ChunkUtil.indexChunkFromBlock(x, z);
        Ref<ChunkStore> chunkRef = chunkStore.getChunkReference(chunkIndex);
        if (chunkRef == null) {
            ctx.sendMessage(Message.raw("El chunk objetivo no está disponible/cargado."));
            return;
        }

        WorldChunk worldChunk = cs.getComponent(chunkRef, WorldChunk.getComponentType());
        if (worldChunk == null) {
            ctx.sendMessage(Message.raw("El chunk objetivo no tiene WorldChunk (estado inválido)."));
            return;
        }

        BlockType blockType = worldChunk.getBlockType(x, y, z);
        if (blockType == null) {
            ctx.sendMessage(Message.raw("No se pudo obtener el BlockType en la posición objetivo."));
            return;
        }

        Message msg = Message.join(
                Message.raw("Información del bloque objetivo\n").color(Color.WHITE),
                Message.raw("Pos: ").color(Color.GRAY),
                Message.raw(x + ", " + y + ", " + z + "\n").color(Color.WHITE),
                Message.raw("ChunkIndex: ").color(Color.GRAY),
                Message.raw(Long.toString(chunkIndex) + "\n").color(Color.WHITE),
                Message.raw("BlockType: ").color(Color.GRAY),
                Message.raw(blockType.getId() + " (" + blockType.getClass().getSimpleName() + ")\n").color(Color.CYAN)
        );

        // ---- Farming config (del BlockType) ----
        String stageSet = null;
        FarmingStageData[] stages = null;

        FarmingData farming = blockType.getFarming();
        if (farming == null || farming.getStages() == null || farming.getStages().isEmpty()) {
            msg.insert(Message.raw("Farming: ").color(Color.GRAY))
                    .insert(Message.raw("NO (este bloque no tiene FarmingData)\n").color(Color.YELLOW));
            // ⚠️ NO hacemos return: seguimos para mostrar BlockEntity y MGHG data.
        } else {
            stageSet = farming.getStartingStageSet();
            if (stageSet == null) stageSet = "Default";

            stages = farming.getStages().get(stageSet);
            if (stages == null || stages.length == 0) {
                msg.insert(Message.raw("Farming: ").color(Color.GRAY))
                        .insert(Message.raw("Sí, pero no hay stages para stageSet '" + stageSet + "'\n").color(Color.YELLOW));
            } else {
                msg.insert(Message.raw("Farming: ").color(Color.GRAY))
                        .insert(Message.raw("Sí").color(Color.GREEN))
                        .insert(Message.raw(" | stageSet=").color(Color.GRAY))
                        .insert(Message.raw(stageSet).color(Color.WHITE))
                        .insert(Message.raw(" | stages=").color(Color.GRAY))
                        .insert(Message.raw(Integer.toString(stages.length) + "\n").color(Color.WHITE));
            }
        }

        // ---- Harvesting info (dropList/itemId) ----
        if (blockType.getGathering() != null && blockType.getGathering().isHarvestable()) {
            HarvestingDropType harvest = blockType.getGathering().getHarvest();
            msg.insert(Message.raw("Harvest: ").color(Color.GRAY))
                    .insert(Message.raw("Sí").color(Color.GREEN))
                    .insert(Message.raw(" | itemId=").color(Color.GRAY))
                    .insert(Message.raw(String.valueOf(harvest.getItemId())).color(Color.WHITE))
                    .insert(Message.raw(" | dropListId=").color(Color.GRAY))
                    .insert(Message.raw(String.valueOf(harvest.getDropListId()) + "\n").color(Color.WHITE));
        } else {
            msg.insert(Message.raw("Harvest: ").color(Color.GRAY))
                    .insert(Message.raw("NO (no harvest config)\n").color(Color.YELLOW));
        }

        // ---- Block-entity (Ref vs Holder) ----
        BlockComponentChunk blockComponentChunk = cs.getComponent(chunkRef, BlockComponentChunk.getComponentType());
        if (blockComponentChunk == null) {
            msg.insert(Message.raw("BlockEntity: ").color(Color.GRAY))
                    .insert(Message.raw("NO (BlockComponentChunk)\n").color(Color.YELLOW));
            ctx.sendMessage(msg);
            return;
        }

        int blockIndex = ChunkUtil.indexBlockInColumn(x, y, z);
        Ref<ChunkStore> blockRef = blockComponentChunk.getEntityReference(blockIndex);
        Holder<ChunkStore> blockHolder = blockComponentChunk.getEntityHolder(blockIndex);

        msg.insert(Message.raw("BlockEntityRef: ").color(Color.GRAY))
                .insert(Message.raw(blockRef != null ? "Sí\n" : "NO\n").color(blockRef != null ? Color.GREEN : Color.YELLOW));
        msg.insert(Message.raw("BlockEntityHolder: ").color(Color.GRAY))
                .insert(Message.raw(blockHolder != null ? "Sí\n" : "NO\n").color(blockHolder != null ? Color.GREEN : Color.YELLOW));

        // FarmingBlock (si existe en ref o holder)
        FarmingBlock farmingBlock = null;
        if (blockRef != null) {
            farmingBlock = cs.getComponent(blockRef, FarmingBlock.getComponentType());
        }
        if (farmingBlock == null && blockHolder != null) {
            farmingBlock = blockHolder.getComponent(FarmingBlock.getComponentType());
        }

        if (farmingBlock != null) {
            float p = farmingBlock.getGrowthProgress();

            msg.insert(Message.raw("FarmingBlock: ").color(Color.GRAY))
                    .insert(Message.raw("Sí").color(Color.GREEN))
                    .insert(Message.raw(" | progress=").color(Color.GRAY))
                    .insert(Message.raw(Float.toString(p)).color(Color.WHITE));

            // stage info SOLO si tenemos stages
            if (stages != null && stages.length > 0) {
                int stageIdx = Math.max(0, Math.min(stages.length - 1, (int) p));
                float frac = p - (float) stageIdx;

                msg.insert(Message.raw(" | stage=").color(Color.GRAY))
                        .insert(Message.raw(stageIdx + "/" + (stages.length - 1)).color(Color.WHITE))
                        .insert(Message.raw(" | frac=").color(Color.GRAY))
                        .insert(Message.raw(String.format(java.util.Locale.ROOT, "%.3f", frac)).color(Color.WHITE));
            }

            msg.insert(Message.raw("\n").color(Color.WHITE));
        } else {
            msg.insert(Message.raw("FarmingBlock: ").color(Color.GRAY))
                    .insert(Message.raw("NO\n").color(Color.YELLOW));
        }

        // MGHG data (ref o holder)
        MghgCropData cropData = null;
        if (blockRef != null) {
            cropData = cs.getComponent(blockRef, MghgCropData.getComponentType());
        }
        if (cropData == null && blockHolder != null) {
            cropData = blockHolder.getComponent(MghgCropData.getComponentType());
        }

        if (cropData != null) {
            msg.insert(Message.raw("MGHG Data: ").color(Color.GRAY))
                    .insert(Message.raw("size=").color(Color.GRAY)).insert(Message.raw(Integer.toString(cropData.getSize())).color(Color.MAGENTA))
                    .insert(Message.raw(" | climate=").color(Color.GRAY)).insert(Message.raw(cropData.getClimate().name()).color(Color.MAGENTA))
                    .insert(Message.raw(" | rarity=").color(Color.GRAY)).insert(Message.raw(cropData.getRarity().name()).color(Color.MAGENTA))
                    .insert(Message.raw(" | lastRoll=").color(Color.GRAY)).insert(Message.raw(String.valueOf(cropData.getLastMutationRoll()) + "\n").color(Color.MAGENTA));
        } else {
            msg.insert(Message.raw("MGHG Data: ").color(Color.GRAY))
                    .insert(Message.raw("NO\n").color(Color.YELLOW));
        }

        ctx.sendMessage(msg);
    }
}
