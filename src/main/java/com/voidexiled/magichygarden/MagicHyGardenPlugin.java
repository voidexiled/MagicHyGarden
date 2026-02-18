package com.voidexiled.magichygarden;

import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.builtin.adventure.farming.FarmingPlugin;
import com.hypixel.hytale.builtin.adventure.farming.states.FarmingBlock;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.HytaleAssetStore;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.farming.GrowthModifierAsset;
import com.hypixel.hytale.server.core.asset.type.weather.config.Weather;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.voidexiled.magichygarden.commands.crop.CropCommand;
import com.voidexiled.magichygarden.commands.farm.FarmCommand;
import com.voidexiled.magichygarden.features.farming.components.MghgCropData;
import com.voidexiled.magichygarden.features.farming.economy.MghgEconomyManager;
import com.voidexiled.magichygarden.features.farming.events.MghgFarmEventScheduler;
import com.voidexiled.magichygarden.features.farming.interactions.MghgHarvestCropInteraction;
import com.voidexiled.magichygarden.features.farming.interactions.MghgHoeTillInteraction;
import com.voidexiled.magichygarden.features.farming.modifiers.MghgCropGrowthModifierAsset;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcelManager;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcelInviteService;
import com.voidexiled.magichygarden.features.farming.perks.MghgFertileSoilReconcileService;
import com.voidexiled.magichygarden.features.farming.perks.MghgFarmPerkManager;
import com.voidexiled.magichygarden.features.farming.shop.MghgShopStockManager;
import com.voidexiled.magichygarden.features.farming.shop.MghgShopUiLogManager;
import com.voidexiled.magichygarden.features.farming.storage.MghgPlayerNameManager;
import com.voidexiled.magichygarden.features.farming.tooltips.MghgDynamicTooltipsManager;
import com.voidexiled.magichygarden.features.farming.worlds.MghgFarmWorldManager;
import com.voidexiled.magichygarden.features.farming.systems.MghgApplyCropMetaOnItemSpawnSystem;
import com.voidexiled.magichygarden.features.farming.systems.MghgCropInspectHudSystem;
import com.voidexiled.magichygarden.features.farming.systems.MghgFertileSoilBreakSystem;
import com.voidexiled.magichygarden.features.farming.systems.MghgFertileSoilPlaceGuardSystem;
import com.voidexiled.magichygarden.features.farming.systems.MghgFertileSoilUsePostSystem;
import com.voidexiled.magichygarden.features.farming.systems.MghgFertileSoilUsePreSystem;
import com.voidexiled.magichygarden.features.farming.systems.MghgFarmShopHudRefreshSystem;
import com.voidexiled.magichygarden.features.farming.systems.MghgPreserveCropMetaOnBreakSystem;
import com.voidexiled.magichygarden.features.farming.systems.MghgMatureCropMutationTickingSystem;
import com.voidexiled.magichygarden.features.farming.systems.MghgOnFarmBlockAddedSystem;
import com.voidexiled.magichygarden.features.farming.systems.MghgParcelBreakGuardSystem;
import com.voidexiled.magichygarden.features.farming.systems.MghgParcelPlaceGuardSystem;
import com.voidexiled.magichygarden.features.farming.systems.MghgParcelUseGuardSystem;
import com.voidexiled.magichygarden.features.farming.systems.MghgRehydrateCropDataOnPlaceSystem;
import com.voidexiled.magichygarden.features.farming.systems.MghgShopBenchUseSystem;
import com.voidexiled.magichygarden.features.farming.registry.MghgCropRegistry;
import com.voidexiled.magichygarden.features.farming.state.MghgMutationRules;
import com.voidexiled.magichygarden.features.farming.state.MghgMutationRulesAsset;
import com.voidexiled.magichygarden.features.farming.state.MghgParticleTracker;

public class MagicHyGardenPlugin extends JavaPlugin {
    private static MagicHyGardenPlugin INSTANCE;

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // Component Types
    private ComponentType<ChunkStore, MghgCropData> mghgCropDataComponentType;
    // Constructors
    public MagicHyGardenPlugin(JavaPluginInit init) {
        super(init);
        INSTANCE = this;
        LOGGER.atInfo().log("Hello from %s version %s", this.getName(), this.getManifest().getVersion().toString());
    }

