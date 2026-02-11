package com.voidexiled.magichygarden.features.farming.economy;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.util.RawJsonReader;
import com.hypixel.hytale.logger.HytaleLogger;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class MghgEconomyConfig {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String RESOURCE_PATH = "Server/Farming/Economy/Mghg_Economy.json";
    private static final Path BUILD_PATH = Paths.get("build", "resources", "main", "Server", "Farming", "Economy", "Mghg_Economy.json");

    public static final BuilderCodec<MghgEconomyConfig> CODEC =
            BuilderCodec.builder(MghgEconomyConfig.class, MghgEconomyConfig::new)
                    .append(new KeyedCodec<>("AutoCreateAccountOnFirstAccess", Codec.BOOLEAN, true),
                            (o, v) -> o.autoCreateAccountOnFirstAccess = v == null || v,
                            o -> o.autoCreateAccountOnFirstAccess)
                    .documentation("If true, creates a new account for players the first time economy is queried.")
                    .add()
                    .append(new KeyedCodec<>("StartingBalance", Codec.DOUBLE, true),
                            (o, v) -> o.startingBalance = v == null ? o.startingBalance : v,
                            o -> o.startingBalance)
                    .documentation("Initial balance for first-time accounts.")
                    .add()
                    .build();

    private boolean autoCreateAccountOnFirstAccess = true;
    private double startingBalance = 100.0d;

    public boolean isAutoCreateAccountOnFirstAccess() {
        return autoCreateAccountOnFirstAccess;
    }

    public double getStartingBalance() {
        return Math.max(0.0d, startingBalance);
    }

    public static MghgEconomyConfig load() {
        try {
            if (Files.exists(BUILD_PATH)) {
                String payload = Files.readString(BUILD_PATH, StandardCharsets.UTF_8);
                RawJsonReader json = RawJsonReader.fromJsonString(payload);
                return CODEC.decodeJson(json, new ExtraInfo());
            }
        } catch (Exception e) {
            LOGGER.atWarning().log("[MGHG|ECO] Failed to load economy config from build/resources: %s", e.getMessage());
        }

        try (InputStream stream = MghgEconomyConfig.class.getClassLoader().getResourceAsStream(RESOURCE_PATH)) {
            if (stream != null) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                    StringBuilder payload = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        payload.append(line).append('\n');
                    }
                    RawJsonReader json = RawJsonReader.fromJsonString(payload.toString());
                    return CODEC.decodeJson(json, new ExtraInfo());
                }
            }
        } catch (Exception e) {
            LOGGER.atWarning().log("[MGHG|ECO] Failed to load economy config from resources: %s", e.getMessage());
        }

        LOGGER.atWarning().log("[MGHG|ECO] No economy config found; using defaults.");
        return new MghgEconomyConfig();
    }
}
