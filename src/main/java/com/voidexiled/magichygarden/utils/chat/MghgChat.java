package com.voidexiled.magichygarden.utils.chat;

import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class MghgChat {
    private static final String PREFIX_TEXT = "[Magic HyGarden] ";
    private static final String PREFIX_TAG = "<b><c:#f2d896>[Magic HyGarden]</c></b> ";

    private MghgChat() {
    }

    public enum Channel {
        INFO("#d2deee"),
        SUCCESS("#8fe388"),
        WARNING("#f2d896"),
        ERROR("#f6a2a2"),
        ADMIN("#8dd5ff"),
        SYSTEM("#e9e2c4"),
        DEBUG("#b2bccb");

        private final String textColor;

        Channel(String textColor) {
            this.textColor = textColor;
        }

        public @Nonnull String textColor() {
            return textColor;
        }
    }

    public static @Nonnull Message text(@Nullable String body) {
        return format(Channel.INFO, body);
    }

    public static @Nonnull Message format(@Nonnull Channel channel, @Nullable String body) {
        Message prefix = MghgMessageTagsBridge.parse(PREFIX_TAG);
        Message output = Message.empty();
        if (prefix != null) {
            output.insert(prefix);
        } else {
            output.insert(Message.raw(PREFIX_TEXT).bold(true).color("#f2d896"));
        }
        output.insert(Message.raw(sanitize(body)).color(channel.textColor()));
        return output;
    }

    public static void info(@Nonnull CommandContext context, @Nullable String body) {
        context.sendMessage(format(Channel.INFO, body));
    }

    public static void success(@Nonnull CommandContext context, @Nullable String body) {
        context.sendMessage(format(Channel.SUCCESS, body));
    }

    public static void warning(@Nonnull CommandContext context, @Nullable String body) {
        context.sendMessage(format(Channel.WARNING, body));
    }

    public static void error(@Nonnull CommandContext context, @Nullable String body) {
        context.sendMessage(format(Channel.ERROR, body));
    }

    public static void toPlayer(@Nullable PlayerRef playerRef, @Nonnull Channel channel, @Nullable String body) {
        if (playerRef == null) {
            return;
        }
        playerRef.sendMessage(format(channel, body));
    }

    public static void toWorld(@Nullable World world, @Nonnull Channel channel, @Nullable String body) {
        if (world == null) {
            return;
        }
        for (PlayerRef playerRef : world.getPlayerRefs()) {
            if (playerRef != null && playerRef.isValid()) {
                playerRef.sendMessage(format(channel, body));
            }
        }
    }

    public static void toServer(@Nonnull Channel channel, @Nullable String body) {
        if (Universe.get() == null) {
            return;
        }

        Set<java.util.UUID> sent = new HashSet<>();
        for (World world : Universe.get().getWorlds().values()) {
            if (world == null) {
                continue;
            }
            for (PlayerRef playerRef : world.getPlayerRefs()) {
                if (playerRef == null || !playerRef.isValid()) {
                    continue;
                }
                if (!sent.add(playerRef.getUuid())) {
                    continue;
                }
                playerRef.sendMessage(format(channel, body));
            }
        }
    }

    public static void toLocal(
            @Nullable World world,
            @Nullable Vector3d center,
            double radius,
            @Nonnull Channel channel,
            @Nullable String body
    ) {
        if (world == null || center == null || radius <= 0.0d) {
            return;
        }
        double maxSquared = radius * radius;
        for (PlayerRef playerRef : world.getPlayerRefs()) {
            if (playerRef == null || !playerRef.isValid()) {
                continue;
            }
            var transform = playerRef.getTransform();
            if (transform == null || transform.getPosition() == null) {
                continue;
            }
            Vector3d pos = transform.getPosition();
            double dx = pos.x - center.x;
            double dy = pos.y - center.y;
            double dz = pos.z - center.z;
            double distSquared = (dx * dx) + (dy * dy) + (dz * dz);
            if (distSquared <= maxSquared) {
                playerRef.sendMessage(format(channel, body));
            }
        }
    }

    public static @Nonnull Message playerChatLine(
            @Nullable String rankLabel,
            @Nullable String rankColor,
            @Nullable String playerName,
            @Nullable String content
    ) {
        String cleanRank = sanitize(rankLabel);
        String cleanName = sanitize(playerName);
        String cleanContent = sanitize(content);
        String resolvedRankColor = normalizeHexColor(rankColor, "#8dd5ff");

        String tagText = cleanRank.isBlank()
                ? ""
                : "<b><c:" + resolvedRankColor + ">[" + cleanRank + "]</c></b> ";
        String template = tagText + "<b><c:#f5f8ff>" + cleanName + "</c></b> <c:#8fa0b5>></c> <c:#d2deee>" + cleanContent + "</c>";
        Message parsed = MghgMessageTagsBridge.parse(template);
        if (parsed != null) {
            return parsed;
        }

        Message fallback = Message.empty();
        if (!cleanRank.isBlank()) {
            fallback.insert(Message.raw("[" + cleanRank + "] ").bold(true).color(resolvedRankColor));
        }
        fallback.insert(Message.raw(cleanName).bold(true).color("#f5f8ff"));
        fallback.insert(Message.raw(" > ").color("#8fa0b5"));
        fallback.insert(Message.raw(cleanContent).color("#d2deee"));
        return fallback;
    }

    private static @Nonnull String sanitize(@Nullable String text) {
        if (text == null) {
            return "";
        }
        return text.replace('\r', '\n').trim();
    }

    private static @Nonnull String normalizeHexColor(@Nullable String color, @Nonnull String fallback) {
        if (color == null) {
            return fallback;
        }
        String value = color.trim().toLowerCase(Locale.ROOT);
        if (value.matches("^#[0-9a-f]{6}$")) {
            return value;
        }
        return fallback;
    }
}

