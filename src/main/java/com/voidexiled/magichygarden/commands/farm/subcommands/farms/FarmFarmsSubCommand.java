package com.voidexiled.magichygarden.commands.farm.subcommands.farms;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcel;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcelAccess;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcelManager;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcelRole;
import com.voidexiled.magichygarden.features.farming.storage.MghgPlayerNameManager;
import org.jspecify.annotations.NonNull;

import java.util.UUID;

public class FarmFarmsSubCommand extends AbstractPlayerCommand {
    public FarmFarmsSubCommand() {
        super("farms", "magichygarden.command.farm.farms.description");
    }

    @Override
    protected void execute(
            @NonNull CommandContext ctx,
            @NonNull Store<EntityStore> store,
            @NonNull Ref<EntityStore> playerEntityRef,
            @NonNull PlayerRef playerRef,
            @NonNull World world
    ) {
        MghgPlayerNameManager.remember(playerRef);
        UUID self = playerRef.getUuid();
        MghgParcel own = MghgParcelManager.getByOwner(self);
        if (own == null) {
            ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("Tu granja no esta inicializada todavia."));
            ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("Usa /farm home para crearla."));
        } else {
            ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text(
                    "Tu granja: owner=" + resolveName(self) + " | " + self
            ));
        }

        int memberships = 0;
        for (MghgParcel parcel : MghgParcelManager.all()) {
            if (parcel == null || parcel.getOwner() == null || self.equals(parcel.getOwner())) {
                continue;
            }
            MghgParcelRole role = MghgParcelAccess.resolveRole(parcel, self);
            if (role == MghgParcelRole.MEMBER || role == MghgParcelRole.MANAGER) {
                memberships++;
                ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text(
                        " - " + role.name() + " en owner=" + resolveName(parcel.getOwner())
                                + " | " + parcel.getOwner()
                ));
            }
        }

        if (memberships == 0) {
            ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("No perteneces a otras granjas."));
            return;
        }
        ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text("Total granjas donde eres miembro: " + memberships));
    }

    private static String resolveName(@NonNull UUID playerUuid) {
        return MghgPlayerNameManager.resolve(playerUuid);
    }
}