    // Override Methods
    @Override
    protected void setup() {
        this.getCodecRegistry(Interaction.CODEC)
                .register("MGHG_HarvestCrop", MghgHarvestCropInteraction.class, MghgHarvestCropInteraction.CODEC);
        this.getCodecRegistry(Interaction.CODEC)
                .register("MGHG_HoeTill", MghgHoeTillInteraction.class, MghgHoeTillInteraction.CODEC);

        this.getAssetRegistry().register(
                HytaleAssetStore.builder(MghgMutationRulesAsset.class, new DefaultAssetMap<>())
                        .setPath("Farming/Mutations")
                        .setCodec(MghgMutationRulesAsset.CODEC)
                        .setKeyFunction(MghgMutationRulesAsset::getId)
                        .loadsAfter(Weather.class)
                        .build()
        );

        // Register Commands
        this.getCommandRegistry().registerCommand(new CropCommand());
        this.getCommandRegistry().registerCommand(new FarmCommand());

        // Register Component Codecs
        this.mghgCropDataComponentType = this.getChunkStoreRegistry()
                .registerComponent(MghgCropData.class, "MGHG_CropData", MghgCropData.CODEC);
        getCodecRegistry(GrowthModifierAsset.CODEC)
                .register("MGHG_CropGrowth", MghgCropGrowthModifierAsset.class, MghgCropGrowthModifierAsset.CODEC);
    }

    @Override
    protected void start() {
        MghgParcelManager.load();
        MghgParcelInviteService.start();
        MghgFarmPerkManager.load();
        MghgFertileSoilReconcileService.start();
        MghgFarmWorldManager.load();
        MghgEconomyManager.load();
        MghgPlayerNameManager.load();
        MghgShopUiLogManager.load();
        FarmingPlugin farmingPlugin =
                FarmingPlugin.get();

        if (farmingPlugin == null) {
            LOGGER.atWarning().log("FarmingPlugin.get() es null en start(); no se registrará MghgOnFarmBlockAddedSystem.");
            return;
        }

        ComponentType<ChunkStore, FarmingBlock> farmingBlockType =
                farmingPlugin.getFarmingBlockComponentType();

        MghgCropRegistry.reload();
        MghgCropGrowthModifierAsset.reloadFromDisk();
        MghgMutationRules.reload();
        MghgFarmEventScheduler.start();
        MghgShopStockManager.start();
        MghgDynamicTooltipsManager.tryRegister();
        MghgDynamicTooltipsManager.refreshAllPlayers();

        // Farm Block Added System ChunkStore
        // minSize, maxSize, goldChance, rainbowChance
        // chances when farm block is added
        this.getChunkStoreRegistry().registerSystem(
                new MghgOnFarmBlockAddedSystem(
                        farmingBlockType,
                        this.mghgCropDataComponentType
                )
        );

        // Parcel protection (place/break) inside farm worlds.
        this.getEntityStoreRegistry().registerSystem(new MghgParcelPlaceGuardSystem());
        this.getEntityStoreRegistry().registerSystem(new MghgParcelBreakGuardSystem());
        this.getEntityStoreRegistry().registerSystem(new MghgParcelUseGuardSystem());
        this.getEntityStoreRegistry().registerSystem(new MghgFertileSoilPlaceGuardSystem());
        this.getEntityStoreRegistry().registerSystem(new MghgFertileSoilUsePreSystem());
        this.getEntityStoreRegistry().registerSystem(new MghgFertileSoilUsePostSystem());
        this.getEntityStoreRegistry().registerSystem(new MghgFertileSoilBreakSystem());
        this.getEntityStoreRegistry().registerSystem(new MghgShopBenchUseSystem());

        // 2) 🔥 IMPORTANT: rehydrate MGHG_Crop metadata onto placed blocks (decorative crop items)
        this.getEntityStoreRegistry().registerSystem(new MghgRehydrateCropDataOnPlaceSystem(
                this.mghgCropDataComponentType
        ));

        // Preserve MGHG metadata when breaking placed blocks (decorative crop items)
        this.getEntityStoreRegistry().registerSystem(new MghgPreserveCropMetaOnBreakSystem(
                this.mghgCropDataComponentType
        ));

        // Apply MGHG metadata to item entities spawned by physics (support breaks, etc.)
        this.getEntityStoreRegistry().registerSystem(new MghgApplyCropMetaOnItemSpawnSystem());

        // Crop inspect HUD (bottom-left Blocchio style)
        this.getEntityStoreRegistry().registerSystem(new MghgCropInspectHudSystem());
        this.getEntityStoreRegistry().registerSystem(new MghgFarmShopHudRefreshSystem());

        this.getChunkStoreRegistry().registerSystem(
                new MghgMatureCropMutationTickingSystem(
                        farmingBlockType,
                        this.mghgCropDataComponentType
                )
        );

        // Track runtime particles via outbound packets (for mutation rules).
        MghgParticleTracker.start();

    }

    @Override
    protected void shutdown() {
        MghgFertileSoilReconcileService.stop();
        MghgFarmWorldManager.shutdown();
        MghgParcelInviteService.stop();
        MghgParcelManager.save();
        MghgEconomyManager.save();
        MghgPlayerNameManager.save();
        MghgShopUiLogManager.save();
        MghgFarmEventScheduler.stop();
        MghgParticleTracker.stop();
        MghgShopStockManager.stop();
    }


    // Methods

    public ComponentType<ChunkStore, MghgCropData> getMghgCropDataComponentType() {
        return mghgCropDataComponentType;
    }

    public static MagicHyGardenPlugin get() {
        return INSTANCE;
    }
}
