package com.voidexiled.magichygarden.commands.farm.subcommands.admin.subcommands.status;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.voidexiled.magichygarden.commands.farm.subcommands.admin.shared.FarmAdminCommandShared;
import com.voidexiled.magichygarden.features.farming.events.MghgFarmEventScheduler;
import com.voidexiled.magichygarden.features.farming.events.MghgGlobalFarmEventState;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcelManager;
import com.voidexiled.magichygarden.features.farming.shop.MghgShopStockManager;
import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.Locale;


public class FarmAdminStatusSubCommand extends AbstractPlayerCommand {
    public FarmAdminStatusSubCommand() {
        super("status", "magichygarden.command.farm.admin.status.description");
    }

    @Override
    protected void execute(@NonNull CommandContext commandContext,
                           @NonNull Store<EntityStore> store,
                           @NonNull Ref<EntityStore> ref,
                           @NonNull PlayerRef playerRef,
                           @NonNull World world) {
        sendStatus(commandContext);
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
        String eventId = eventActive ? FarmAdminCommandShared.fallback(state.eventId(), "-") : "-";
        String eventWeather = eventActive ? FarmAdminCommandShared.fallback(state.weatherId(), "-") : "-";
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
                FarmAdminCommandShared.formatDuration(nextStock)
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
}
