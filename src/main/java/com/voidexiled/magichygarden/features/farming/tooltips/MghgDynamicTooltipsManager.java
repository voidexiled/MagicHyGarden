package com.voidexiled.magichygarden.features.farming.tooltips;

import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.common.semver.SemverRange;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import com.voidexiled.magichygarden.features.farming.items.MghgCropMeta;
import com.voidexiled.magichygarden.features.farming.registry.MghgCropDefinition;
import com.voidexiled.magichygarden.features.farming.registry.MghgCropRegistry;
import com.voidexiled.magichygarden.features.farming.shop.MghgShopConfig;
import com.voidexiled.magichygarden.features.farming.shop.MghgShopPricing;
import com.voidexiled.magichygarden.features.farming.shop.MghgShopStockManager;
import com.voidexiled.magichygarden.features.farming.ui.MghgMutationUiPalette;
import org.bson.BsonDocument;
import org.bson.BsonValue;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class MghgDynamicTooltipsManager {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final String API_PROVIDER_CLASS = "org.herolias.tooltips.api.DynamicTooltipsApiProvider";
    private static final String API_CLASS = "org.herolias.tooltips.api.DynamicTooltipsApi";
    private static final String TOOLTIP_PROVIDER_CLASS = "org.herolias.tooltips.api.TooltipProvider";
    private static final String TOOLTIP_DATA_CLASS = "org.herolias.tooltips.api.TooltipData";
    private static final String TOOLTIP_PLUGIN_ID = "org.herolias:DynamicTooltipsLib";
    private static final String PROVIDER_ID = "mghg:shop-tooltips";
    private static final int PROVIDER_PRIORITY = 120;

    private static boolean providerRegistered = false;
    private static boolean libraryMissingLogged = false;
    private static @Nullable ClassLoader externalPluginLoader;
    private static @Nullable Object tooltipPluginObject;
    private static @Nullable Object tooltipRegistryInstance;
    private static @Nullable Method tooltipComposeMethod;
    private static @Nullable Method tooltipCombinedHashMethod;
    private static @Nullable Method virtualGenerateIdMethod;

    private MghgDynamicTooltipsManager() {
    }

    public static void tryRegister() {
        if (providerRegistered) {
            return;
        }
        try {
            Class<?> providerInterface = loadExternalClass(TOOLTIP_PROVIDER_CLASS);
            Object api = resolveApi();
            if (api == null) {
                return;
            }

            Object providerProxy = Proxy.newProxyInstance(
                    providerInterface.getClassLoader(),
                    new Class<?>[]{providerInterface},
                    new ProviderHandler()
            );
            Class<?> apiInterface = loadExternalClass(API_CLASS);
            Method registerProvider = apiInterface.getMethod("registerProvider", providerInterface);
            registerProvider.invoke(api, providerProxy);
            providerRegistered = true;
            LOGGER.atInfo().log("[MGHG|TOOLTIP] Dynamic tooltip provider registered.");
        } catch (ClassNotFoundException missing) {
            if (!libraryMissingLogged) {
                LOGGER.atInfo().log(
                        "[MGHG|TOOLTIP] DynamicTooltipsLib classes unavailable (%s). Item tooltips fallback to vanilla.",
                        missing.getMessage()
                );
                libraryMissingLogged = true;
            }
        } catch (Throwable throwable) {
            LOGGER.atWarning().log("[MGHG|TOOLTIP] Failed to register dynamic tooltip provider: %s", throwable.getMessage());
        }
    }

    public static void refreshAllPlayers() {
        try {
            Object api = resolveApi();
            if (api == null) {
                return;
            }
            Class<?> apiInterface = loadExternalClass(API_CLASS);
            Method refresh = apiInterface.getMethod("refreshAllPlayers");
            refresh.invoke(api);
        } catch (Throwable ignored) {
            // Optional integration: ignore refresh errors silently.
        }
    }

    public static @Nullable String resolveVirtualItemIdForUi(@Nullable String rawItemId, @Nullable String metadataJson) {
        String itemId = sanitizeUiItemId(rawItemId);
        if (itemId == null || itemId.isBlank()) {
            return null;
        }

        try {
            Object tooltipRegistry = resolveTooltipRegistry();
            if (tooltipRegistry == null) {
                return null;
            }
            if (tooltipComposeMethod == null) {
                tooltipComposeMethod = tooltipRegistry.getClass().getMethod("compose", String.class, String.class);
            }
            Object composed = tooltipComposeMethod.invoke(tooltipRegistry, itemId, metadataJson);
            if (composed == null) {
                return null;
            }

            if (tooltipCombinedHashMethod == null) {
                tooltipCombinedHashMethod = composed.getClass().getMethod("getCombinedHash");
            }
            Object hashRaw = tooltipCombinedHashMethod.invoke(composed);
            if (!(hashRaw instanceof String hash) || hash.isBlank()) {
                return null;
            }

            Method generateVirtualId = resolveGenerateVirtualIdMethod();
            if (generateVirtualId == null) {
                return null;
            }
            Object value = generateVirtualId.invoke(null, itemId, hash);
            if (value instanceof String virtualId && !virtualId.isBlank()) {
                return virtualId;
            }
            return null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static @Nullable Object resolveApi() throws Exception {
        Class<?> providerClass = loadExternalClass(API_PROVIDER_CLASS);
        Method getMethod = providerClass.getMethod("get");
        return getMethod.invoke(null);
    }

    private static @Nullable Method resolveGenerateVirtualIdMethod() {
        if (virtualGenerateIdMethod != null) {
            return virtualGenerateIdMethod;
        }
        try {
            Class<?> virtualRegistryClass = loadExternalClass("org.herolias.tooltips.internal.VirtualItemRegistry");
            virtualGenerateIdMethod = virtualRegistryClass.getMethod("generateVirtualId", String.class, String.class);
            return virtualGenerateIdMethod;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static @Nullable Object resolveTooltipRegistry() {
        if (tooltipRegistryInstance != null) {
            return tooltipRegistryInstance;
        }
        try {
            Object pluginObject = resolveTooltipPluginObject();
            if (pluginObject == null) {
                return null;
            }
            java.lang.reflect.Field tooltipRegistryField = pluginObject.getClass().getDeclaredField("tooltipRegistry");
            tooltipRegistryField.setAccessible(true);
            Object registry = tooltipRegistryField.get(pluginObject);
            if (registry != null) {
                tooltipRegistryInstance = registry;
            }
            return tooltipRegistryInstance;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static @Nullable Object resolveTooltipPluginObject() {
        if (tooltipPluginObject != null) {
            return tooltipPluginObject;
        }
        try {
            Object pluginManager = HytaleServer.get().getPluginManager();
            if (pluginManager == null) {
                return null;
            }
            PluginIdentifier pluginIdentifier = PluginIdentifier.fromString(TOOLTIP_PLUGIN_ID);
            Object pluginObject = findPluginObject(pluginManager, pluginIdentifier);
            if (pluginObject != null) {
                tooltipPluginObject = pluginObject;
            }
            return pluginObject;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static @Nonnull Class<?> loadExternalClass(@Nonnull String className) throws ClassNotFoundException {
        ClassLoader pluginDependencyLoader = resolveExternalPluginLoader();
        if (pluginDependencyLoader != null) {
            try {
                return Class.forName(className, false, pluginDependencyLoader);
            } catch (ClassNotFoundException ignored) {
                // Keep trying fallback loaders.
            }
        }

        ClassLoader pluginLoader = MghgDynamicTooltipsManager.class.getClassLoader();
        try {
            return Class.forName(className, false, pluginLoader);
        } catch (ClassNotFoundException ignored) {
            // Fallback to context loader because some plugin bootstraps expose dependencies there.
        }

        ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
        if (contextLoader != null) {
            try {
                return Class.forName(className, false, contextLoader);
            } catch (ClassNotFoundException ignored) {
                // Continue to default class lookup.
            }
        }

        ClassLoader systemLoader = ClassLoader.getSystemClassLoader();
        if (systemLoader != null) {
            try {
                return Class.forName(className, false, systemLoader);
            } catch (ClassNotFoundException ignored) {
                // Continue to default class lookup.
            }
        }

        return Class.forName(className, false, pluginLoader);
    }

    private static @Nullable ClassLoader resolveExternalPluginLoader() {
        if (externalPluginLoader != null) {
            return externalPluginLoader;
        }
        try {
            Object pluginManager = HytaleServer.get().getPluginManager();
            if (pluginManager == null) {
                return null;
            }

            PluginIdentifier pluginIdentifier = PluginIdentifier.fromString(TOOLTIP_PLUGIN_ID);
            Object pluginObject = findPluginObject(pluginManager, pluginIdentifier);
            if (pluginObject == null) {
                return null;
            }

            externalPluginLoader = pluginObject.getClass().getClassLoader();
            return externalPluginLoader;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static @Nullable Object findPluginObject(@Nonnull Object pluginManager, @Nonnull PluginIdentifier pluginIdentifier) {
        for (Method method : pluginManager.getClass().getMethods()) {
            Class<?>[] params = method.getParameterTypes();
            if (params.length == 2
                    && params[0] == PluginIdentifier.class
                    && params[1] == SemverRange.class
                    && method.getReturnType() != boolean.class) {
                Object resolved = invokePluginLookup(pluginManager, method, pluginIdentifier, SemverRange.WILDCARD);
                if (resolved != null) {
                    return resolved;
                }
            }
        }

        for (Method method : pluginManager.getClass().getMethods()) {
            Class<?>[] params = method.getParameterTypes();
            if (params.length == 1
                    && params[0] == PluginIdentifier.class
                    && method.getReturnType() != boolean.class) {
                Object resolved = invokePluginLookup(pluginManager, method, pluginIdentifier);
                if (resolved != null) {
                    return resolved;
                }
            }
        }

        for (Method method : pluginManager.getClass().getMethods()) {
            if (method.getParameterCount() != 0) {
                continue;
            }
            String name = method.getName().toLowerCase(Locale.ROOT);
            if (!name.contains("plugin")) {
                continue;
            }

            Object container;
            try {
                container = method.invoke(pluginManager);
            } catch (Throwable ignored) {
                continue;
            }

            Object resolved = findPluginObjectInContainer(container);
            if (resolved != null) {
                return resolved;
            }
        }

        return null;
    }

    private static @Nullable Object invokePluginLookup(@Nonnull Object pluginManager, @Nonnull Method method, Object... args) {
        try {
            Object value = method.invoke(pluginManager, args);
            if (value == null) {
                return null;
            }
            if (value instanceof Optional<?> optional) {
                return optional.orElse(null);
            }
            return value;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static @Nullable Object findPluginObjectInContainer(@Nullable Object container) {
        if (container == null) {
            return null;
        }

        if (container instanceof Map<?, ?> map) {
            for (Object value : map.values()) {
                Object resolved = findPluginObjectInContainer(value);
                if (resolved != null) {
                    return resolved;
                }
            }
            return null;
        }

        if (container instanceof Collection<?> collection) {
            for (Object value : collection) {
                Object resolved = findPluginObjectInContainer(value);
                if (resolved != null) {
                    return resolved;
                }
            }
            return null;
        }

        if (container.getClass().isArray()) {
            Object[] array = (Object[]) container;
            for (Object value : array) {
                Object resolved = findPluginObjectInContainer(value);
                if (resolved != null) {
                    return resolved;
                }
            }
            return null;
        }

        if (isTooltipPluginObject(container)) {
            return container;
        }
        return null;
    }

    private static boolean isTooltipPluginObject(@Nonnull Object pluginObject) {
        try {
            Method getManifest = pluginObject.getClass().getMethod("getManifest");
            Object manifest = getManifest.invoke(pluginObject);
            if (manifest == null) {
                return false;
            }
            Method getGroup = manifest.getClass().getMethod("getGroup");
            Method getName = manifest.getClass().getMethod("getName");
            Object group = getGroup.invoke(manifest);
            Object name = getName.invoke(manifest);
            return "org.herolias".equalsIgnoreCase(String.valueOf(group))
                    && "DynamicTooltipsLib".equalsIgnoreCase(String.valueOf(name));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static final class ProviderHandler implements InvocationHandler {
        @Override
        public @Nullable Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            if (method.getDeclaringClass() == Object.class) {
                return switch (name) {
                    case "toString" -> "MghgDynamicTooltipProvider";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == (args == null || args.length == 0 ? null : args[0]);
                    default -> null;
                };
            }
            return switch (name) {
                case "getProviderId" -> PROVIDER_ID;
                case "getPriority" -> PROVIDER_PRIORITY;
                case "getTooltipData" -> buildTooltipData(
                        args != null && args.length > 0 ? (String) args[0] : null,
                        args != null && args.length > 1 ? (String) args[1] : null
                );
                default -> null;
            };
        }

        private @Nullable Object buildTooltipData(@Nullable String rawItemId, @Nullable String metadata) {
            TooltipPayload payload = buildPayload(rawItemId, metadata);
            if (payload == null || payload.lines().isEmpty()) {
                return null;
            }

            try {
                Class<?> tooltipData = loadExternalClass(TOOLTIP_DATA_CLASS);
                Object builder = tooltipData.getMethod("builder").invoke(null);
                Class<?> builderClass = builder.getClass();
                builderClass.getMethod("hashInput", String.class).invoke(builder, payload.hashInput());
                Method addLine = builderClass.getMethod("addLine", String.class);
                for (String line : payload.lines()) {
                    addLine.invoke(builder, line);
                }
                return builderClass.getMethod("build").invoke(builder);
            } catch (Throwable ignored) {
                return null;
            }
        }
    }

    private static @Nullable TooltipPayload buildPayload(@Nullable String rawItemId, @Nullable String metadata) {
        String itemId = normalizeItemId(rawItemId);
        if (itemId == null || itemId.isBlank()) {
            return null;
        }

        MghgShopConfig.ShopItem seedMatch = findBuyMatch(itemId);
        MghgShopConfig.ShopItem sellMatch = findSellMatch(itemId);
        MghgCropMeta meta = parseCropMeta(metadata);

        if (sellMatch != null && meta != null) {
            return buildCropPayload(itemId, sellMatch, meta);
        }
        if (seedMatch != null) {
            return buildSeedPayload(itemId, seedMatch);
        }
        if (sellMatch != null) {
            return buildStaticSellPayload(itemId, sellMatch);
        }
        return null;
    }

    private static @Nonnull TooltipPayload buildSeedPayload(@Nonnull String itemId, @Nonnull MghgShopConfig.ShopItem item) {
        ArrayList<String> lines = new ArrayList<>();
        lines.add(color("#f2d896", "Seed bag pricing"));
        lines.add(color("#9fb6d1", "Buy price: ") + color("#ffffff", formatMoney(item.getBuyPrice())));
        lines.add(color("#9fb6d1", "Base sell value: ") + color("#d7e5f7", formatMoney(item.getSellPrice())));
        int growTimeSeconds = resolveGrowTimeSeconds(item);
        if (growTimeSeconds > 0) {
            lines.add(color("#9fb6d1", "Grow time: ") + color("#d7e5f7", formatDuration(growTimeSeconds)));
        }
        lines.add(color("#9fb6d1", "Restock chance: ") + color("#d7e5f7", formatPercent(item.getRestockChance())));

        if (item.isEnableMetaSellPricing()) {
            lines.add(color("#9fb6d1", "Formula: ") + "Base x Rarity x Size x Climate x Lunar");
            lines.add(
                    color("#9fb6d1", "Size curve: ")
                            + color(
                            "#d7e5f7",
                            String.format(
                                    Locale.ROOT,
                                    "%.0f->x%.2f  %.0f->x%.2f",
                                    item.getSellSizeMultiplierMinSize(),
                                    item.getSellSizeMultiplierAtMin(),
                                    item.getSellSizeMultiplierMaxSize(),
                                    item.getSellSizeMultiplierAtMax()
                            )
                    )
            );
        } else {
            lines.add(color("#9fb6d1", "Formula: ") + color("#d7e5f7", "Fixed base sell value"));
        }

        String hashInput = String.format(
                Locale.ROOT,
                "seed|%s|%f|%f|%f|%b|%d",
                itemId,
                item.getBuyPrice(),
                item.getSellPrice(),
                item.getRestockChance(),
                item.isEnableMetaSellPricing(),
                growTimeSeconds
        );
        return new TooltipPayload(lines, hashInput);
    }

    private static @Nonnull TooltipPayload buildStaticSellPayload(@Nonnull String itemId, @Nonnull MghgShopConfig.ShopItem item) {
        ArrayList<String> lines = new ArrayList<>();
        //lines.add(color("#f2d896", "Crop sell value"));
        lines.add(color("#9fb6d1", "Base sell value: ") + color("#ffffff", formatMoney(item.getSellPrice())));
        /*if (item.isEnableMetaSellPricing()) {
            lines.add(color("#9fb6d1", "Formula: ") + "Base x Rarity x Size x Climate x Lunar");
            lines.add(color("#9fb6d1", "Grow and harvest this crop to roll metadata."));
        } else {
            lines.add(color("#9fb6d1", "Formula: ") + color("#d7e5f7", "Fixed base sell value"));
        }*/
        String hashInput = String.format(Locale.ROOT, "sell-static|%s|%f|%b", itemId, item.getSellPrice(), item.isEnableMetaSellPricing());
        return new TooltipPayload(lines, hashInput);
    }

    private static @Nonnull TooltipPayload buildCropPayload(
            @Nonnull String itemId,
            @Nonnull MghgShopConfig.ShopItem item,
            @Nonnull MghgCropMeta meta
    ) {
        ArrayList<String> lines = new ArrayList<>();

        double base = Math.max(0.0d, item.getSellPrice());
        double unitValue = MghgShopPricing.computeUnitSellPrice(item, meta);
        double size = MghgShopPricing.computeSizeMultiplier(item, meta.getSize());
        double rarity = sanitizeMultiplier(item.getRarityMultiplier(meta.getRarity()));
        double climate = sanitizeMultiplier(item.getClimateMultiplier(meta.getClimate()));
        double lunar = sanitizeMultiplier(item.getLunarMultiplier(meta.getLunar()));

        String climateName = mutationLabel(meta.getClimate());
        String lunarName = mutationLabel(meta.getLunar());
        String rarityName = mutationLabel(meta.getRarity());
        String climateNameColor = orDefault(MghgMutationUiPalette.colorForClimate(meta.getClimate()), "#d7e5f7");
        String lunarNameColor = orDefault(MghgMutationUiPalette.colorForLunar(meta.getLunar()), "#d7e5f7");
        String rarityNameColor = orDefault(MghgMutationUiPalette.colorForRarity(meta.getRarity()), "#d7e5f7");

        //lines.add(color("#f2d896", "Crop pricing"));
        lines.add(color("#9fb6d1", "Base value: ") + color("#d7e5f7", formatMoney(base)));
        lines.add("");
        lines.add(
                color("#9fb6d1", "Size: ")
                        + color("#d7e5f7", Integer.toString(Math.max(0, meta.getSize())))
                        + " "
                        + color(MghgMutationUiPalette.colorForMultiplier(size), "(x" + formatMultiplier(size) + ")")
        );
        lines.add(
                color("#9fb6d1", "Climate: ")
                        + color(climateNameColor, climateName)
                        + " "
                        + color(MghgMutationUiPalette.colorForMultiplier(climate), "(x" + formatMultiplier(climate) + ")")
        );
        lines.add(
                color("#9fb6d1", "Lunar: ")
                        + color(lunarNameColor, lunarName)
                        + " "
                        + color(MghgMutationUiPalette.colorForMultiplier(lunar), "(x" + formatMultiplier(lunar) + ")")
        );
        lines.add(
                color("#9fb6d1", "Rarity: ")
                        + color(rarityNameColor, rarityName)
                        + " "
                        + color(MghgMutationUiPalette.colorForMultiplier(rarity), "(x" + formatMultiplier(rarity) + ")")
        );
        lines.add("");
        lines.add(color("#9fb6d1", "Total value: ") + color("#ffffff", formatMoney(unitValue)));
        //lines.add(color("#9fb6d1", "Formula: ") + "Base x Rarity x Size x Climate x Lunar");
        /*lines.add(
                color(
                        "#d7e5f7",
                        formatMoney(base)
                                + " x " + formatMultiplier(rarity)
                                + " x " + formatMultiplier(size)
                                + " x " + formatMultiplier(climate)
                                + " x " + formatMultiplier(lunar)
                )
        );*/

        String hashInput = String.format(
                Locale.ROOT,
                "crop|%s|%d|%s|%s|%s|%f|%f",
                itemId,
                meta.getSize(),
                normalize(meta.getClimate()),
                normalize(meta.getLunar()),
                normalize(meta.getRarity()),
                sanitize(meta.getWeightGrams()),
                item.getSellPrice()
        );
        return new TooltipPayload(lines, hashInput);
    }

    private static @Nullable MghgCropMeta parseCropMeta(@Nullable String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return null;
        }
        try {
            BsonDocument metadata = BsonDocument.parse(metadataJson);
            BsonValue value = metadata.get("MGHG_Crop");
            if (value == null || !value.isDocument()) {
                return null;
            }
            BsonDocument crop = value.asDocument();
            int size = readInt(crop, "Size", 0);
            String climate = readString(crop, "Climate", "NONE");
            String lunar = readString(crop, "Lunar", "NONE");
            String rarity = readString(crop, "Rarity", "NONE");
            double weight = readDouble(crop, "WeightGrams", 0.0d);
            return MghgCropMeta.fromCropData(size, climate, lunar, rarity, weight);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static @Nullable MghgShopConfig.ShopItem findBuyMatch(@Nonnull String normalizedItemId) {
        for (MghgShopConfig.ShopItem candidate : MghgShopStockManager.getConfiguredItems()) {
            if (candidate == null) {
                continue;
            }
            String buyId = normalizeItemId(candidate.resolveBuyItemId());
            if (buyId != null && buyId.equalsIgnoreCase(normalizedItemId)) {
                return candidate;
            }
        }
        return null;
    }

    private static @Nullable MghgShopConfig.ShopItem findSellMatch(@Nonnull String normalizedItemId) {
        for (MghgShopConfig.ShopItem candidate : MghgShopStockManager.getConfiguredItems()) {
            if (candidate == null || candidate.getSellPrice() <= 0.0d) {
                continue;
            }
            for (String sellId : candidate.resolveSellItemIds()) {
                String normalizedSell = normalizeItemId(sellId);
                if (normalizedSell != null && normalizedSell.equalsIgnoreCase(normalizedItemId)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private static int readInt(@Nonnull BsonDocument document, @Nonnull String key, int defaultValue) {
        BsonValue value = document.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value.isInt32()) {
            return value.asInt32().getValue();
        }
        if (value.isInt64()) {
            return (int) value.asInt64().getValue();
        }
        if (value.isDouble()) {
            return (int) Math.round(value.asDouble().getValue());
        }
        return defaultValue;
    }

    private static double readDouble(@Nonnull BsonDocument document, @Nonnull String key, double defaultValue) {
        BsonValue value = document.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value.isDouble()) {
            return value.asDouble().getValue();
        }
        if (value.isInt32()) {
            return value.asInt32().getValue();
        }
        if (value.isInt64()) {
            return value.asInt64().getValue();
        }
        return defaultValue;
    }

    private static @Nonnull String readString(@Nonnull BsonDocument document, @Nonnull String key, @Nonnull String defaultValue) {
        BsonValue value = document.get(key);
        if (value != null && value.isString()) {
            return value.asString().getValue();
        }
        return defaultValue;
    }

    private static @Nonnull String mutationLabel(@Nullable String raw) {
        String normalized = normalize(raw);
        if (normalized.isBlank() || "NONE".equals(normalized)) {
            return "Normal";
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static @Nonnull String color(@Nonnull String hex, @Nonnull String text) {
        return "<color is=\"" + hex + "\">" + text + "</color>";
    }

    private static @Nonnull String formatMoney(double value) {
        return String.format(Locale.ROOT, "$%.2f", Math.max(0.0d, value));
    }

    private static @Nonnull String formatPercent(double chance) {
        double normalized = chance;
        if (normalized > 1.0d) {
            normalized = normalized / 100.0d;
        }
        normalized = Math.max(0.0d, Math.min(1.0d, normalized));
        return String.format(Locale.ROOT, "%.2f%%", normalized * 100.0d);
    }

    private static @Nonnull String formatMultiplier(double value) {
        return String.format(Locale.ROOT, "%.2f", Math.max(0.0d, value));
    }

    private static @Nonnull String formatDuration(int totalSecondsRaw) {
        int totalSeconds = Math.max(0, totalSecondsRaw);
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int seconds = totalSeconds % 60;

        ArrayList<String> parts = new ArrayList<>(3);
        if (hours > 0) {
            parts.add(hours + "h");
        }
        if (minutes > 0) {
            parts.add(minutes + "m");
        }
        if (seconds > 0 || parts.isEmpty()) {
            parts.add(seconds + "s");
        }
        return String.join(" ", parts);
    }

    private static int resolveGrowTimeSeconds(@Nonnull MghgShopConfig.ShopItem item) {
        for (String sellItemId : item.resolveSellItemIds()) {
            String normalizedSellId = normalizeItemId(sellItemId);
            if (normalizedSellId == null || normalizedSellId.isBlank()) {
                continue;
            }
            MghgCropDefinition definition = MghgCropRegistry.getDefinitionByItemId(normalizedSellId);
            if (definition != null && definition.getGrowTimeSeconds() > 0) {
                return definition.getGrowTimeSeconds();
            }
        }
        return 0;
    }

    private static @Nonnull String orDefault(@Nullable String value, @Nonnull String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    private static double sanitizeMultiplier(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 1.0d;
        }
        return Math.max(0.0d, value);
    }

    private static double sanitize(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0d;
        }
        return Math.max(0.0d, value);
    }

    private static @Nullable String normalizeItemId(@Nullable String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return null;
        }
        String normalized = itemId.trim();
        if (normalized.startsWith("*")) {
            normalized = normalized.substring(1);
        }
        int stateIndex = normalized.indexOf("_State_");
        if (stateIndex > 0) {
            normalized = normalized.substring(0, stateIndex);
        }
        return normalized;
    }

    private static @Nonnull String normalize(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static @Nullable String sanitizeUiItemId(@Nullable String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return null;
        }
        String value = itemId.trim();
        while (!value.isBlank() && value.charAt(0) == '#') {
            value = value.substring(1);
        }
        return value.isBlank() ? null : value;
    }

    private record TooltipPayload(@Nonnull List<String> lines, @Nonnull String hashInput) {
    }
}
