package com.voidexiled.magichygarden.features.farming.logic;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.entity.ItemUtils;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Method;

public final class MghgItemDropUtil {
    private static volatile boolean resolved;
    private static volatile Method dropWithOrigin;
    private static volatile boolean resolvedComponent;
    private static volatile Method componentDropMethod;

    private MghgItemDropUtil() {
    }

    public static void dropAt(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull ItemStack itemStack,
            @Nonnull Vector3d origin,
            @Nonnull ComponentAccessor<EntityStore> accessor
    ) {
        if (tryItemUtilsDrop(ref, itemStack, origin, accessor)) {
            return;
        }

        if (tryItemComponentDrop(ref, itemStack, origin, accessor)) {
            return;
        }

        ItemUtils.dropItem(ref, itemStack, accessor);
    }

    @Nullable
    private static Method resolveDropWithOrigin() {
        if (resolved) {
            return dropWithOrigin;
        }
        resolved = true;

        for (Method method : ItemUtils.class.getMethods()) {
            if (!method.getName().equals("dropItem")) {
                continue;
            }
            Class<?>[] params = method.getParameterTypes();
            if (params.length < 3 || params.length > 5) {
                continue;
            }
            if (!hasVectorParam(params)) {
                continue;
            }
            if (!hasParam(params, ItemStack.class)) {
                continue;
            }
            if (!hasParamAssignable(params, ComponentAccessor.class) && !hasParamAssignable(params, Store.class)
                    && !hasParamAssignable(params, World.class)) {
                continue;
            }
            dropWithOrigin = method;
            return method;
        }
        return null;
    }

    private static boolean tryItemUtilsDrop(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull ItemStack itemStack,
            @Nonnull Vector3d origin,
            @Nonnull ComponentAccessor<EntityStore> accessor
    ) {
        Method method = resolveDropWithOrigin();
        if (method == null) {
            return false;
        }
        try {
            Object[] args = buildArgs(method.getParameterTypes(), ref, itemStack, origin, accessor);
            if (args != null) {
                method.invoke(null, args);
                return true;
            }
        } catch (ReflectiveOperationException ignored) {
            // fallthrough
        }
        return false;
    }

    private static boolean tryItemComponentDrop(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull ItemStack itemStack,
            @Nonnull Vector3d origin,
            @Nonnull ComponentAccessor<EntityStore> accessor
    ) {
        Method method = resolveComponentDrop();
        if (method == null) {
            return false;
        }

        try {
            Object[] args = buildArgs(method.getParameterTypes(), ref, itemStack, origin, accessor);
            if (args == null) {
                return false;
            }
            Object result = method.invoke(null, args);
            if (result instanceof Holder<?> holder) {
                @SuppressWarnings("unchecked")
                Holder<EntityStore> entityHolder = (Holder<EntityStore>) holder;
                accessor.addEntity(entityHolder, AddReason.SPAWN);
            }
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    @Nullable
    private static Method resolveComponentDrop() {
        if (resolvedComponent) {
            return componentDropMethod;
        }
        resolvedComponent = true;

        for (Method method : ItemComponent.class.getMethods()) {
            if (!java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            String name = method.getName();
            if (!(name.contains("drop") || name.contains("generate"))) {
                continue;
            }
            Class<?>[] params = method.getParameterTypes();
            if (!hasParam(params, ItemStack.class) || !hasVectorParam(params)) {
                continue;
            }
            if (!hasParamAssignable(params, ComponentAccessor.class)
                    && !hasParamAssignable(params, Store.class)
                    && !hasParamAssignable(params, World.class)) {
                continue;
            }
            componentDropMethod = method;
            return method;
        }
        return null;
    }

    @Nullable
    private static Object[] buildArgs(
            @Nonnull Class<?>[] params,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull ItemStack itemStack,
            @Nonnull Vector3d origin,
            @Nonnull ComponentAccessor<EntityStore> accessor
    ) {
        Object[] args = new Object[params.length];
        for (int i = 0; i < params.length; i++) {
            Class<?> param = params[i];
            if (param.isAssignableFrom(ref.getClass()) || param == Ref.class) {
                args[i] = ref;
            } else if (EntityStore.class.isAssignableFrom(param)) {
                Object store = tryGetStore(accessor);
                if (store instanceof Store<?> s && s.getExternalData() instanceof EntityStore entityStore) {
                    args[i] = entityStore;
                } else {
                    return null;
                }
            } else if (param.isAssignableFrom(itemStack.getClass()) || param == ItemStack.class) {
                args[i] = itemStack;
            } else if (param.isAssignableFrom(origin.getClass()) || param == Vector3d.class) {
                args[i] = origin;
            } else if (isVectorParam(param)) {
                Object vector = adaptVector(param, origin);
                if (vector == null) {
                    return null;
                }
                args[i] = vector;
            } else if (param.isAssignableFrom(accessor.getClass()) || param == ComponentAccessor.class) {
                args[i] = accessor;
            } else if (Store.class.isAssignableFrom(param)) {
                Object store = tryGetStore(accessor);
                if (store == null || !param.isAssignableFrom(store.getClass())) {
                    return null;
                }
                args[i] = store;
            } else if (World.class.isAssignableFrom(param)) {
                World world = accessor.getExternalData().getWorld();
                if (world == null || !param.isAssignableFrom(world.getClass())) {
                    return null;
                }
                args[i] = world;
            } else {
                return null;
            }
        }
        return args;
    }

    private static boolean hasParam(@Nonnull Class<?>[] params, @Nonnull Class<?> type) {
        for (Class<?> param : params) {
            if (param == type) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasVectorParam(@Nonnull Class<?>[] params) {
        for (Class<?> param : params) {
            if (param == Vector3d.class || isVectorParam(param)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isVectorParam(@Nonnull Class<?> param) {
        String name = param.getSimpleName();
        return name.startsWith("Vector3");
    }

    private static boolean hasParamAssignable(@Nonnull Class<?>[] params, @Nonnull Class<?> type) {
        for (Class<?> param : params) {
            if (type.isAssignableFrom(param)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static Object tryGetStore(@Nonnull ComponentAccessor<EntityStore> accessor) {
        try {
            Method method = accessor.getClass().getMethod("getStore");
            return method.invoke(accessor);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    @Nullable
    private static Object adaptVector(@Nonnull Class<?> param, @Nonnull Vector3d origin) {
        try {
            if (param == Vector3d.class) {
                return origin;
            }
            // Common signatures: (double,double,double), (float,float,float), (int,int,int)
            try {
                return param.getConstructor(double.class, double.class, double.class)
                        .newInstance(origin.x, origin.y, origin.z);
            } catch (ReflectiveOperationException ignored) {
                // try float ctor
            }
            try {
                return param.getConstructor(float.class, float.class, float.class)
                        .newInstance((float) origin.x, (float) origin.y, (float) origin.z);
            } catch (ReflectiveOperationException ignored) {
                // try int ctor
            }
            return param.getConstructor(int.class, int.class, int.class)
                    .newInstance((int) origin.x, (int) origin.y, (int) origin.z);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
