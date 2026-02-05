package com.voidexiled.magichygarden.features.farming.ui;

import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.ui.ItemGridSlot;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nullable;

public final class MghgCropInspectHud extends CustomUIHud {
    public static final String UI_PATH = "Hud/Mghg_CropInspect.ui";
    private static final String ROOT_DEFAULT = "#MghgCropInspectDefault";
    private static final String ROOT_GOLD = "#MghgCropInspectGold";
    private static final String ROOT_RAINBOW = "#MghgCropInspectRainbow";
    private static final String[] ROOTS = new String[]{
            ROOT_DEFAULT,
            ROOT_GOLD,
            ROOT_RAINBOW
    };

    private boolean appended;

    public MghgCropInspectHud(PlayerRef playerRef) {
        super(playerRef);
    }

    @Override
    protected void build(UICommandBuilder builder) {
        builder.append(UI_PATH);
        appended = true;
    }

    public void updateContent(
            @Nullable String activeRoot,
            @Nullable String title,
            @Nullable String subline,
            @Nullable String climate,
            @Nullable String lunar,
            @Nullable String rarity,
            @Nullable String climateColor,
            @Nullable String lunarColor,
            @Nullable String rarityColor,
            @Nullable String climateBadgeBackground,
            @Nullable String lunarBadgeBackground,
            @Nullable String rarityBadgeBackground,
            @Nullable ItemStack previewStack
    ) {
        UICommandBuilder builder = new UICommandBuilder();
        if (!appended) {
            builder.append(UI_PATH);
            appended = true;
        }

        String rootToShow = activeRoot != null ? activeRoot : ROOT_DEFAULT;
        boolean hasClimate = climate != null && !climate.isBlank();
        boolean hasLunar = lunar != null && !lunar.isBlank();
        boolean hasRarity = rarity != null && !rarity.isBlank();

        for (String root : ROOTS) {
            boolean isActive = root.equals(rootToShow);
            builder.set(root + ".Visible", isActive);
            builder.set(root + " #Title.Text", title != null ? title : "");
            builder.set(root + " #Subline.Text", subline != null ? subline : "");

            builder.set(root + " #ClimateBadge.Visible", hasClimate);
            builder.set(root + " #LunarBadge.Visible", hasLunar);
            builder.set(root + " #RarityBadge.Visible", hasRarity);

            builder.set(root + " #ClimateLabel.Text", hasClimate ? climate : "");
            builder.set(root + " #LunarLabel.Text", hasLunar ? lunar : "");
            builder.set(root + " #RarityLabel.Text", hasRarity ? rarity : "");

            if (climateColor != null && !climateColor.isBlank()) {
                builder.set(root + " #ClimateLabel.Style.TextColor", climateColor);
            }
            if (lunarColor != null && !lunarColor.isBlank()) {
                builder.set(root + " #LunarLabel.Style.TextColor", lunarColor);
            }
            if (rarityColor != null && !rarityColor.isBlank()) {
                builder.set(root + " #RarityLabel.Style.TextColor", rarityColor);
            }
            if (previewStack != null) {
                builder.set(root + " #PreviewSlot.Slots", new ItemGridSlot[]{
                        new ItemGridSlot(previewStack)
                });
            } else {
                builder.set(root + " #PreviewSlot.Slots", new ItemGridSlot[0]);
            }
        }

        update(false, builder);
    }

    public void hide() {
        if (!appended) {
            return;
        }
        UICommandBuilder builder = new UICommandBuilder();
        for (String root : ROOTS) {
            builder.set(root + ".Visible", false);
            builder.set(root + " #Title.Text", "");
            builder.set(root + " #Subline.Text", "");
            builder.set(root + " #ClimateLabel.Text", "");
            builder.set(root + " #LunarLabel.Text", "");
            builder.set(root + " #RarityLabel.Text", "");
            builder.set(root + " #ClimateBadge.Visible", false);
            builder.set(root + " #LunarBadge.Visible", false);
            builder.set(root + " #RarityBadge.Visible", false);
            builder.set(root + " #PreviewSlot.Slots", new ItemGridSlot[0]);
        }
        update(false, builder);
    }
}
