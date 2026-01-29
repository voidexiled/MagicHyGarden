package com.voidexiled.magichygarden.features.farming.components;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.EnumCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.voidexiled.magichygarden.MagicHyGardenPlugin;
import com.voidexiled.magichygarden.features.farming.state.ClimateMutation;
import com.voidexiled.magichygarden.features.farming.state.RarityMutation;

import javax.annotation.Nullable;
import java.time.Instant;

public class MghgCropData implements Component<ChunkStore> {

    public static final BuilderCodec<MghgCropData> CODEC = BuilderCodec.builder(MghgCropData.class, MghgCropData::new)
            .append(
                    new KeyedCodec<>("Size", Codec.INTEGER),
                    (c, v) -> c.size = v == null ? 0 : v,
                    c -> c.size == 0 ? null : c.size
            ).add()
            .append(
                    new KeyedCodec<>("Climate", new EnumCodec<>(ClimateMutation.class)),
                    (c, v) -> c.climate = v == null ? ClimateMutation.NONE : v,
                    c -> c.climate == ClimateMutation.NONE ? null : c.climate
            ).add()
            .append(
                    new KeyedCodec<>("Rarity", new EnumCodec<>(RarityMutation.class)),
                    (c, v) -> c.rarity = v == null ? RarityMutation.NONE : v,
                    c -> c.rarity == RarityMutation.NONE ? null : c.rarity
            ).add()
            .append(
                    new KeyedCodec<>("LastMutationRoll", Codec.INSTANT),
                    (c, v) -> c.lastMutationRoll = v,
                    c -> c.lastMutationRoll
            ).add()
            .build();

    private int size; // 50..100 (0 = unset)
    private ClimateMutation climate = ClimateMutation.NONE;
    private RarityMutation rarity = RarityMutation.NONE;

    @Nullable
    private Instant lastMutationRoll;

    public static ComponentType<ChunkStore, MghgCropData> getComponentType() {
        return MagicHyGardenPlugin.get().getMghgCropDataComponentType();
    }

    public MghgCropData() { }

    public MghgCropData(int size, ClimateMutation climate, RarityMutation rarity, @Nullable Instant lastMutationRoll) {
        this.size = size;
        this.climate = climate == null ? ClimateMutation.NONE : climate;
        this.rarity = rarity == null ? RarityMutation.NONE : rarity;
        this.lastMutationRoll = lastMutationRoll;
    }

    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }

    public ClimateMutation getClimate() { return climate; }
    public void setClimate(ClimateMutation climate) { this.climate = climate == null ? ClimateMutation.NONE : climate; }

    public RarityMutation getRarity() { return rarity; }
    public void setRarity(RarityMutation rarity) { this.rarity = rarity == null ? RarityMutation.NONE : rarity; }

    @Nullable
    public Instant getLastMutationRoll() { return lastMutationRoll; }
    public void setLastMutationRoll(@Nullable Instant lastMutationRoll) { this.lastMutationRoll = lastMutationRoll; }

    @Nullable
    @Override
    public Component<ChunkStore> clone() {
        return new MghgCropData(this.size, this.climate, this.rarity, this.lastMutationRoll);
    }
}
