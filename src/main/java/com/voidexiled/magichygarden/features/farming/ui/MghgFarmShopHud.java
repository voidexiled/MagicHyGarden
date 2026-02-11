package com.voidexiled.magichygarden.features.farming.ui;

import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nullable;

public final class MghgFarmShopHud extends CustomUIHud {
    public static final String UI_PATH = "Hud/Mghg_FarmShop.ui";
    private static final String ROOT = "#MghgFarmShop";
    private static final int MAX_LINES = 8;

    private boolean appended;

    public MghgFarmShopHud(PlayerRef playerRef) {
        super(playerRef);
    }

    @Override
    protected void build(UICommandBuilder builder) {
        builder.append(UI_PATH);
        appended = true;
    }

    public void updateContent(
            @Nullable String title,
            @Nullable String balance,
            @Nullable String restock,
            @Nullable String[] lines,
            @Nullable String footer
    ) {
        UICommandBuilder builder = new UICommandBuilder();
        if (!appended) {
            builder.append(UI_PATH);
            appended = true;
        }

        builder.set(ROOT + ".Visible", true);
        builder.set(ROOT + " #Title.Text", safe(title));
        builder.set(ROOT + " #Balance.Text", safe(balance));
        builder.set(ROOT + " #Restock.Text", safe(restock));
        for (int i = 0; i < MAX_LINES; i++) {
            String line = (lines != null && i < lines.length) ? lines[i] : "";
            builder.set(ROOT + " #Line" + i + ".Text", safe(line));
            builder.set(ROOT + " #Line" + i + ".Visible", !safe(line).isBlank());
        }
        builder.set(ROOT + " #Footer.Text", safe(footer));
        update(false, builder);
    }

    public void hide() {
        if (!appended) {
            return;
        }
        UICommandBuilder builder = new UICommandBuilder();
        builder.set(ROOT + ".Visible", false);
        builder.set(ROOT + " #Title.Text", "");
        builder.set(ROOT + " #Balance.Text", "");
        builder.set(ROOT + " #Restock.Text", "");
        for (int i = 0; i < MAX_LINES; i++) {
            builder.set(ROOT + " #Line" + i + ".Text", "");
            builder.set(ROOT + " #Line" + i + ".Visible", false);
        }
        builder.set(ROOT + " #Footer.Text", "");
        update(false, builder);
    }

    private static String safe(@Nullable String value) {
        return value == null ? "" : value;
    }
}
