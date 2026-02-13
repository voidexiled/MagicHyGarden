package com.voidexiled.magichygarden.commands.farm;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import org.jspecify.annotations.NonNull;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Locale;

public final class FarmCommandArgs {
    private FarmCommandArgs() {
    }

    public static @NonNull String[] parseSubCommandArgs(@NonNull CommandContext ctx, @NonNull String subCommandName) {
        String input = ctx.getInputString();
        if (input == null || input.isBlank()) {
            return new String[0];
        }
        String[] tokens = input.trim().split("\\s+");
        int index = -1;
        for (int i = 0; i < tokens.length; i++) {
            String token = normalizeToken(tokens[i], i == 0);
            if (normalize(subCommandName).equals(token)) {
                index = i;
            }
        }
        if (index < 0 || index + 1 >= tokens.length) {
            return new String[0];
        }
        return Arrays.copyOfRange(tokens, index + 1, tokens.length);
    }

    public static @NonNull String[] parseCalledCommandArgs(@NonNull CommandContext ctx) {
        String commandName = ctx.getCalledCommand() == null ? null : ctx.getCalledCommand().getName();
        if (commandName == null || commandName.isBlank()) {
            return new String[0];
        }
        return parseSubCommandArgs(ctx, commandName);
    }

    public static @NonNull String normalize(@Nullable String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    private static @NonNull String normalizeToken(@Nullable String raw, boolean first) {
        if (raw == null) {
            return "";
        }
        String value = raw.trim();
        if (first && value.startsWith("/")) {
            value = value.substring(1);
        }
        return normalize(value);
    }
}
