package com.voidexiled.magichygarden.features.farming.logic;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.BlockHarvestUtils;
import com.voidexiled.magichygarden.features.farming.components.MghgCropData;
import com.voidexiled.magichygarden.features.farming.modifiers.MghgCropGrowthModifierAsset;
import com.voidexiled.magichygarden.features.farming.state.RarityMutation;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Centraliza overrides de droplist y drops extra por rareza.
 * Se configura en Size.json via MghgCropGrowthModifierAsset.
 */
public final class MghgDropListResolver {

    private MghgDropListResolver() {
    }

    @Nullable
    public static String resolveDropListId(@Nullable String dropListId) {
        MghgCropGrowthModifierAsset cfg = MghgCropGrowthModifierAsset.getLastLoaded();
        if (cfg == null) {
            return dropListId;
        }
        return cfg.resolveDropListId(dropListId);
    }

    @Nonnull
    public static List<ItemStack> collectExtraDrops(
            @Nonnull BlockType blockType,
            @Nullable MghgCropData data
    ) {
        List<ItemStack> out = new ArrayList<>();
        if (data == null) {
            return out;
        }

        MghgCropGrowthModifierAsset cfg = MghgCropGrowthModifierAsset.getLastLoaded();
        if (cfg == null) {
            return out;
        }

        appendFrom(out, blockType, cfg.getExtraDropListsAll());

        RarityMutation rarity = data.getRarity();
        if (rarity == RarityMutation.GOLD) {
            appendFrom(out, blockType, cfg.getExtraDropListsGold());
        } else if (rarity == RarityMutation.RAINBOW) {
            appendFrom(out, blockType, cfg.getExtraDropListsRainbow());
        }

        return out;
    }

    private static void appendFrom(
            @Nonnull List<ItemStack> out,
            @Nonnull BlockType blockType,
            @Nullable String[] dropListIds
    ) {
        if (dropListIds == null || dropListIds.length == 0) {
            return;
        }

        for (String dropListId : dropListIds) {
            if (dropListId == null || dropListId.isBlank()) {
                continue;
            }
            out.addAll(BlockHarvestUtils.getDrops(blockType, 1, null, dropListId));
        }
    }
}
