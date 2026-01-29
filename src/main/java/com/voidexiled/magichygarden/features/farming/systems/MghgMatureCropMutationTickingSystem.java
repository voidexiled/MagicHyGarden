package com.voidexiled.magichygarden.features.farming.systems;

import com.hypixel.hytale.builtin.adventure.farming.states.FarmingBlock;
import com.hypixel.hytale.builtin.weather.resources.WeatherResource;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.weather.config.Weather;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.meta.BlockState;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.voidexiled.magichygarden.features.farming.components.MghgCropData;
import com.voidexiled.magichygarden.features.farming.state.ClimateMutation;
import com.voidexiled.magichygarden.features.farming.visuals.MghgCropVisualStateResolver;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

public class MghgMatureCropMutationTickingSystem extends EntityTickingSystem<ChunkStore> {

    private final Query<ChunkStore> query;

    private final ComponentType<ChunkStore, FarmingBlock> farmingBlockType;
    private final ComponentType<ChunkStore, MghgCropData> cropDataType;

    private final int mutationRollCooldownSeconds;
    private final double mutationChanceRain;
    private final double mutationChanceSnow;
    private final double mutationChanceFrozen;

    private final IntOpenHashSet rainWeatherIds = new IntOpenHashSet();
    private final IntOpenHashSet snowWeatherIds = new IntOpenHashSet();
    private final IntOpenHashSet frozenWeatherIds = new IntOpenHashSet();

    public MghgMatureCropMutationTickingSystem(
            ComponentType<ChunkStore, FarmingBlock> farmingBlockType,
            ComponentType<ChunkStore, MghgCropData> cropDataType,
            int mutationRollCooldownSeconds,
            double mutationChanceRain,
            double mutationChanceSnow,
            double mutationChanceFrozen,
            String[] rainWeatherIds,
            String[] snowWeatherIds,
            String[] frozenWeatherIds
    ) {
        this.farmingBlockType = farmingBlockType;
        this.cropDataType = cropDataType;

        this.mutationRollCooldownSeconds = mutationRollCooldownSeconds;
        this.mutationChanceRain = mutationChanceRain;
        this.mutationChanceSnow = mutationChanceSnow;
        this.mutationChanceFrozen = mutationChanceFrozen;

        if (rainWeatherIds != null) {
            for (String id : rainWeatherIds) this.rainWeatherIds.add(Weather.getAssetMap().getIndex(id));
        }
        if (snowWeatherIds != null) {
            for (String id : snowWeatherIds) this.snowWeatherIds.add(Weather.getAssetMap().getIndex(id));
        }
        if (frozenWeatherIds != null) {
            for (String id : frozenWeatherIds) this.frozenWeatherIds.add(Weather.getAssetMap().getIndex(id));
        }

        this.query = Query.and(
                BlockModule.BlockStateInfo.getComponentType(),
                this.cropDataType
        );
    }

    @Override
    public Query<ChunkStore> getQuery() {
        return query;
    }

    @Override
    public void tick(float dt, int index, ArchetypeChunk<ChunkStore> archetypeChunk, Store<ChunkStore> store, CommandBuffer<ChunkStore> commandBuffer) {
        Ref<ChunkStore> ref = archetypeChunk.getReferenceTo(index);

        // solo maduros persistentes: si aún existe FarmingBlock, lo maneja el GrowthModifier
        if (commandBuffer.getComponent(ref, farmingBlockType) != null) {
            return;
        }

        MghgCropData data = archetypeChunk.getComponent(index, cropDataType);
        BlockModule.BlockStateInfo info = archetypeChunk.getComponent(index, BlockModule.BlockStateInfo.getComponentType());
        if (data == null || info == null) return;

        Ref<ChunkStore> chunkRef = info.getChunkRef();
        if (chunkRef == null || !chunkRef.isValid()) return;

        BlockChunk blockChunk = store.getComponent(chunkRef, BlockChunk.getComponentType());
        if (blockChunk == null) return;

        BlockComponentChunk blockComponentChunk = store.getComponent(chunkRef, BlockComponentChunk.getComponentType());

        int idx = info.getIndex();
        int x = com.hypixel.hytale.math.util.ChunkUtil.xFromBlockInColumn(idx);
        int y = com.hypixel.hytale.math.util.ChunkUtil.yFromBlockInColumn(idx);
        int z = com.hypixel.hytale.math.util.ChunkUtil.zFromBlockInColumn(idx);

        Store<EntityStore> entityStore = commandBuffer.getExternalData().getWorld().getEntityStore().getStore();
        Instant now = entityStore.getResource(WorldTimeResource.getResourceType()).getGameTime();

        // siempre “sync visual” (corrige mismatch aunque no haya cooldown)
        Instant last = data.getLastMutationRoll();
        if (last != null && mutationRollCooldownSeconds > 0) {
            Duration since = Duration.between(last, now);
            if (since.getSeconds() < mutationRollCooldownSeconds) {
                applyFinalVisual(store, commandBuffer, chunkRef, blockChunk, x, y, z, data);
                return;
            }
        }

        int weatherMask = getWeatherMask(entityStore, blockChunk, x, y, z);
        boolean raining = (weatherMask & 1) != 0;
        boolean snowing = (weatherMask & 2) != 0;

        // si no llueve ni nieva, no “gastamos” cooldown, pero sí aseguramos visual
        if (!raining && !snowing) {
            applyFinalVisual(store, commandBuffer, chunkRef, blockChunk, x, y, z, data);
            return;
        }

        boolean dirty = false;

        data.setLastMutationRoll(now);
        dirty = true;

        ClimateMutation before = data.getClimate();
        ClimateMutation after = computeNextClimate(before, raining, snowing);

        if (after != before) {
            data.setClimate(after);
            dirty = true;
        }

        if (dirty) {
            if (blockComponentChunk != null) blockComponentChunk.markNeedsSaving();
            blockChunk.markNeedsSaving();
        }

        applyFinalVisual(store, commandBuffer, chunkRef, blockChunk, x, y, z, data);
    }

