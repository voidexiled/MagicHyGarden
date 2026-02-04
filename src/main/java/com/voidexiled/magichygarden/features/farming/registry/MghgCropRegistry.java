package com.voidexiled.magichygarden.features.farming.registry;

import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.codec.util.RawJsonReader;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class MghgCropRegistry {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String RESOURCE_PATH = "Server/Farming/Crops/Mghg_Crops.json";

    private static volatile Map<String, MghgCropDefinition> BY_BLOCK = new ConcurrentHashMap<>();
    private static volatile Map<String, MghgCropDefinition> BY_ITEM = new ConcurrentHashMap<>();

    private MghgCropRegistry() {
    }

    public static void reload() {
        Map<String, MghgCropDefinition> byBlock = new HashMap<>();
        Map<String, MghgCropDefinition> byItem = new HashMap<>();

        MghgCropRegistryConfig config = loadConfig();
        if (config != null && config.getDefinitions() != null) {
            for (MghgCropDefinition def : config.getDefinitions()) {
                if (def == null) {
                    continue;
                }
                def.normalize();
                if (def.getBlockId() == null || def.getBlockId().isBlank()) {
                    continue;
                }
                byBlock.put(def.getBlockId(), def);
                if (def.getItemId() != null && !def.getItemId().isBlank()) {
                    byItem.put(def.getItemId(), def);
                }
            }
        }

        BY_BLOCK = new ConcurrentHashMap<>(byBlock);
        BY_ITEM = new ConcurrentHashMap<>(byItem);
        LOGGER.atInfo().log("[MGHG|CROP_REGISTRY] Loaded crops: %d", BY_BLOCK.size());
        if (BY_BLOCK.isEmpty()) {
            LOGGER.atWarning().log("[MGHG|CROP_REGISTRY] No crops loaded. Check build/resources/main/Server/Farming/Crops/Mghg_Crops.json");
        }
    }

    @Nullable
    public static MghgCropDefinition getDefinition(@Nullable BlockType blockType) {
        if (blockType == null) {
            return null;
        }
        return getDefinitionByBlockId(blockType.getId());
    }

    @Nullable
    public static MghgCropDefinition getDefinitionByBlockId(@Nullable String blockId) {
        if (blockId == null || blockId.isBlank()) {
            return null;
        }
        MghgCropDefinition direct = BY_BLOCK.get(blockId);
        if (direct != null) {
            return direct;
        }
        String base = normalizeStateAssetId(blockId);
        if (base != null) {
            MghgCropDefinition baseDef = BY_BLOCK.get(base);
            if (baseDef != null) {
                return baseDef;
            }
            MghgCropDefinition itemDef = BY_ITEM.get(base);
            if (itemDef != null) {
                return itemDef;
            }
        }
        String lowerId = blockId.toLowerCase();
        for (MghgCropDefinition def : BY_BLOCK.values()) {
            if (def == null || def.getBlockId() == null) {
                continue;
            }
            String baseId = def.getBlockId();
            if (baseId == null || baseId.isBlank()) {
                continue;
            }
            if (lowerId.contains(baseId.toLowerCase())) {
                return def;
            }
        }
        return null;
    }

    public static boolean isMghgCropBlock(@Nullable BlockType blockType) {
        if (blockType == null) {
            return false;
        }
        if (getDefinition(blockType) != null) {
            return true;
        }
        String blockId = blockType.getId();
        String base = normalizeStateAssetId(blockId);
        if (base != null && BY_ITEM.containsKey(base)) {
            return true;
        }
        if (blockType.getItem() != null) {
            String itemId = blockType.getItem().getId();
            if (itemId != null && BY_ITEM.containsKey(itemId)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isMghgCropItem(@Nullable String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return false;
        }
        if (BY_ITEM.containsKey(itemId)) {
            return true;
        }
        String base = normalizeStateAssetId(itemId);
        if (base != null && BY_ITEM.containsKey(base)) {
            return true;
        }
        return false;
    }

    @Nullable
    private static String normalizeStateAssetId(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        int idx = id.indexOf("_State_");
        if (idx <= 0) {
            return null;
        }
        if (id.charAt(0) == '*') {
            return idx > 1 ? id.substring(1, idx) : null;
        }
        return id.substring(0, idx);
    }

    @Nullable
    private static MghgCropRegistryConfig loadConfig() {
        Path buildPath = Paths.get("build", "resources", "main", "Server", "Farming", "Crops", "Mghg_Crops.json");
        if (Files.exists(buildPath)) {
            try {
                String payload = Files.readString(buildPath, StandardCharsets.UTF_8);
                RawJsonReader json = RawJsonReader.fromJsonString(payload);
                return MghgCropRegistryConfig.CODEC.decodeJson(json, new ExtraInfo());
            } catch (IOException e) {
                LOGGER.atWarning().log("[MGHG|CROP_REGISTRY] Failed to load crop registry from build/resources: %s", e.getMessage());
            }
        }

        try (InputStream stream = MghgCropRegistry.class.getClassLoader().getResourceAsStream(RESOURCE_PATH)) {
            if (stream != null) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                    StringBuilder payload = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        payload.append(line).append('\n');
                    }
                    RawJsonReader json = RawJsonReader.fromJsonString(payload.toString());
                    return MghgCropRegistryConfig.CODEC.decodeJson(json, new ExtraInfo());
                }
            }
        } catch (IOException e) {
            LOGGER.atWarning().log("[MGHG|CROP_REGISTRY] Failed to load crop registry from classpath: %s", e.getMessage());
        }

        LOGGER.atWarning().log("[MGHG|CROP_REGISTRY] No config found at %s or %s", RESOURCE_PATH, buildPath.toString());
        return null;
    }
}
