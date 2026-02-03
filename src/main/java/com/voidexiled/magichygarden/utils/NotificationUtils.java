package com.voidexiled.magichygarden.utils;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.ItemWithAllMetadata;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import com.voidexiled.magichygarden.features.farming.components.MghgCropData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NotificationUtils {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();


    private static final String COLOR_DEFAULT = "#7f51ad";
    private static final String COLOR_TEXT = "#e3d6ff";
    private static final String COLOR_GOLD = "#f5c542";
    private static final String COLOR_RAINBOW = "#ff4fd8";
    private static final String COLOR_FROZEN = "#6fb7ff";
    private static final String COLOR_WET = "#45c1ff";

    private NotificationUtils() {
    }

    /**
     * Variante simple (sin nombre explícito). El ícono ya representa el item.
     */
    public static void sendNotification(
            @NotNull PlayerRef playerRef,
            @NotNull MghgCropData cropData,
            ItemWithAllMetadata itemIconPacket
    ) {
        sendNotification(playerRef, null, cropData, itemIconPacket);
    }

    /**
     * Variante recomendada si quieres mostrar el nombre del item (ej. "Lettuce").
     */
    public static void sendNotification(
            @NotNull PlayerRef playerRef,
            @Nullable String itemName,
            @NotNull MghgCropData cropData,
            ItemWithAllMetadata itemIconPacket
    ) {
        var packetHandler = playerRef.getPacketHandler();

        Message title = resolveTitleNotification(itemName, cropData);
        Message body = resolveBodyNotification(cropData);

        NotificationUtil.sendNotification(
                packetHandler,
                title,
                body,
                itemIconPacket
        );
    }

    private static Message resolveTitleNotification(@Nullable String itemName, @NotNull MghgCropData cropData) {
        LOGGER.atInfo().log("Resolving title notification for item: " + itemName);
        String rarityLabel = resolveRarityLabel(cropData);
        String namePart = (itemName == null || itemName.isBlank()) ? "Crop" : itemName.trim();

        // Ej: "Obtained Lettuce (Rainbow)"
        StringBuilder sb = new StringBuilder("Obtained ").append(namePart);
        if (!rarityLabel.isBlank()) {
            sb.append(" (").append(rarityLabel).append(")");
        }

        return Message.raw(sb.toString()).color(resolveTitleColor(cropData));
    }

    private static Message resolveBodyNotification(@NotNull MghgCropData cropData) {
        // Ej: "Giant • Wet • Rainbow • Size 100"
        String sizeTier = resolveSizeTierLabel(cropData.getSize());
        String climateLabel = resolveClimateLabel(cropData);
        String lunarLabel = resolveLunarLabel(cropData);
        String rarityLabel = resolveRarityLabel(cropData);

        StringBuilder sb = new StringBuilder();

        if (!sizeTier.isBlank()) sb.append(sizeTier);
        if (!climateLabel.isBlank()) appendBullet(sb, climateLabel);
        if (!lunarLabel.isBlank()) appendBullet(sb, lunarLabel);
        if (!rarityLabel.isBlank()) appendBullet(sb, rarityLabel);

        appendBullet(sb, "Size " + cropData.getSize());

        return Message.raw(sb.toString()).color(COLOR_TEXT);
    }

    private static void appendBullet(StringBuilder sb, String text) {
        if (sb.length() > 0) sb.append(" \u2022 ");
        sb.append(text);
    }

    private static String resolveSizeTierLabel(int size) {
        // Tus thresholds (corrigiendo el duplicado de "Large (50+)")
        if (size >= 100) return "Giant";
        if (size >= 95) return "Huge";
        if (size >= 75) return "Large";
        if (size >= 50) return "Normal";
        return "Small";
    }

    private static String resolveRarityLabel(@NotNull MghgCropData cropData) {
        // No asumimos enum; usamos toString seguro.
        String raw = String.valueOf(cropData.getRarity()).trim();
        if (raw.equalsIgnoreCase("NONE")) return "";
        return raw; // "GOLD", "RAINBOW", etc.
    }

    private static String resolveClimateLabel(@NotNull MghgCropData cropData) {
        String raw = String.valueOf(cropData.getClimate()).trim();
        if (raw.equalsIgnoreCase("NONE")) return "";

        // Si tu enum usa RAIN/SNOW/FROZEN o combinaciones, aquí lo dejas tal cual.
        // Si quieres nombres “bonitos”, puedes mapearlos:
        if (raw.equalsIgnoreCase("RAIN")) return "Wet";
        if (raw.equalsIgnoreCase("SNOW")) return "Chilled";
        if (raw.equalsIgnoreCase("FROZEN")) return "Frozen";

        return raw;
    }

    private static String resolveLunarLabel(@NotNull MghgCropData cropData) {
        String raw = String.valueOf(cropData.getLunar()).trim();
        if (raw.equalsIgnoreCase("NONE")) return "";
        return raw;
    }

    private static String resolveTitleColor(@NotNull MghgCropData cropData) {
        String rarity = String.valueOf(cropData.getRarity()).trim();
        if (rarity.equalsIgnoreCase("RAINBOW")) return COLOR_RAINBOW;
        if (rarity.equalsIgnoreCase("GOLD")) return COLOR_GOLD;

        // Si está congelado o mojado, puedes priorizar color temático si no hay rareza:
        String climate = String.valueOf(cropData.getClimate()).trim();
        if (climate.equalsIgnoreCase("FROZEN")) return COLOR_FROZEN;
        if (climate.equalsIgnoreCase("RAIN")) return COLOR_WET;

        return COLOR_DEFAULT;
    }
}