    private int getWeatherMask(Store<EntityStore> entityStore, BlockChunk blockChunk, int x, int y, int z) {
        // sky-check similar al del GrowthModifier
        int cropId = blockChunk.getBlock(x, y, z);
        for (int ay = y + 1; ay < 320; ay++) {
            int above = blockChunk.getBlock(x, ay, z);
            if (above != 0 && above != cropId) {
                return 0;
            }
        }

        WeatherResource weatherResource = entityStore.getResource(WeatherResource.getResourceType());

        int forced = weatherResource.getForcedWeatherIndex();
        int weatherId;
        if (forced != Weather.UNKNOWN_ID) {
            weatherId = forced;
        } else {
            int envId = blockChunk.getEnvironment(x, y, z);
            weatherId = weatherResource.getWeatherIndexForEnvironment(envId);
        }

        if (weatherId == Weather.UNKNOWN_ID) return 0;

        boolean isRain = rainWeatherIds.contains(weatherId);
        boolean isSnow = snowWeatherIds.contains(weatherId);
        boolean isFrozen = frozenWeatherIds.contains(weatherId);

        boolean raining = isRain || isFrozen;
        boolean snowing = isSnow || isFrozen;

        int mask = 0;
        if (raining) mask |= 1;
        if (snowing) mask |= 2;
        return mask;
    }

    private ClimateMutation computeNextClimate(ClimateMutation current, boolean raining, boolean snowing) {
        if (current == null) current = ClimateMutation.NONE;
        if (current == ClimateMutation.FROZEN) return ClimateMutation.FROZEN;

        ThreadLocalRandom rnd = ThreadLocalRandom.current();

        if (current == ClimateMutation.RAIN) {
            if (snowing && mutationChanceFrozen > 0.0 && rnd.nextDouble() <= mutationChanceFrozen) {
                return ClimateMutation.FROZEN;
            }
            return ClimateMutation.RAIN;
        }

        if (current == ClimateMutation.SNOW) {
            if (raining && mutationChanceFrozen > 0.0 && rnd.nextDouble() <= mutationChanceFrozen) {
                return ClimateMutation.FROZEN;
            }
            return ClimateMutation.SNOW;
        }

        // current == NONE
        if (raining && !snowing) {
            if (mutationChanceRain > 0.0 && rnd.nextDouble() <= mutationChanceRain) return ClimateMutation.RAIN;
            return ClimateMutation.NONE;
        }

        if (snowing && !raining) {
            if (mutationChanceSnow > 0.0 && rnd.nextDouble() <= mutationChanceSnow) return ClimateMutation.SNOW;
            return ClimateMutation.NONE;
        }

        if (raining && snowing) {
            boolean firstRain = rnd.nextBoolean();
            if (firstRain) {
                if (mutationChanceRain > 0.0 && rnd.nextDouble() <= mutationChanceRain) return ClimateMutation.RAIN;
                if (mutationChanceSnow > 0.0 && rnd.nextDouble() <= mutationChanceSnow) return ClimateMutation.SNOW;
            } else {
                if (mutationChanceSnow > 0.0 && rnd.nextDouble() <= mutationChanceSnow) return ClimateMutation.SNOW;
                if (mutationChanceRain > 0.0 && rnd.nextDouble() <= mutationChanceRain) return ClimateMutation.RAIN;
            }
        }

        return ClimateMutation.NONE;
    }

    private void applyFinalVisual(Store<ChunkStore> store,
                                  CommandBuffer<ChunkStore> commandBuffer,
                                  Ref<ChunkStore> chunkRef,
                                  BlockChunk blockChunk,
                                  int x, int y, int z,
                                  MghgCropData data) {

        int currentId = blockChunk.getBlock(x, y, z);
        if (currentId == 0) return;

        BlockType currentType = BlockType.getAssetMap().getAsset(currentId);
        if (currentType == null) return;

        // siempre final stage
        String targetState = MghgCropVisualStateResolver.resolveVariantKey(data) + "_stagefinal";
        BlockType targetType = currentType.getBlockForState(targetState);
        if (targetType == null) return;

        int targetId = BlockType.getAssetMap().getIndex(targetType.getId());
        if (targetId == BlockType.UNKNOWN_ID || targetId == currentId) return;

        WorldChunk worldChunk = store.getComponent(chunkRef, WorldChunk.getComponentType());
        if (worldChunk == null) return;

        // ✅ evitar deprecated getRotationIndex(x,y,z)
        //int indexBlock = ChunkUtil.indexBlock(x, y, z);

        BlockState state = worldChunk.getState(x, y, z);
        int rotation = state != null ? state.getRotationIndex() : 0;

        blockChunk.markNeedsSaving();

        commandBuffer.getExternalData().getWorld().execute(() ->
                worldChunk.setBlock(x, y, z, targetId, targetType, rotation, 0, 2)
        );
    }
}
