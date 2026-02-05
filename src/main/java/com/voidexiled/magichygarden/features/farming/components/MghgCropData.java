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
import com.voidexiled.magichygarden.features.farming.state.LunarMutation;
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
                    new KeyedCodec<>("Lunar", new EnumCodec<>(LunarMutation.class)),
                    (c, v) -> c.lunar = v == null ? LunarMutation.NONE : v,
                    c -> c.lunar == LunarMutation.NONE ? null : c.lunar
            ).add()
            .append(
                    new KeyedCodec<>("Rarity", new EnumCodec<>(RarityMutation.class)),
                    (c, v) -> c.rarity = v == null ? RarityMutation.NONE : v,
                    c -> c.rarity == RarityMutation.NONE ? null : c.rarity
            ).add()
            .append(
                    new KeyedCodec<>("WeightGrams", Codec.DOUBLE),
                    (c, v) -> c.weightGrams = v == null ? 0.0 : v,
                    c -> c.weightGrams <= 0 ? null : c.weightGrams
            ).add()
            .append(
                    new KeyedCodec<>("LastRegularRoll", Codec.INSTANT),
                    (c, v) -> c.lastRegularRoll = v,
                    c -> c.lastRegularRoll
            ).add()
            .append(
                    new KeyedCodec<>("LastLunarRoll", Codec.INSTANT),
                    (c, v) -> c.lastLunarRoll = v,
                    c -> c.lastLunarRoll
            ).add()
            .append(
                    new KeyedCodec<>("LastSpecialRoll", Codec.INSTANT),
                    (c, v) -> c.lastSpecialRoll = v,
                    c -> c.lastSpecialRoll
            ).add()
            .append(
                    new KeyedCodec<>("LastMutationRoll", Codec.INSTANT),
                    (c, v) -> {
                        c.lastMutationRoll = v;
                        if (c.lastRegularRoll == null) {
                            c.lastRegularRoll = v;
                        }
                    },
                    c -> c.lastMutationRoll
            ).add()
            .build();

    private int size; // 50..100 (0 = unset)
    private ClimateMutation climate = ClimateMutation.NONE;
    private LunarMutation lunar = LunarMutation.NONE;
    private RarityMutation rarity = RarityMutation.NONE;
    private double weightGrams; // 0 = unset

    @Nullable
    private Instant lastRegularRoll;
    @Nullable
    private Instant lastLunarRoll;
    @Nullable
    private Instant lastSpecialRoll;
    @Nullable
    private Instant lastMutationRoll; // legacy

    public static ComponentType<ChunkStore, MghgCropData> getComponentType() {
        return MagicHyGardenPlugin.get().getMghgCropDataComponentType();
    }

    public MghgCropData() { }

    public MghgCropData(int size,
                        ClimateMutation climate,
                        LunarMutation lunar,
                        RarityMutation rarity,
                        double weightGrams,
                        @Nullable Instant lastRegularRoll,
                        @Nullable Instant lastLunarRoll,
                        @Nullable Instant lastSpecialRoll,
                        @Nullable Instant lastMutationRoll) {
        this.size = size;
        this.climate = climate == null ? ClimateMutation.NONE : climate;
        this.lunar = lunar == null ? LunarMutation.NONE : lunar;
        this.rarity = rarity == null ? RarityMutation.NONE : rarity;
        this.weightGrams = weightGrams;
        this.lastRegularRoll = lastRegularRoll;
        this.lastLunarRoll = lastLunarRoll;
        this.lastSpecialRoll = lastSpecialRoll;
        this.lastMutationRoll = lastMutationRoll;
    }

    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }

    public double getWeightGrams() { return weightGrams; }
    public void setWeightGrams(double weightGrams) { this.weightGrams = weightGrams; }

    public ClimateMutation getClimate() { return climate; }
    public void setClimate(ClimateMutation climate) { this.climate = climate == null ? ClimateMutation.NONE : climate; }

    public LunarMutation getLunar() { return lunar; }
    public void setLunar(LunarMutation lunar) { this.lunar = lunar == null ? LunarMutation.NONE : lunar; }

    public RarityMutation getRarity() { return rarity; }
    public void setRarity(RarityMutation rarity) { this.rarity = rarity == null ? RarityMutation.NONE : rarity; }

    @Nullable
    public Instant getLastMutationRoll() { return lastMutationRoll; }
    public void setLastMutationRoll(@Nullable Instant lastMutationRoll) {
        this.lastMutationRoll = lastMutationRoll;
        if (this.lastRegularRoll == null) {
            this.lastRegularRoll = lastMutationRoll;
        }
    }

    @Nullable
    public Instant getLastRegularRoll() {
        return lastRegularRoll != null ? lastRegularRoll : lastMutationRoll;
    }
    public void setLastRegularRoll(@Nullable Instant lastRegularRoll) { this.lastRegularRoll = lastRegularRoll; }

    @Nullable
    public Instant getLastLunarRoll() { return lastLunarRoll; }
    public void setLastLunarRoll(@Nullable Instant lastLunarRoll) { this.lastLunarRoll = lastLunarRoll; }

    @Nullable
    public Instant getLastSpecialRoll() { return lastSpecialRoll; }
    public void setLastSpecialRoll(@Nullable Instant lastSpecialRoll) { this.lastSpecialRoll = lastSpecialRoll; }

    @Nullable
    @Override
    public Component<ChunkStore> clone() {
        return new MghgCropData(
                this.size,
                this.climate,
                this.lunar,
                this.rarity,
                this.weightGrams,
                this.lastRegularRoll,
                this.lastLunarRoll,
                this.lastSpecialRoll,
                this.lastMutationRoll
        );
    }
}
