package com.voidexiled.magichygarden.features.farming.worlds;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldConfig;
import com.hypixel.hytale.server.core.universe.world.spawn.ISpawnProvider;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.component.ChunkSavingSystems;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.voidexiled.magichygarden.features.farming.storage.MghgStoragePaths;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcel;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcelBlocks;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcelBounds;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcelManager;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MghgFarmWorldManager {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String WORLD_BACKUPS_DIR = "world_backups";
    private static final int SAFE_SPAWN_SEARCH_RADIUS_BLOCKS = 16;
    private static final int SAFE_SPAWN_MIN_Y = ChunkUtil.MIN_Y + 1;
    private static final int SAFE_SPAWN_MAX_Y = ChunkUtil.HEIGHT_MINUS_1;
    private static final Pattern FORCED_WEATHER_STRING_PATTERN =
            Pattern.compile("\"ForcedWeather\"\\s*:\\s*\"([^\"]*)\"");
    private static volatile MghgFarmWorldConfig CONFIG = new MghgFarmWorldConfig();
    private static volatile ScheduledFuture<?> backupTask;
    private static volatile int backupCursor;

    private MghgFarmWorldManager() {}

    public static void load() {
        CONFIG = MghgFarmWorldConfig.load();
        sanitizePersistedFarmWorldConfigs();
        startBackupTask();
    }

    public static void shutdown() {
        backupAllNow();
        stopBackupTask();
    }

    public static void forceSnapshotAll() {
        backupAllNow();
    }

    public static boolean forceSnapshotOwner(@Nullable UUID owner) {
        if (owner == null) {
            return false;
        }
        Universe universe = Universe.get();
        if (universe == null) {
            return false;
        }
        String worldName = getFarmWorldName(owner);
        if (worldName == null || worldName.isBlank()) {
            return false;
        }
        backupWorldByName(universe, worldName);
        return true;
    }

    public static boolean restoreOwnerFromSnapshot(@Nullable UUID owner) {
        if (owner == null) {
            return false;
        }
        Universe universe = Universe.get();
        if (universe == null) {
            return false;
        }
        String worldName = getFarmWorldName(owner);
        if (worldName == null || worldName.isBlank()) {
            return false;
        }
        return restoreWorldFromBackupIfNeeded(universe, worldName);
    }

    public static Path getBackupRootPath() {
        return MghgStoragePaths.resolveInDataRoot(WORLD_BACKUPS_DIR);
    }

    public static Path getBackupWorldPath(@Nullable UUID owner) {
        if (owner == null) {
            return getBackupRootPath();
        }
        return resolveBackupWorldPath(getFarmWorldName(owner));
    }

    public static String getFarmWorldNamePrefix() {
        return CONFIG.getFarmWorldNamePrefix();
    }

    public static int getConfiguredParcelSizeX() {
        return CONFIG.getParcelSizeX();
    }

    public static int getConfiguredParcelSizeY() {
        return CONFIG.getParcelSizeY();
    }

    public static int getConfiguredParcelSizeZ() {
        return CONFIG.getParcelSizeZ();
    }

    public static String getFarmWorldName(UUID owner) {
        String prefix = CONFIG.getFarmWorldNamePrefix();
        return (prefix == null ? "MGHG_Farm_" : prefix) + owner.toString();
    }

    public static @Nullable World resolveLobbyWorld() {
        Universe universe = Universe.get();
        if (universe == null) return null;
        World configured = resolveByName(universe.getWorlds(), CONFIG.getLobbyWorldName());
        if (configured != null) return configured;

        for (World world : universe.getWorlds().values()) {
            if (world == null || isFarmWorld(world)) continue;
            String name = world.getName();
            if (name != null && name.toLowerCase(Locale.ROOT).contains("lobby")) {
                return world;
            }
        }
        return firstNonFarmWorld(universe);
    }

    public static @Nullable World resolveSurvivalWorld() {
        Universe universe = Universe.get();
        if (universe == null) return null;
        World configured = resolveByName(universe.getWorlds(), CONFIG.getSurvivalWorldName());
        if (configured != null) return configured;

        for (World world : universe.getWorlds().values()) {
            if (world == null || isFarmWorld(world)) continue;
            String name = world.getName();
            if (name == null) continue;
            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.contains("survival") || lower.contains("zone1")) {
                return world;
            }
        }
        return firstNonFarmWorld(universe);
    }

    public static boolean isFarmWorld(@Nullable World world) {
        if (world == null) return false;
        String name = world.getName();
        if (name == null) return false;
        String prefix = CONFIG.getFarmWorldNamePrefix();
        if (prefix == null || prefix.isBlank()) return false;
        return name.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT));
    }

    @Nullable
    public static UUID getOwnerFromFarmWorld(@Nullable World world) {
        if (!isFarmWorld(world)) {
            return null;
        }
        String name = world.getName();
        String prefix = CONFIG.getFarmWorldNamePrefix();
        if (name == null || prefix == null || prefix.isBlank()) {
            return null;
        }
        if (!name.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))) {
            return null;
        }
        String raw = name.substring(prefix.length());
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static CompletableFuture<World> ensureFarmWorld(UUID owner) {
        Universe universe = Universe.get();
        if (universe == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Universe not ready"));
        }

        String worldName = getFarmWorldName(owner);
        sanitizeWorldDirectory(resolveUniverseWorldPath(universe, worldName));
        sanitizeWorldDirectory(resolveBackupWorldPath(worldName));
        World existing = universe.getWorld(worldName);
        if (existing != null) {
            queueSnapshot(existing);
            return CompletableFuture.completedFuture(existing);
        }

        boolean restored = restoreWorldFromBackupIfNeeded(universe, worldName);
        if (universe.isWorldLoadable(worldName)) {
            return universe.loadWorld(worldName)
                    .thenApply(world -> {
                        applyWorldConfig(world);
                        initializeParcel(world, owner, false);
                        queueSnapshot(world);
                        if (restored) {
                            LOGGER.atInfo().log("[MGHG|FARM] Restored and loaded farm world %s from backup", worldName);
                        } else {
                            LOGGER.atInfo().log("[MGHG|FARM] Loaded farm world %s from disk", worldName);
                        }
                        return world;
                    });
        }

        String gen = CONFIG.getWorldGenProvider();
        String storage = CONFIG.getChunkStorageProvider();

        return universe.addWorld(worldName, gen, storage)
                .thenApply(world -> {
                    applyWorldConfig(world);
                    initializeParcel(world, owner, true);
                    queueSnapshot(world);
                    LOGGER.atInfo().log("[MGHG|FARM] Created farm world %s (gen=%s storage=%s)", worldName, gen, storage);
                    return world;
                });
    }

    private static void applyWorldConfig(World world) {
        WorldConfig cfg = world.getWorldConfig();
        cfg.setPvpEnabled(CONFIG.isPvpEnabled());
        cfg.setSpawningNPC(CONFIG.isSpawningNPC());
        if (isCustomWeatherId(cfg.getForcedWeather())) {
            cfg.setForcedWeather(null);
        }
        String display = CONFIG.getDisplayName();
        if (display != null && !display.isBlank()) {
            cfg.setDisplayName(display);
        }
        cfg.markChanged();
    }

    private static void initializeParcel(World world, UUID owner, boolean applyLegacyBlocks) {
        Transform baseSpawn = resolveFarmSpawn(world, owner);
        MghgParcelBounds desiredBounds = buildParcelBounds(baseSpawn);
        MghgParcel parcel = MghgParcelManager.getOrCreate(
                owner,
                desiredBounds.getOriginX(),
                desiredBounds.getOriginY(),
                desiredBounds.getOriginZ()
        );
        MghgParcelBounds bounds = parcel.getBounds();
        if (bounds == null) {
            parcel.setBounds(desiredBounds);
            bounds = desiredBounds;
        }

        MghgParcelBlocks blocks = parcel.getBlocks();
        if (shouldRebaseParcel(bounds, desiredBounds, blocks)) {
            LOGGER.atInfo().log("[MGHG|FARM] Rebasing parcel bounds for %s from y=%d to y=%d",
                    owner, bounds.getOriginY(), desiredBounds.getOriginY());
            parcel.setBounds(desiredBounds);
            bounds = desiredBounds;
            blocks = null;
            parcel.setBlocks(null);
            MghgParcelManager.save();
        }

        if (applyLegacyBlocks) {
            MghgParcelBounds finalBounds = bounds;
            world.execute(() -> {
                MghgParcelBlocks current = parcel.getBlocks();
                if (current != null && !current.isEmpty() && current.isCompatible(finalBounds)) {
                    current.apply(world, finalBounds);
                }
            });
        }
    }

    private static MghgParcelBounds buildParcelBounds(Transform spawn) {
        int sizeX = CONFIG.getParcelSizeX();
        int sizeY = CONFIG.getParcelSizeY();
        int sizeZ = CONFIG.getParcelSizeZ();
        int originX = (int) Math.floor(spawn.getPosition().x) - (sizeX / 2);
        // Keep floor-level area in bounds for flat/custom map spawns.
        int originY = ((int) Math.floor(spawn.getPosition().y)) - 1;
        int originZ = (int) Math.floor(spawn.getPosition().z) - (sizeZ / 2);
        return new MghgParcelBounds(
                originX,
                originY,
                originZ,
                sizeX,
                sizeY,
                sizeZ
        );
    }

    private static boolean shouldRebaseParcel(
            @Nullable MghgParcelBounds current,
            @Nullable MghgParcelBounds desired,
            @Nullable MghgParcelBlocks blocks
    ) {
        if (current == null || desired == null) {
            return false;
        }
        boolean sameOrigin = current.getOriginX() == desired.getOriginX()
                && current.getOriginY() == desired.getOriginY()
                && current.getOriginZ() == desired.getOriginZ();
        if (sameOrigin) {
            return false;
        }
        return blocks == null || blocks.isEffectivelyEmpty();
    }

    public static Transform resolveFarmSpawn(World world, UUID owner) {
        Transform resolved = resolveRawSpawn(world, owner);
        int guardRadius = CONFIG.getFarmSpawnOriginGuardRadius();
        if (guardRadius <= 0) {
            return resolved;
        }
        double x = resolved.getPosition().x;
        double z = resolved.getPosition().z;
        if (Math.abs(x) <= guardRadius && Math.abs(z) <= guardRadius) {
            return new Transform(
                    new Vector3d(CONFIG.getFarmSpawnX(), CONFIG.getFarmSpawnY(), CONFIG.getFarmSpawnZ()),
                    resolved.getRotation()
            );
        }
        return resolved;
    }

    public static Transform resolveSafeSurfaceSpawn(World world, Transform preferred) {
        int centerX = (int) Math.floor(preferred.getPosition().x);
        int centerZ = (int) Math.floor(preferred.getPosition().z);
        Vector3f rotation = preferred.getRotation();
        int fallbackY = clampY((int) Math.floor(preferred.getPosition().y));

        int bestX = centerX;
        int bestZ = centerZ;
        int bestDistance = Integer.MAX_VALUE;
        int bestY = Integer.MIN_VALUE;

        for (int radius = 0; radius <= SAFE_SPAWN_SEARCH_RADIUS_BLOCKS; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    int x = centerX + dx;
                    int z = centerZ + dz;
                    int candidateY = findSafeSpawnY(world, x, z);
                    if (candidateY == Integer.MIN_VALUE) {
                        continue;
                    }
                    int manhattan = Math.abs(dx) + Math.abs(dz);
                    if (bestY == Integer.MIN_VALUE || manhattan < bestDistance) {
                        bestDistance = manhattan;
                        bestX = x;
                        bestY = candidateY;
                        bestZ = z;
                    }
                }
            }
            if (bestY != Integer.MIN_VALUE) {
                break;
            }
        }

        int resolvedY = bestY == Integer.MIN_VALUE ? fallbackY : bestY;
        return new Transform(new Vector3d(bestX + 0.5, resolvedY, bestZ + 0.5), rotation);
    }

    private static Transform resolveRawSpawn(World world, UUID owner) {
        ISpawnProvider provider = world.getWorldConfig().getSpawnProvider();
        if (provider != null) {
            return provider.getSpawnPoint(world, owner);
        }
        return new Transform(
                new Vector3d(CONFIG.getFarmSpawnX(), CONFIG.getFarmSpawnY(), CONFIG.getFarmSpawnZ()),
                new Vector3f(0.0f, 0.0f, 0.0f)
        );
    }

    private static int findSafeSpawnY(World world, int x, int z) {
        for (int y = SAFE_SPAWN_MAX_Y - 2; y >= SAFE_SPAWN_MIN_Y; y--) {
            if (!isSolid(world, x, y, z)) {
                continue;
            }
            if (isEmpty(world, x, y + 1, z) && isEmpty(world, x, y + 2, z)) {
                return y + 1;
            }
        }
        return Integer.MIN_VALUE;
    }

    private static boolean isSolid(World world, int x, int y, int z) {
        BlockType block = world.getBlockType(x, y, z);
        return block != null && block.getMaterial() == BlockMaterial.Solid;
    }

    private static boolean isEmpty(World world, int x, int y, int z) {
        if (y < SAFE_SPAWN_MIN_Y || y > SAFE_SPAWN_MAX_Y) {
            return false;
        }
        BlockType block = world.getBlockType(x, y, z);
        return block == null || block.getMaterial() == BlockMaterial.Empty;
    }

    private static int clampY(int y) {
        return Math.max(SAFE_SPAWN_MIN_Y, Math.min(SAFE_SPAWN_MAX_Y, y));
    }

    private static @Nullable World firstNonFarmWorld(Universe universe) {
        for (World world : universe.getWorlds().values()) {
            if (world != null && !isFarmWorld(world)) {
                return world;
            }
        }
        return null;
    }

    private static @Nullable World resolveByName(Map<String, World> worlds, @Nullable String preferredName) {
        if (preferredName == null || preferredName.isBlank()) return null;
        World exact = worlds.get(preferredName);
        if (exact != null) return exact;
        for (World world : worlds.values()) {
            if (world == null || world.getName() == null) continue;
            if (preferredName.equalsIgnoreCase(world.getName())) {
                return world;
            }
        }
        return null;
    }

    private static void startBackupTask() {
        stopBackupTask();
        if (!CONFIG.isEnableFullInstancePersistence()) {
            return;
        }
        int intervalSeconds = CONFIG.getFullInstanceBackupIntervalSeconds();
        backupTask = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(
                MghgFarmWorldManager::backupTickSafe,
                intervalSeconds,
                intervalSeconds,
                TimeUnit.SECONDS
        );
    }

    private static void stopBackupTask() {
        ScheduledFuture<?> current = backupTask;
        backupTask = null;
        if (current != null) {
            current.cancel(false);
        }
    }

    private static void backupTickSafe() {
        try {
            backupTick();
        } catch (Exception e) {
            LOGGER.atWarning().log("[MGHG|FARM] Backup tick failed: %s", e.getMessage());
        }
    }

    private static void backupTick() {
        if (!CONFIG.isEnableFullInstancePersistence()) {
            return;
        }
        Universe universe = Universe.get();
        if (universe == null) {
            return;
        }
        List<String> farmWorldNames = collectFarmWorldNames(universe);
        if (farmWorldNames.isEmpty()) {
            return;
        }
        int worldsPerTick = Math.min(CONFIG.getFullInstanceBackupWorldsPerTick(), farmWorldNames.size());
        int start = Math.floorMod(backupCursor, farmWorldNames.size());
        for (int i = 0; i < worldsPerTick; i++) {
            int idx = (start + i) % farmWorldNames.size();
            backupWorldByName(universe, farmWorldNames.get(idx));
        }
        backupCursor = (start + worldsPerTick) % farmWorldNames.size();
    }

    private static void backupAllNow() {
        if (!CONFIG.isEnableFullInstancePersistence()) {
            return;
        }
        Universe universe = Universe.get();
        if (universe == null) {
            return;
        }
        for (String worldName : collectFarmWorldNames(universe)) {
            backupWorldByName(universe, worldName);
        }
    }

    private static void queueSnapshot(@Nullable World world) {
        if (!CONFIG.isEnableFullInstancePersistence() || world == null) {
            return;
        }
        HytaleServer.SCHEDULED_EXECUTOR.execute(() -> {
            Universe universe = Universe.get();
            if (universe == null) {
                return;
            }
            String worldName = world.getName();
            if (worldName != null && !worldName.isBlank()) {
                backupWorldByName(universe, worldName);
            }
        });
    }

    private static boolean restoreWorldFromBackupIfNeeded(Universe universe, String worldName) {
        Path worldPath = resolveUniverseWorldPath(universe, worldName);
        Path backupPath = resolveBackupWorldPath(worldName);
        sanitizeWorldDirectory(worldPath);
        sanitizeWorldDirectory(backupPath);
        if (Files.isDirectory(worldPath) && isValidWorldDirectory(worldPath)) {
            return false;
        }
        if (!isValidWorldDirectory(backupPath)) {
            return false;
        }
        try {
            if (Files.exists(worldPath) && !isValidWorldDirectory(worldPath)) {
                deleteRecursively(worldPath);
            }
            copyDirectory(backupPath, worldPath);
            sanitizeWorldDirectory(worldPath);
            LOGGER.atInfo().log("[MGHG|FARM] Restored world data for %s from %s", worldName, backupPath);
            return true;
        } catch (Exception e) {
            LOGGER.atWarning().log("[MGHG|FARM] Failed to restore world %s from backup: %s", worldName, e.getMessage());
            return false;
        }
    }

    private static void backupWorldByName(Universe universe, String worldName) {
        if (worldName == null || worldName.isBlank() || !isFarmWorldName(worldName)) {
            return;
        }
        World loaded = universe.getWorld(worldName);
        if (loaded != null) {
            backupWorldToSnapshot(universe, loaded);
            return;
        }
        backupWorldDirectoryToSnapshot(universe, worldName);
    }

    private static void backupWorldToSnapshot(Universe universe, World world) {
        if (world == null || !isFarmWorld(world)) {
            return;
        }
        String worldName = world.getName();
        if (worldName == null || worldName.isBlank()) {
            return;
        }
        Path source = resolveUniverseWorldPath(universe, worldName);
        if (!isValidWorldDirectory(source)) {
            return;
        }
        Path backup = resolveBackupWorldPath(worldName);
        Store<ChunkStore> store = world.getChunkStore().getStore();
        ChunkSavingSystems.Data saveData = store.getResource(ChunkStore.SAVE_RESOURCE);
        try {
            if (saveData != null) {
                saveData.isSaving = false;
                saveData.waitForSavingChunks().join();
            }
            copyDirectory(source, backup);
            sanitizeWorldDirectory(backup);
        } catch (Exception e) {
            LOGGER.atWarning().log("[MGHG|FARM] Failed to snapshot world %s: %s", worldName, e.getMessage());
        } finally {
            if (saveData != null) {
                saveData.isSaving = true;
            }
        }
    }

    private static void backupWorldDirectoryToSnapshot(Universe universe, String worldName) {
        Path source = resolveUniverseWorldPath(universe, worldName);
        if (!isValidWorldDirectory(source)) {
            return;
        }
        Path backup = resolveBackupWorldPath(worldName);
        try {
            copyDirectory(source, backup);
            sanitizeWorldDirectory(backup);
        } catch (Exception e) {
            LOGGER.atWarning().log("[MGHG|FARM] Failed to snapshot unloaded world %s: %s", worldName, e.getMessage());
        }
    }

    private static List<String> collectFarmWorldNames(Universe universe) {
        Set<String> names = new LinkedHashSet<>();

        for (World world : universe.getWorlds().values()) {
            if (world == null) {
                continue;
            }
            String worldName = world.getName();
            if (isFarmWorldName(worldName)) {
                names.add(worldName);
            }
        }

        Path worldsRoot = universe.getPath().resolve("worlds").toAbsolutePath().normalize();
        if (Files.isDirectory(worldsRoot)) {
            try (var stream = Files.list(worldsRoot)) {
                stream.filter(Files::isDirectory)
                        .map(path -> path.getFileName())
                        .filter(fileName -> fileName != null)
                        .map(Path::toString)
                        .filter(MghgFarmWorldManager::isFarmWorldName)
                        .forEach(names::add);
            } catch (Exception e) {
                LOGGER.atWarning().log("[MGHG|FARM] Failed to enumerate worlds for backup: %s", e.getMessage());
            }
        }

        return new ArrayList<>(names);
    }

    private static boolean isFarmWorldName(@Nullable String worldName) {
        if (worldName == null || worldName.isBlank()) {
            return false;
        }
        String prefix = CONFIG.getFarmWorldNamePrefix();
        if (prefix == null || prefix.isBlank()) {
            return false;
        }
        return worldName.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT));
    }

    private static Path resolveUniverseWorldPath(Universe universe, String worldName) {
        return universe.getPath().resolve("worlds").resolve(worldName).toAbsolutePath().normalize();
    }

    private static Path resolveBackupWorldPath(String worldName) {
        return MghgStoragePaths.resolveInDataRoot(WORLD_BACKUPS_DIR, worldName);
    }

    private static void copyDirectory(Path source, Path destination) throws IOException {
        Path tmp = destination.resolveSibling(destination.getFileName() + ".tmp");
        deleteRecursively(tmp);
        Files.createDirectories(tmp);

        try {
            copyTree(source, tmp, true);
        } catch (RuntimeException e) {
            deleteRecursively(tmp);
            throw new IOException("World snapshot copy failed: " + e.getMessage(), e);
        }

        deleteRecursively(destination);
        Path parent = destination.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try {
            Files.move(tmp, destination, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException moveError) {
            // Fallback for transient FS locks on directory move.
            deleteRecursively(destination);
            Files.createDirectories(destination);
            copyTree(tmp, destination, false);
            deleteRecursively(tmp);
        }
    }

    private static void copyTree(Path source, Path destination, boolean skipLockFiles) throws IOException {
        try (var stream = Files.walk(source)) {
            stream.forEach(path -> {
                try {
                    Path relative = source.relativize(path);
                    Path target = destination.resolve(relative);
                    if (Files.isDirectory(path)) {
                        Files.createDirectories(target);
                        return;
                    }
                    String fileName = path.getFileName() == null ? "" : path.getFileName().toString();
                    if (skipLockFiles && fileName.endsWith(".lck")) {
                        return;
                    }
                    Path targetParent = target.getParent();
                    if (targetParent != null) {
                        Files.createDirectories(targetParent);
                    }
                    Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception e) {
                    throw new RuntimeException("Failed while copying " + path + ": " + e.getMessage(), e);
                }
            });
        }
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (var walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        }
    }

    private static boolean isValidWorldDirectory(@Nullable Path path) {
        if (path == null || !Files.isDirectory(path)) {
            return false;
        }
        return Files.exists(path.resolve("config.json")) || Files.exists(path.resolve("config.bson"));
    }

    private static void sanitizePersistedFarmWorldConfigs() {
        Universe universe = Universe.get();
        if (universe != null && universe.getPath() != null) {
            sanitizeFarmWorldConfigRoot(universe.getPath().resolve("worlds"));
        }
        sanitizeFarmWorldConfigRoot(MghgStoragePaths.resolveInDataRoot(WORLD_BACKUPS_DIR));
    }

    private static void sanitizeFarmWorldConfigRoot(@Nullable Path root) {
        if (root == null || !Files.isDirectory(root)) {
            return;
        }
        try (var stream = Files.list(root)) {
            stream.filter(Files::isDirectory).forEach(path -> {
                Path namePath = path.getFileName();
                String worldName = namePath == null ? null : namePath.toString();
                if (!isFarmWorldName(worldName)) {
                    return;
                }
                sanitizeWorldDirectory(path);
            });
        } catch (Exception e) {
            LOGGER.atWarning().log("[MGHG|FARM] Failed to sanitize world configs under %s: %s", root, e.getMessage());
        }
    }

    private static void sanitizeWorldDirectory(@Nullable Path worldDir) {
        if (worldDir == null || !Files.isDirectory(worldDir)) {
            return;
        }
        boolean changed = false;
        changed |= sanitizeWorldConfigFileIfPresent(worldDir.resolve("config.json"));
        changed |= sanitizeWorldConfigFileIfPresent(worldDir.resolve("config.json.bak"));
        if (changed) {
            LOGGER.atInfo().log("[MGHG|FARM] Sanitized invalid ForcedWeather in %s", worldDir);
        }
    }

    private static boolean sanitizeWorldConfigFileIfPresent(@Nullable Path configPath) {
        if (configPath == null || !Files.isRegularFile(configPath)) {
            return false;
        }
        try {
            String raw = Files.readString(configPath, StandardCharsets.UTF_8);
            Matcher matcher = FORCED_WEATHER_STRING_PATTERN.matcher(raw);
            StringBuffer output = new StringBuffer(raw.length());
            boolean changed = false;

            while (matcher.find()) {
                String weatherId = matcher.group(1);
                if (!isCustomWeatherId(weatherId)) {
                    continue;
                }
                matcher.appendReplacement(output, "\"ForcedWeather\": null");
                changed = true;
            }
            if (!changed) {
                return false;
            }
            matcher.appendTail(output);
            Files.writeString(configPath, output.toString(), StandardCharsets.UTF_8);
            return true;
        } catch (Exception e) {
            LOGGER.atWarning().log("[MGHG|FARM] Failed to sanitize config file %s: %s", configPath, e.getMessage());
            return false;
        }
    }

    private static boolean isCustomWeatherId(@Nullable String weatherId) {
        return weatherId != null && weatherId.toLowerCase(Locale.ROOT).startsWith("mghg_");
    }
}
