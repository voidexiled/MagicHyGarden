package com.voidexiled.magichygarden.commands.crop.subcommands.grow;

import com.hypixel.hytale.builtin.adventure.farming.states.FarmingBlock;
import com.hypixel.hytale.codec.validation.Validators;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.farming.FarmingData;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.farming.FarmingStageData;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.DefaultArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import com.voidexiled.magichygarden.features.farming.registry.MghgCropRegistry;
import org.jspecify.annotations.NonNull;

public class CropGrowCommand extends AbstractPlayerCommand {

    final private DefaultArg<Integer> percentArg;

    public CropGrowCommand() {
        super("grow", "magichygarden.command.crop.grow.description");

        percentArg = withDefaultArg(
                "percent",
                "magichygarden.command.crop.grow.args.percent.description",
                ArgTypes.INTEGER,
                100,
                "100"
        ).addValidator(Validators.greaterThanOrEqual(0))
         .addValidator(Validators.lessThan(101));
    }

    @Override
    protected void execute(@NonNull CommandContext commandContext,
                           @NonNull Store<EntityStore> store,
                           @NonNull Ref<EntityStore> playerEntityRef,
                           @NonNull PlayerRef playerRef,
                           @NonNull World world) {
        int percent = percentArg.get(commandContext);
        if (percent < 0) percent = 0;
        if (percent > 100) percent = 100;

        // 1) Bloque objetivo (raycast real de la API)
        Vector3i target = TargetUtil.getTargetBlock(playerEntityRef, 6.0, store);
        if (target == null) {
            commandContext.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("No estás mirando ningún bloque (rango 6)."));
            return;
        }

        int x = target.getX();
        int y = target.getY();
        int z = target.getZ();

        // 2) Chunk + componentes de mundo
        ChunkStore chunkStore = world.getChunkStore();
        Store<ChunkStore> chunkStoreStore = chunkStore.getStore();

        long chunkIndex = ChunkUtil.indexChunkFromBlock(x, z);
        Ref<ChunkStore> chunkRef = chunkStore.getChunkReference(chunkIndex);
        if (chunkRef == null || !chunkRef.isValid()) {
            commandContext.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("El chunk objetivo no está disponible."));
            return;
        }

        WorldChunk worldChunk = chunkStoreStore.getComponent(chunkRef, WorldChunk.getComponentType());
        if (worldChunk == null) {
            commandContext.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("No pude leer WorldChunk del chunk objetivo."));
            return;
        }

        // 3) Verificar que el bloque sea “farmable” (crop)
        BlockType blockType = worldChunk.getBlockType(x, y, z);
        if (blockType == null) {
            commandContext.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("No pude obtener BlockType del bloque objetivo."));
            return;
        }
        if (!MghgCropRegistry.isMghgCropBlock(blockType)) {
            commandContext.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("Ese bloque no es un crop MGHG."));
            return;
        }

        FarmingData farming = blockType.getFarming();
        if (farming == null || farming.getStages() == null || farming.getStages().isEmpty()) {
            commandContext.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("Ese bloque no tiene FarmingData (no parece ser un crop)."));
            return;
        }

        // 4) Resolver stage set + stages
        String stageSet = farming.getStartingStageSet();
        FarmingStageData[] stages = farming.getStages().get(stageSet);
        if (stages == null || stages.length == 0) {
            commandContext.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("El crop no tiene stages en el stageSet inicial: " + stageSet));
            return;
        }

        // progress en la API de farming se maneja como float por “índice de stage”
        float maxStage = Math.max(0, stages.length - 1);
        float progress = (maxStage == 0) ? 0f : (percent / 100.0f) * maxStage;
        int targetStageIndex = (int) Math.floor(progress + 1e-6f);
        if (targetStageIndex < 0) targetStageIndex = 0;
        if (targetStageIndex > stages.length - 1) targetStageIndex = stages.length - 1;

        // 5) Asegurar block component entity (donde vive FarmingBlock)
        BlockComponentChunk blockComponentChunk = chunkStoreStore.getComponent(chunkRef,
                BlockComponentChunk.getComponentType());
        if (blockComponentChunk == null) {
            commandContext.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("No pude obtener BlockComponentChunk del chunk objetivo."));
            return;
        }

        int indexInColumn = ChunkUtil.indexBlockInColumn(x, y, z);
        Ref<ChunkStore> blockEntityRef = blockComponentChunk.getEntityReference(indexInColumn);

        // Si hay holder (chunk no-ticking), lo convertimos a entity; si no hay nada, creamos desde cero.
        if (blockEntityRef == null || !blockEntityRef.isValid()) {
            var holder = blockComponentChunk.removeEntityHolder(indexInColumn);

            if (holder == null) {
                holder = ChunkStore.REGISTRY.newHolder();
            }

            // BlockStateInfo es clave para block-component entities
            holder.putComponent(
                    BlockModule.BlockStateInfo.getComponentType(),
                    new BlockModule.BlockStateInfo(indexInColumn, chunkRef)
            );

            // FarmingBlock (estado del crop)
            FarmingBlock fb = holder.getComponent(FarmingBlock.getComponentType());
            if (fb == null) {
                fb = new FarmingBlock();
                holder.putComponent(FarmingBlock.getComponentType(), fb);
            }

            blockEntityRef = chunkStoreStore.addEntity(holder, AddReason.SPAWN);
            if (blockEntityRef == null) {
                commandContext.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("No pude crear la Block Component Entity del bloque objetivo."));
                return;
            }
            blockComponentChunk.addEntityReference(indexInColumn, blockEntityRef);
        }

        // 6) Actualizar componente FarmingBlock con el progreso deseado
        FarmingBlock farmingBlock = chunkStoreStore.getComponent(blockEntityRef, FarmingBlock.getComponentType());
        if (farmingBlock == null) {
            farmingBlock = new FarmingBlock();
            chunkStoreStore.putComponent(blockEntityRef, FarmingBlock.getComponentType(), farmingBlock);
        }

        WorldTimeResource time = store.getResource(WorldTimeResource.getResourceType());

        farmingBlock.setCurrentStageSet(stageSet);
        farmingBlock.setGrowthProgress(progress);
        if (time != null) {
            farmingBlock.setLastTickGameTime(time.getGameTime());
        }
        farmingBlock.setGeneration(farmingBlock.getGeneration() + 1);

        // 7) Aplicar stage al bloque + schedule tick (misma ruta que usa el farming real)
        Ref<ChunkStore> sectionRef = chunkStore.getChunkSectionReference(
                ChunkUtil.chunkCoordinate(x),
                ChunkUtil.chunkCoordinate(y),
                ChunkUtil.chunkCoordinate(z)
        );

        BlockSection blockSection = (sectionRef != null)
                ? chunkStoreStore.getComponent(sectionRef, BlockSection.getComponentType())
                : null;

        if (sectionRef == null || blockSection == null) {
            commandContext.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("No pude obtener BlockSection/sectionRef del bloque objetivo."));
            return;
        }

        FarmingStageData targetStage = stages[targetStageIndex];
        targetStage.apply(chunkStoreStore, sectionRef, blockEntityRef, x, y, z, targetStage);

        if (time != null) {
            blockSection.scheduleTick(ChunkUtil.indexBlock(x, y, z), time.getGameTime());
        }

        // aseguramos ticking para esa celda (igual que interacciones de farming)
        worldChunk.setTicking(x, y, z, true);

        commandContext.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text(
                "Crop crecido a " + percent + "% (stage " + targetStageIndex + "/" + (stages.length - 1) + ")."
        ));
    }
}
