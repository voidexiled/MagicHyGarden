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
import com.hypixel.hytale.server.core.entity.entities.BlockEntity;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.voidexiled.magichygarden.commands.shared.Targeting;
import com.voidexiled.magichygarden.features.farming.components.MghgCropData;
import com.voidexiled.magichygarden.features.farming.events.MghgFarmEventScheduler;
import com.voidexiled.magichygarden.features.farming.events.MghgGlobalFarmEventState;
import com.voidexiled.magichygarden.features.farming.modifiers.MghgCropGrowthModifierAsset;
import com.voidexiled.magichygarden.features.farming.state.MghgMutationContext;
import com.voidexiled.magichygarden.features.farming.state.MghgMutationRule;
import com.voidexiled.magichygarden.features.farming.state.MghgMutationRuleSet;
import com.voidexiled.magichygarden.features.farming.state.MghgMutationRules;
import com.voidexiled.magichygarden.features.farming.state.MghgWeatherResolver;
import com.voidexiled.magichygarden.features.farming.state.MghgWeatherIdUtil;
import com.voidexiled.magichygarden.features.farming.state.MutationEventType;
import com.voidexiled.magichygarden.features.farming.state.MghgBlockIdUtil;
import com.voidexiled.magichygarden.features.farming.state.MutationSlot;
import org.jspecify.annotations.NonNull;

