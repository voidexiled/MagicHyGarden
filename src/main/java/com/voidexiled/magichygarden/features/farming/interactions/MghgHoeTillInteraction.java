package com.voidexiled.magichygarden.features.farming.interactions;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcel;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcelAccess;
import com.voidexiled.magichygarden.features.farming.perks.MghgFarmPerkManager;
import com.voidexiled.magichygarden.features.farming.worlds.MghgFarmWorldManager;
import com.voidexiled.magichygarden.utils.chat.MghgChat;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MghgHoeTillInteraction extends SimpleBlockInteraction {
    public static final BuilderCodec<MghgHoeTillInteraction> CODEC =
            BuilderCodec.builder(MghgHoeTillInteraction.class, MghgHoeTillInteraction::new, SimpleBlockInteraction.CODEC)
                    .documentation("Farm-aware hoe tilling: enforces custom hoe + perk cap in farm parcel worlds.")
                    .build();

    private static final long MESSAGE_COOLDOWN_MILLIS = 800L;
    private static final String VANILLA_TILLED_BLOCK_ID = "Soil_Dirt_Tilled";
    private static final String TILL_WORLD_SOUND_EVENT_ID = "SFX_Hoe_T1_Till";
    private static final Map<UUID, Long> LAST_MESSAGE_AT = new ConcurrentHashMap<>();

    @Override
    protected void interactWithBlock(
            @Nonnull World world,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull InteractionType interactionType,
            @Nonnull InteractionContext context,
            @Nullable ItemStack itemInHand,
            @Nonnull Vector3i targetBlock,
            @Nonnull CooldownHandler cooldownHandler
    ) {
        BlockType source = world.getBlockType(targetBlock.x, targetBlock.y, targetBlock.z);
        if (!MghgFarmPerkManager.isTillSourceBlock(source)) {
            return;
        }

        String heldItemId = itemInHand == null || itemInHand.getItem() == null ? null : itemInHand.getItem().getId();
        boolean farmWorld = MghgFarmWorldManager.isFarmWorld(world);
        if (!farmWorld) {
            if (setBlockIfValid(world, targetBlock, VANILLA_TILLED_BLOCK_ID)) {
                playTillWorldSound(world, targetBlock);
            }
            return;
        }

        PlayerRef player = resolvePlayerRef(world, context);
        if (!MghgFarmPerkManager.isHoeItem(heldItemId)) {
            sendMessageWithCooldown(
                    player,
                    MghgChat.Channel.WARNING,
                    "You must use the custom farm hoe for tilling.\nCraft/use Tool_Hoe_Custom."
            );
            return;
        }

        MghgParcel parcel = MghgParcelAccess.resolveParcel(world);
        if (parcel == null) {
            return;
        }

        if (!canBuildInParcel(world, parcel, player)) {
            return;
        }

        String key = MghgFarmPerkManager.toBlockKey(targetBlock);
        if (!MghgFarmPerkManager.canTrackFertileBlock(parcel, key)) {
            int current = MghgFarmPerkManager.getTrackedFertileCount(parcel);
            int cap = MghgFarmPerkManager.getFertileSoilCap(parcel);
            sendMessageWithCooldown(
                    player,
                    MghgChat.Channel.WARNING,
                    "Fertile soil limit reached.\nCurrent: " + current + " / " + cap + ".\nUpgrade with /farm perks upgrade fertile_soil."
            );
            return;
        }

        String fertileBlockId = MghgFarmPerkManager.getPrimaryFertileBaseBlockId();
        if (!setBlockIfValid(world, targetBlock, fertileBlockId)) {
            return;
        }
        MghgFarmPerkManager.trackFertileBlock(parcel, key);
        playTillWorldSound(world, targetBlock);
    }

    @Override
    protected void simulateInteractWithBlock(
            @Nonnull InteractionType interactionType,
            @Nonnull InteractionContext interactionContext,
            @Nullable ItemStack itemStack,
            @Nonnull World world,
            @Nonnull Vector3i vector3i
    ) {
        // Server-authoritative behavior only.
    }

    private boolean setBlockIfValid(@Nonnull World world, @Nonnull Vector3i pos, @Nullable String blockId) {
        if (blockId == null || blockId.isBlank()) {
            return false;
        }
        if (BlockType.getAssetMap().getIndex(blockId) == Integer.MIN_VALUE) {
            return false;
        }
        world.setBlock(pos.x, pos.y, pos.z, blockId);
        return true;
    }

    private void playTillWorldSound(@Nonnull World world, @Nonnull Vector3i pos) {
        int soundIndex = SoundEvent.getAssetMap().getIndex(TILL_WORLD_SOUND_EVENT_ID);
        if (soundIndex == Integer.MIN_VALUE) {
            return;
        }
        SoundUtil.playSoundEvent3d(
                soundIndex,
                SoundCategory.SFX,
                pos.x + 0.5,
                pos.y + 0.5,
                pos.z + 0.5,
                world.getEntityStore().getStore()
        );
    }

    private boolean canBuildInParcel(@Nonnull World world, @Nonnull MghgParcel parcel, @Nullable PlayerRef player) {
        if (player == null) {
            return false;
        }
        UUID playerId = player.getUuid();
        UUID ownerId = MghgFarmWorldManager.getOwnerFromFarmWorld(world);
        boolean isOwner = ownerId != null && ownerId.equals(playerId);
        return isOwner || MghgParcelAccess.canBuild(parcel, playerId);
    }

    private @Nullable PlayerRef resolvePlayerRef(@Nonnull World world, @Nonnull InteractionContext context) {
        Ref<EntityStore> ref = context.getEntity();
        if (ref == null || !ref.isValid()) {
            return null;
        }
        return world.getEntityStore().getStore().getComponent(ref, PlayerRef.getComponentType());
    }

    private void sendMessageWithCooldown(
            @Nullable PlayerRef player,
            @Nonnull MghgChat.Channel channel,
            @Nonnull String body
    ) {
        if (player == null) {
            return;
        }
        long now = System.currentTimeMillis();
        UUID playerId = player.getUuid();
        Long previous = LAST_MESSAGE_AT.get(playerId);
        if (previous != null && now - previous < MESSAGE_COOLDOWN_MILLIS) {
            return;
        }
        LAST_MESSAGE_AT.put(playerId, now);
        MghgChat.toPlayer(player, channel, body);
    }
}
