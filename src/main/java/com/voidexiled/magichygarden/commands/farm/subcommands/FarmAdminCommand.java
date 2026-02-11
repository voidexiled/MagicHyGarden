package com.voidexiled.magichygarden.commands.farm.subcommands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.DefaultArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.voidexiled.magichygarden.features.farming.economy.MghgEconomyManager;
import com.voidexiled.magichygarden.features.farming.events.MghgFarmEventScheduler;
import com.voidexiled.magichygarden.features.farming.events.MghgGlobalFarmEventState;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcel;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcelBlocks;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcelBounds;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcelInviteService;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcelManager;
import com.voidexiled.magichygarden.features.farming.shop.MghgShopConfig;
import com.voidexiled.magichygarden.features.farming.shop.MghgShopStockManager;
import com.voidexiled.magichygarden.features.farming.storage.MghgStoragePaths;
import com.voidexiled.magichygarden.features.farming.worlds.MghgFarmWorldManager;
import org.jspecify.annotations.NonNull;

import javax.annotation.Nullable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class FarmAdminCommand extends AbstractPlayerCommand {
    private final DefaultArg<String> actionArg;
    private final DefaultArg<String> value1Arg;
    private final DefaultArg<String> value2Arg;
    private final DefaultArg<String> value3Arg;

    public FarmAdminCommand() {
        super("admin", "magichygarden.command.farm.admin.description");
        this.actionArg = withDefaultArg(
                "action",
                "magichygarden.command.farm.admin.args.action.description",
                ArgTypes.STRING,
                "status",
                "status"
        );
        this.value1Arg = withDefaultArg(
                "value1",
                "magichygarden.command.farm.admin.args.value1.description",
                ArgTypes.STRING,
                "",
                ""
        );
        this.value2Arg = withDefaultArg(
                "value2",
                "magichygarden.command.farm.admin.args.value2.description",
                ArgTypes.STRING,
                "",
                ""
        );
        this.value3Arg = withDefaultArg(
                "value3",
                "magichygarden.command.farm.admin.args.value3.description",
                ArgTypes.STRING,
                "",
                ""
        );
    }

    @Override
    protected void execute(
            @NonNull CommandContext ctx,
            @NonNull Store<EntityStore> store,
            @NonNull Ref<EntityStore> playerEntityRef,
            @NonNull PlayerRef playerRef,
            @NonNull World world
    ) {
        String action = normalize(actionArg.get(ctx));
        String value1 = raw(value1Arg.get(ctx));
        String value2 = raw(value2Arg.get(ctx));
        String value3 = raw(value3Arg.get(ctx));

        switch (action) {
            case "status" -> sendStatus(ctx);
            case "paths" -> sendPaths(ctx);
            case "parcel" -> handleParcel(ctx, playerRef, value1);
            case "world" -> handleWorld(ctx, playerRef, value1, value2);
            case "stock" -> handleStock(ctx, value1, value2, value3);
            case "economy", "eco" -> handleEconomy(ctx, playerRef, value1, value2, value3);
            default -> ctx.sendMessage(Message.raw(
                    "Uso: /farm admin <status|paths|parcel|world|stock|economy>"
            ));
        }
    }

    private static void sendStatus(@NonNull CommandContext ctx) {
        Universe universe = Universe.get();
        int totalWorlds = 0;
        int farmWorlds = 0;
        int onlinePlayers = 0;
        if (universe != null) {
            totalWorlds = universe.getWorlds().size();
            onlinePlayers = universe.getPlayers().size();
            for (World world : universe.getWorlds().values()) {
                if (MghgFarmEventScheduler.isFarmWorld(world)) {
                    farmWorlds++;
                }
            }
        }

        int parcelCount = MghgParcelManager.all().size();
        MghgGlobalFarmEventState state = MghgFarmEventScheduler.getState();
        boolean eventActive = state != null && state.isActive(Instant.now());
        String eventType = eventActive ? state.eventType().name() : "NONE";
        String eventId = eventActive ? fallback(state.eventId(), "-") : "-";
        String eventWeather = eventActive ? fallback(state.weatherId(), "-") : "-";
        long nextStock = MghgShopStockManager.getRemainingRestockSeconds();
        int configuredItems = MghgShopStockManager.getConfiguredItems().length;

        ctx.sendMessage(Message.raw(String.format(
                Locale.ROOT,
                "Worlds total=%d | farm=%d | onlinePlayers=%d",
                totalWorlds,
                farmWorlds,
                onlinePlayers
        )));
        ctx.sendMessage(Message.raw(String.format(
                Locale.ROOT,
                "Parcels=%d | shopItems=%d | nextRestock=%s",
                parcelCount,
                configuredItems,
                formatDuration(nextStock)
        )));
        ctx.sendMessage(Message.raw(String.format(
                Locale.ROOT,
                "Event active=%s | type=%s | id=%s | weather=%s",
                eventActive,
                eventType,
                eventId,
                eventWeather
        )));
    }

    private static void sendPaths(@NonNull CommandContext ctx) {
        Path root = MghgStoragePaths.dataRoot();
        Path parcelDir = MghgParcelManager.getStoreDirectory();
        Path invitePath = MghgParcelInviteService.getStorePath();
        Path ecoPath = MghgEconomyManager.getStorePath();
        Path shopPath = MghgShopStockManager.getStorePath();
        Path backupRoot = MghgFarmWorldManager.getBackupRootPath();
        ctx.sendMessage(Message.raw("Data root: " + root));
        ctx.sendMessage(Message.raw("Parcels dir: " + parcelDir));
        ctx.sendMessage(Message.raw("Parcel invites file: " + invitePath));
        ctx.sendMessage(Message.raw("World backups dir: " + backupRoot));
        ctx.sendMessage(Message.raw("Economy file: " + ecoPath));
        ctx.sendMessage(Message.raw("Shop stock file: " + shopPath));
    }

    private static void handleWorld(
            @NonNull CommandContext ctx,
            @NonNull PlayerRef executor,
            @Nullable String actionToken,
            @Nullable String targetToken
    ) {
        String action = normalize(actionToken);
        switch (action) {
            case "", "status", "list" -> sendWorldStatus(ctx);
            case "backup", "snapshot" -> forceWorldBackup(ctx, executor, targetToken);
            case "backup-all", "snapshot-all", "backupall", "snapshotall" -> {
                MghgFarmWorldManager.forceSnapshotAll();
                ctx.sendMessage(Message.raw("Snapshot forzado para todas las farm worlds."));
            }
            case "restore" -> forceWorldRestore(ctx, executor, targetToken);
            case "ensure" -> ensureWorld(ctx, executor, targetToken);
            default -> ctx.sendMessage(Message.raw(
                    "Uso: /farm admin world <status|list|backup|backup-all|restore|ensure> [self|player|uuid]"
            ));
        }
    }

    private static void sendWorldStatus(@NonNull CommandContext ctx) {
        Universe universe = Universe.get();
        if (universe == null) {
            ctx.sendMessage(Message.raw("Universe no disponible."));
            return;
        }
        int count = 0;
        for (World candidate : universe.getWorlds().values()) {
            if (candidate == null || !MghgFarmWorldManager.isFarmWorld(candidate)) {
                continue;
            }
            count++;
            UUID owner = MghgFarmWorldManager.getOwnerFromFarmWorld(candidate);
            Path backupPath = MghgFarmWorldManager.getBackupWorldPath(owner);
            boolean backupExists = Files.isDirectory(backupPath);
            ctx.sendMessage(Message.raw(String.format(
                    Locale.ROOT,
                    "%s | owner=%s | players=%d | ticking=%s | backup=%s",
                    fallback(candidate.getName(), "(unnamed)"),
                    owner == null ? "-" : owner.toString(),
                    candidate.getPlayerRefs().size(),
                    candidate.isTicking(),
                    backupExists
            )));
            if (count >= 60) {
                ctx.sendMessage(Message.raw("World list truncada en 60 entradas."));
                break;
            }
        }
        if (count == 0) {
            ctx.sendMessage(Message.raw("No hay farm worlds activas."));
        }
    }

    private static void forceWorldBackup(
            @NonNull CommandContext ctx,
            @NonNull PlayerRef executor,
            @Nullable String targetToken
    ) {
        UUID owner = resolveUuid(executor, targetToken);
        if (owner == null) {
            ctx.sendMessage(Message.raw("No pude resolver owner para backup. Usa self|player|uuid."));
            return;
        }
        boolean ok = MghgFarmWorldManager.forceSnapshotOwner(owner);
        if (!ok) {
            ctx.sendMessage(Message.raw("No pude forzar snapshot (world/universe no disponible)."));
            return;
        }
        ctx.sendMessage(Message.raw("Snapshot forzado para " + owner));
    }

    private static void forceWorldRestore(
            @NonNull CommandContext ctx,
            @NonNull PlayerRef executor,
            @Nullable String targetToken
    ) {
        UUID owner = resolveUuid(executor, targetToken);
        if (owner == null) {
            ctx.sendMessage(Message.raw("No pude resolver owner para restore. Usa self|player|uuid."));
            return;
        }
        boolean restored = MghgFarmWorldManager.restoreOwnerFromSnapshot(owner);
        ctx.sendMessage(Message.raw(restored
                ? "Restore desde snapshot aplicado para " + owner
                : "No habia snapshot/restauracion para " + owner));
    }

    private static void ensureWorld(
            @NonNull CommandContext ctx,
            @NonNull PlayerRef executor,
            @Nullable String targetToken
    ) {
        UUID owner = resolveUuid(executor, targetToken);
        if (owner == null) {
            ctx.sendMessage(Message.raw("No pude resolver owner para ensure. Usa self|player|uuid."));
            return;
        }
        try {
            World world = MghgFarmWorldManager.ensureFarmWorld(owner).join();
            ctx.sendMessage(Message.raw("Farm world asegurada: " + fallback(world.getName(), "-")));
        } catch (Exception e) {
            ctx.sendMessage(Message.raw("No pude asegurar farm world: " + e.getMessage()));
        }
    }

    private static void handleParcel(
            @NonNull CommandContext ctx,
            @NonNull PlayerRef executor,
            @Nullable String token
    ) {
        String normalized = normalize(token);
        if ("list".equals(normalized)) {
            int count = 0;
            for (MghgParcel parcel : MghgParcelManager.all()) {
                if (parcel == null || parcel.getOwner() == null) {
                    continue;
                }
                count++;
                String worldName = MghgFarmWorldManager.getFarmWorldName(parcel.getOwner());
                MghgParcelBounds bounds = parcel.getBounds();
                ctx.sendMessage(Message.raw(String.format(
                        Locale.ROOT,
                        "%s | owner=%s | world=%s | spawn=(%d,%d,%d) | origin=(%d,%d,%d)",
                        fallback(parcel.getId() == null ? null : parcel.getId().toString(), "-"),
                        parcel.getOwner(),
                        worldName,
                        parcel.resolveSpawnX(),
                        parcel.resolveSpawnY(),
                        parcel.resolveSpawnZ(),
                        bounds == null ? 0 : bounds.getOriginX(),
                        bounds == null ? 0 : bounds.getOriginY(),
                        bounds == null ? 0 : bounds.getOriginZ()
                )));
                if (count >= 40) {
                    ctx.sendMessage(Message.raw("Parcel list truncada en 40 entradas."));
                    break;
                }
            }
            ctx.sendMessage(Message.raw("Total parcels: " + MghgParcelManager.all().size()));
            return;
        }

        if ("save".equals(normalized)) {
            MghgParcelManager.save();
            ctx.sendMessage(Message.raw("Parcel store guardado a disco."));
            return;
        }

        if ("reload".equals(normalized)) {
            MghgParcelManager.load();
            ctx.sendMessage(Message.raw("Parcel store recargado desde disco."));
            return;
        }

        UUID owner = resolveUuid(executor, token);
        if (owner == null) {
            ctx.sendMessage(Message.raw("No pude resolver owner. Usa UUID, player online, 'self', 'list', 'save' o 'reload'."));
            return;
        }
        MghgParcel parcel = MghgParcelManager.getByOwner(owner);
        if (parcel == null) {
            ctx.sendMessage(Message.raw("Parcel no encontrada para owner=" + owner));
            return;
        }

        MghgParcelBounds bounds = parcel.getBounds();
        MghgParcelBlocks blocks = parcel.getBlocks();
        int entries = (blocks == null || blocks.getEntries() == null) ? 0 : blocks.getEntries().length;
        int members = parcel.getMembers() == null ? 0 : parcel.getMembers().length;
        boolean customSpawn = parcel.hasCustomSpawn();
        String worldName = MghgFarmWorldManager.getFarmWorldName(owner);
        Universe universe = Universe.get();
        boolean loaded = universe != null && universe.getWorld(worldName) != null;
        Path filePath = MghgParcelManager.getOwnerFile(owner);

        ctx.sendMessage(Message.raw(String.format(
                Locale.ROOT,
                "Parcel id=%s | owner=%s | world=%s | loaded=%s",
                parcel.getId(),
                owner,
                worldName,
                loaded
        )));
        if (bounds != null) {
            ctx.sendMessage(Message.raw(String.format(
                    Locale.ROOT,
                    "Bounds origin=(%d,%d,%d) size=(%d,%d,%d)",
                    bounds.getOriginX(),
                    bounds.getOriginY(),
                    bounds.getOriginZ(),
                    bounds.getSizeX(),
                    bounds.getSizeY(),
                    bounds.getSizeZ()
            )));
        } else {
            ctx.sendMessage(Message.raw("Bounds: none"));
        }
        ctx.sendMessage(Message.raw(String.format(
                Locale.ROOT,
                "Spawn=(%d,%d,%d) custom=%s | members=%d | blockEntries=%d",
                parcel.resolveSpawnX(),
                parcel.resolveSpawnY(),
                parcel.resolveSpawnZ(),
                customSpawn,
                members,
                entries
        )));
        ctx.sendMessage(Message.raw("Parcel file: " + filePath));
    }

    private static void handleStock(
            @NonNull CommandContext ctx,
            @Nullable String action,
            @Nullable String value2,
            @Nullable String value3
    ) {
        String normalizedAction = normalize(action);
        if (normalizedAction.isBlank() || "status".equals(normalizedAction)) {
            long remaining = MghgShopStockManager.getRemainingRestockSeconds();
            MghgShopConfig.ShopItem[] items = MghgShopStockManager.getConfiguredItems();
            ctx.sendMessage(Message.raw(String.format(
                    Locale.ROOT,
                    "Stock status | configured=%d | nextRestock=%s",
                    items.length,
                    formatDuration(remaining)
            )));
            for (MghgShopConfig.ShopItem item : items) {
                if (item == null || item.getId() == null || item.getId().isBlank()) {
                    continue;
                }
                int stock = MghgShopStockManager.getStock(item.getId());
                double chance = normalizeChance(item.getRestockChance()) * 100.0d;
                String buyItemId = item.resolveBuyItemId();
                ctx.sendMessage(Message.raw(String.format(
                        Locale.ROOT,
                        " - %s | stockGlobal=%d | buyItem=%s | buy=%.2f | sellBase=%.2f | restockChance=%.2f%%",
                        item.getId(),
                        stock,
                        buyItemId == null ? "-" : buyItemId,
                        item.getBuyPrice(),
                        item.getSellPrice(),
                        chance
                )));
            }
            return;
        }

        if ("restock".equals(normalizedAction)) {
            MghgShopStockManager.forceRestockNow();
            ctx.sendMessage(Message.raw("Restock forzado."));
            return;
        }

        if ("set".equals(normalizedAction)) {
            if (isBlank(value2) || isBlank(value3)) {
                ctx.sendMessage(Message.raw("Uso: /farm admin stock set <shopId> <qty>"));
                return;
            }
            Integer qty = parseInt(value3);
            if (qty == null) {
                ctx.sendMessage(Message.raw("Cantidad invalida."));
                return;
            }
            boolean ok = MghgShopStockManager.setStock(value2, qty);
            if (!ok) {
                ctx.sendMessage(Message.raw("No pude actualizar stock para '" + value2 + "'."));
                return;
            }
            ctx.sendMessage(Message.raw("Stock actualizado: " + value2 + "=" + qty));
            return;
        }

        ctx.sendMessage(Message.raw("Uso: /farm admin stock <status|restock|set>"));
    }

    private static void handleEconomy(
            @NonNull CommandContext ctx,
            @NonNull PlayerRef executor,
            @Nullable String targetToken,
            @Nullable String actionToken,
            @Nullable String amountToken
    ) {
        if (isBlank(targetToken)) {
            ctx.sendMessage(Message.raw("Uso: /farm admin economy <player|uuid|self> [set|add|sub] [amount]"));
            return;
        }

        UUID target = resolveUuid(executor, targetToken);
        if (target == null) {
            ctx.sendMessage(Message.raw("No pude resolver jugador/uuid objetivo."));
            return;
        }

        String action = normalize(actionToken);
        if (action.isBlank() || "status".equals(action)) {
            double balance = MghgEconomyManager.getBalance(target);
            ctx.sendMessage(Message.raw(String.format(Locale.ROOT, "Balance %s = $%.2f", target, balance)));
            return;
        }

        Double amount = parseDouble(amountToken);
        if (amount == null) {
            ctx.sendMessage(Message.raw("Monto invalido."));
            return;
        }
        if (amount < 0.0d) {
            ctx.sendMessage(Message.raw("Monto debe ser >= 0."));
            return;
        }

        switch (action) {
            case "set" -> MghgEconomyManager.setBalance(target, amount);
            case "add" -> MghgEconomyManager.deposit(target, amount);
            case "sub" -> {
                boolean ok = MghgEconomyManager.withdraw(target, amount);
                if (!ok) {
                    ctx.sendMessage(Message.raw("No pude debitar, balance insuficiente."));
                    return;
                }
            }
            default -> {
                ctx.sendMessage(Message.raw("Uso: /farm admin economy <target> [set|add|sub] [amount]"));
                return;
            }
        }
        double updated = MghgEconomyManager.getBalance(target);
        ctx.sendMessage(Message.raw(String.format(Locale.ROOT, "Balance actualizado %s = $%.2f", target, updated)));
    }

    private static @Nullable UUID resolveUuid(@NonNull PlayerRef executor, @Nullable String token) {
        if (isBlank(token) || "self".equals(normalize(token))) {
            return executor.getUuid();
        }
        String raw = token.trim();
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            // Fallback to online player name lookup.
        }

        Universe universe = Universe.get();
        if (universe == null) {
            return null;
        }
        for (PlayerRef playerRef : universe.getPlayers()) {
            if (playerRef == null || playerRef.getUsername() == null) {
                continue;
            }
            if (raw.equalsIgnoreCase(playerRef.getUsername())) {
                return playerRef.getUuid();
            }
        }
        return null;
    }

    private static String formatDuration(long seconds) {
        if (seconds <= 0L) {
            return "0s";
        }
        long h = seconds / 3600L;
        long m = (seconds % 3600L) / 60L;
        long s = seconds % 60L;
        if (h > 0L) {
            return String.format(Locale.ROOT, "%dh %dm %ds", h, m, s);
        }
        if (m > 0L) {
            return String.format(Locale.ROOT, "%dm %ds", m, s);
        }
        return String.format(Locale.ROOT, "%ds", s);
    }

    private static String normalize(@Nullable String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }

    private static String fallback(@Nullable String raw, String fallback) {
        return isBlank(raw) ? fallback : raw;
    }

    private static String raw(@Nullable String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean isBlank(@Nullable String value) {
        return value == null || value.isBlank();
    }

    private static @Nullable Integer parseInt(@Nullable String raw) {
        if (isBlank(raw)) {
            return null;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static @Nullable Double parseDouble(@Nullable String raw) {
        if (isBlank(raw)) {
            return null;
        }
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static double normalizeChance(double raw) {
        if (Double.isNaN(raw) || raw <= 0.0d) {
            return 0.0d;
        }
        double chance = raw > 1.0d ? (raw / 100.0d) : raw;
        if (chance < 0.0d) return 0.0d;
        if (chance > 1.0d) return 1.0d;
        return chance;
    }
}