import java.awt.*;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Locale;

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
            ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("No estás mirando ningún bloque (rango " + range + ")."));
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
            ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("El chunk objetivo no está disponible/cargado."));
            return;
        }

        WorldChunk worldChunk = cs.getComponent(chunkRef, WorldChunk.getComponentType());
        if (worldChunk == null) {
            ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("El chunk objetivo no tiene WorldChunk (estado inválido)."));
            return;
        }
        BlockChunk blockChunk = cs.getComponent(chunkRef, BlockChunk.getComponentType());
        if (blockChunk == null) {
            ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("El chunk objetivo no tiene BlockChunk (estado inválido)."));
            return;
        }

        BlockType blockType = worldChunk.getBlockType(x, y, z);
        if (blockType == null) {
            ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("No se pudo obtener el BlockType en la posición objetivo."));
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
                    .insert(Message.raw(" | weight=").color(Color.GRAY)).insert(Message.raw(String.format(java.util.Locale.ROOT, "%.2f", cropData.getWeightGrams())).color(Color.MAGENTA))
                    .insert(Message.raw(" | climate=").color(Color.GRAY)).insert(Message.raw(cropData.getClimate().name()).color(Color.MAGENTA))
                    .insert(Message.raw(" | lunar=").color(Color.GRAY)).insert(Message.raw(cropData.getLunar().name()).color(Color.MAGENTA))
                    .insert(Message.raw(" | rarity=").color(Color.GRAY)).insert(Message.raw(cropData.getRarity().name()).color(Color.MAGENTA))
                    .insert(Message.raw(" | lastRegular=").color(Color.GRAY)).insert(Message.raw(String.valueOf(cropData.getLastRegularRoll())).color(Color.MAGENTA))
                    .insert(Message.raw(" | lastLunar=").color(Color.GRAY)).insert(Message.raw(String.valueOf(cropData.getLastLunarRoll())).color(Color.MAGENTA))
                    .insert(Message.raw(" | lastSpecial=").color(Color.GRAY)).insert(Message.raw(String.valueOf(cropData.getLastSpecialRoll()) + "\n").color(Color.MAGENTA));

            appendMutationDiagnostics(msg, world, blockChunk, cropData, x, y, z, blockType, farmingBlock);
        } else {
            msg.insert(Message.raw("MGHG Data: ").color(Color.GRAY))
                    .insert(Message.raw("NO\n").color(Color.YELLOW));
        }

        ctx.sendMessage(msg);
    }

    private static void appendMutationDiagnostics(
            Message msg,
            World world,
            BlockChunk blockChunk,
            MghgCropData cropData,
            int worldX,
            int worldY,
            int worldZ,
            BlockType blockType,
            FarmingBlock farmingBlock
    ) {
        if (world == null || blockChunk == null || cropData == null) {
            return;
        }
        int localX = Math.floorMod(worldX, 16);
        int localY = worldY;
        int localZ = Math.floorMod(worldZ, 16);

        Store<EntityStore> entityStore = world.getEntityStore().getStore();
        int weatherId = MghgWeatherResolver.resolveWeatherId(entityStore, blockChunk, localX, localY, localZ, false);
        int weatherIgnoreSky = MghgWeatherResolver.resolveWeatherId(entityStore, blockChunk, localX, localY, localZ, true);

        Instant now = Instant.now();
        MghgGlobalFarmEventState state = MghgFarmEventScheduler.getState();
        boolean eventActive = state != null && state.isActive(now);
        MutationEventType eventType = MutationEventType.WEATHER;
        String eventWeather = "-";
        if (eventActive && state != null) {
            if (state.eventType() != null) {
                eventType = state.eventType();
            }
            if (state.weatherId() != null && !state.weatherId().isBlank()) {
                eventWeather = state.weatherId();
            }
        }
        int weatherFromEvent = eventActive && state != null
                ? MghgWeatherIdUtil.resolveWeatherIndex(state.weatherId())
                : Integer.MIN_VALUE;
        int effectiveWeather = weatherFromEvent != Integer.MIN_VALUE ? weatherFromEvent : weatherId;

        boolean mature = isMature(blockType, farmingBlock);
        boolean ownerOnline = MghgFarmEventScheduler.isFarmOwnerOnline(world);

        MghgMutationContext context = new MghgMutationContext(
                now,
                eventType,
                effectiveWeather,
                weatherIgnoreSky,
                mature,
                ownerOnline,
                Collections.emptySet(),
                null,
                world.getWorldConfig().getUuid(),
                worldX,
                worldY,
                worldZ,
                Byte.toUnsignedInt(blockChunk.getSkyLight(localX, localY, localZ)),
                Short.toUnsignedInt(blockChunk.getBlockLight(localX, localY, localZ)),
                Byte.toUnsignedInt(blockChunk.getBlockLightIntensity(localX, localY, localZ)),
                Byte.toUnsignedInt(blockChunk.getRedBlockLight(localX, localY, localZ)),
                Byte.toUnsignedInt(blockChunk.getGreenBlockLight(localX, localY, localZ)),
                Byte.toUnsignedInt(blockChunk.getBlueBlockLight(localX, localY, localZ)),
                -1,
                -1.0d,
                -1,
                -1,
                null,
                null
        );

        MghgCropGrowthModifierAsset cfg = MghgCropGrowthModifierAsset.getLastLoaded();
        MghgMutationRuleSet rules = MghgMutationRules.getActive(cfg);
        int defaultCooldown = cfg == null ? 0 : Math.max(0, cfg.getMutationRollCooldownSeconds());

        int climateRules = 0;
        int eventMatched = 0;
        int requirementsMatched = 0;
        int cooldownReady = 0;
        String firstRuleStatus = null;

        if (rules != null && rules.getRules() != null) {
            for (MghgMutationRule rule : rules.getRules()) {
                if (rule == null) {
                    continue;
                }
                if (rule.getSlot() != MutationSlot.CLIMATE) {
                    continue;
                }
                climateRules++;
                boolean eventOk = rule.matchesEvent(context);
                if (eventOk) {
                    eventMatched++;
                }
                boolean reqOk = eventOk && rule.matchesRequirements(cropData, context);
                if (reqOk) {
                    requirementsMatched++;
                    if (isCooldownReady(cropData.getLastRegularRoll(), rule.getCooldownSecondsOrDefault(defaultCooldown), now)) {
                        cooldownReady++;
                    }
                }
                if (firstRuleStatus == null) {
                    firstRuleStatus = String.format(
                            Locale.ROOT,
                            "%s[event=%s req=%s cd=%s]",
                            rule.getId() == null ? "<no-id>" : rule.getId(),
                            eventOk,
                            reqOk,
                            reqOk && isCooldownReady(cropData.getLastRegularRoll(), rule.getCooldownSecondsOrDefault(defaultCooldown), now)
                    );
                }
            }
        }

        msg.insert(Message.raw("MutationDebug: ").color(Color.GRAY))
                .insert(Message.raw(String.format(
                        Locale.ROOT,
                        "eventActive=%s eventType=%s eventWeather=%s\n",
                        eventActive,
                        eventType,
                        eventWeather
                )).color(Color.CYAN));
        msg.insert(Message.raw("MutationWeather: ").color(Color.GRAY))
                .insert(Message.raw(String.format(
                        Locale.ROOT,
                        "resolved=%d ignoreSky=%d fromEvent=%d effective=%d\n",
                        weatherId,
                        weatherIgnoreSky,
                        weatherFromEvent,
                        effectiveWeather
                )).color(Color.CYAN));
        msg.insert(Message.raw("MutationRules: ").color(Color.GRAY))
                .insert(Message.raw(String.format(
                        Locale.ROOT,
                        "climateRules=%d eventMatched=%d reqMatched=%d cooldownReady=%d first=%s\n",
                        climateRules,
                        eventMatched,
                        requirementsMatched,
                        cooldownReady,
                        firstRuleStatus == null ? "-" : firstRuleStatus
                )).color(Color.CYAN));
    }

    private static boolean isCooldownReady(Instant lastRoll, int cooldownSeconds, Instant now) {
        if (cooldownSeconds <= 0) {
            return true;
        }
        if (lastRoll == null || now == null) {
            return true;
        }
        Duration since = Duration.between(lastRoll, now);
        if (since.isNegative()) {
            return true;
        }
        return since.getSeconds() >= cooldownSeconds;
    }

    private static boolean isMature(BlockType blockType, FarmingBlock farmingBlock) {
        if (blockType == null) {
            return false;
        }
        BlockType base = MghgBlockIdUtil.resolveBaseBlockType(blockType);
        if (farmingBlock != null
                && base != null
                && base.getFarming() != null
                && base.getFarming().getStages() != null) {
            String stageSet = farmingBlock.getCurrentStageSet();
            FarmingStageData[] stages = stageSet == null ? null : base.getFarming().getStages().get(stageSet);
            if (stages == null || stages.length == 0) {
                return false;
            }
            return ((int) farmingBlock.getGrowthProgress()) >= stages.length - 1;
        }
        String id = blockType.getId();
        return id != null && id.toLowerCase(Locale.ROOT).contains("_stagefinal");
    }
}
