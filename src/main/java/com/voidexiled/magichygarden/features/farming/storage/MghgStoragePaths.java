package com.voidexiled.magichygarden.features.farming.storage;

import com.voidexiled.magichygarden.MagicHyGardenPlugin;
import com.hypixel.hytale.server.core.universe.Universe;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public final class MghgStoragePaths {
    private static final String ROOT_DIRECTORY = "mghg";

    private MghgStoragePaths() {
    }

    public static Path dataRoot() {
        return pluginDataDirectory().resolve(ROOT_DIRECTORY).toAbsolutePath().normalize();
    }

    public static Path resolveInDataRoot(String first, String... more) {
        Path path = dataRoot().resolve(first);
        for (String part : more) {
            path = path.resolve(part);
        }
        return path.toAbsolutePath().normalize();
    }

    public static List<Path> legacyCandidates(String first, String... more) {
        LinkedHashSet<Path> set = new LinkedHashSet<>();
        set.add(resolveInDataRoot(first, more));

        Path cwd = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        set.add(resolveLegacyFrom(cwd, first, more));
        set.add(resolveLegacyFrom(cwd.resolve("run"), first, more));
        set.add(resolveLegacyFrom(cwd.resolve("data"), first, more));

        return new ArrayList<>(set);
    }

    private static Path resolveLegacyFrom(@Nullable Path base, String first, String... more) {
        Path path = (base == null ? Path.of(".") : base).resolve(ROOT_DIRECTORY).resolve(first);
        for (String part : more) {
            path = path.resolve(part);
        }
        return path.toAbsolutePath().normalize();
    }

    private static Path pluginDataDirectory() {
        Universe universe = Universe.get();
        if (universe != null && universe.getPath() != null) {
            Path universePath = universe.getPath().toAbsolutePath().normalize();
            Path parent = universePath.getParent();
            if (parent != null) {
                return parent;
            }
            return universePath;
        }
        MagicHyGardenPlugin plugin = MagicHyGardenPlugin.get();
        if (plugin != null && plugin.getDataDirectory() != null) {
            return plugin.getDataDirectory().toAbsolutePath().normalize();
        }
        return Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
    }
}
