package com.voidexiled.magichygarden.utils.chat;

import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.common.semver.SemverRange;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.Message;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Method;

final class MghgMessageTagsBridge {
    private static final String PLUGIN_ID = "Koboo:MessageTags";
    private static final String API_CLASS = "eu.koboo.messagetags.api.MessageTags";

    private static volatile boolean initialized;
    private static @Nullable Method parseMethod;

    private MghgMessageTagsBridge() {
    }

    static @Nullable Message parse(@Nullable String text) {
        Method method = resolveParseMethod();
        if (method == null) {
            return null;
        }
        try {
            Object out = method.invoke(null, text);
            if (out instanceof Message message) {
                return message;
            }
        } catch (Throwable ignored) {
            // Optional integration: ignore parser failures and fallback to vanilla formatting.
        }
        return null;
    }

    private static @Nullable Method resolveParseMethod() {
        if (parseMethod != null) {
            return parseMethod;
        }
        if (initialized) {
            return null;
        }

        synchronized (MghgMessageTagsBridge.class) {
            if (parseMethod != null) {
                return parseMethod;
            }
            if (initialized) {
                return null;
            }
            initialized = true;

            try {
                Class<?> clazz = loadApiClass();
                parseMethod = clazz.getMethod("parse", String.class);
                return parseMethod;
            } catch (Throwable ignored) {
                return null;
            }
        }
    }

    private static @Nonnull Class<?> loadApiClass() throws Exception {
        ClassLoader pluginLoader = resolvePluginClassLoader();
        if (pluginLoader != null) {
            try {
                return Class.forName(API_CLASS, false, pluginLoader);
            } catch (ClassNotFoundException ignored) {
                // Continue fallback loaders.
            }
        }

        ClassLoader own = MghgMessageTagsBridge.class.getClassLoader();
        try {
            return Class.forName(API_CLASS, false, own);
        } catch (ClassNotFoundException ignored) {
            // Continue fallback loaders.
        }

        ClassLoader context = Thread.currentThread().getContextClassLoader();
        if (context != null) {
            return Class.forName(API_CLASS, false, context);
        }

        return Class.forName(API_CLASS, false, own);
    }

    private static @Nullable ClassLoader resolvePluginClassLoader() {
        try {
            Object pluginManager = HytaleServer.get().getPluginManager();
            if (pluginManager == null) {
                return null;
            }
            Object plugin = findPluginObject(pluginManager, PluginIdentifier.fromString(PLUGIN_ID));
            if (plugin == null) {
                return null;
            }
            return plugin.getClass().getClassLoader();
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
                Object found = invokeLookup(pluginManager, method, pluginIdentifier, SemverRange.WILDCARD);
                if (found != null) {
                    return found;
                }
            }
        }

        for (Method method : pluginManager.getClass().getMethods()) {
            Class<?>[] params = method.getParameterTypes();
            if (params.length == 1
                    && params[0] == PluginIdentifier.class
                    && method.getReturnType() != boolean.class) {
                Object found = invokeLookup(pluginManager, method, pluginIdentifier);
                if (found != null) {
                    return found;
                }
            }
        }

        return null;
    }

    private static @Nullable Object invokeLookup(@Nonnull Object pluginManager, @Nonnull Method method, Object... args) {
        try {
            Object value = method.invoke(pluginManager, args);
            if (value == null) {
                return null;
            }
            if (value instanceof java.util.Optional<?> optional) {
                return optional.orElse(null);
            }
            return value;
        } catch (Throwable ignored) {
            return null;
        }
    }
}

