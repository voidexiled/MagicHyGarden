package com.voidexiled.magichygarden;

import com.hypixel.hytale.builtin.adventure.farming.FarmingPlugin;
import com.hypixel.hytale.builtin.adventure.farming.states.FarmingBlock;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.farming.GrowthModifierAsset;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.voidexiled.magichygarden.commands.crop.CropCommand;
import com.voidexiled.magichygarden.features.farming.components.MghgCropData;
import com.voidexiled.magichygarden.features.farming.interactions.MghgHarvestCropInteraction;
import com.voidexiled.magichygarden.features.farming.modifiers.MghgCropGrowthModifierAsset;
import com.voidexiled.magichygarden.features.farming.systems.MghgApplyCropMetaOnItemSpawnSystem;
import com.voidexiled.magichygarden.features.farming.systems.MghgPreserveCropMetaOnBreakSystem;
import com.voidexiled.magichygarden.features.farming.systems.MghgMatureCropMutationTickingSystem;
import com.voidexiled.magichygarden.features.farming.systems.MghgOnFarmBlockAddedSystem;
import com.voidexiled.magichygarden.features.farming.systems.MghgRehydrateCropDataOnPlaceSystem;
import com.voidexiled.magichygarden.features.farming.registry.MghgCropRegistry;
import com.voidexiled.magichygarden.features.farming.state.MghgMutationRules;

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

        // Register Commands
        this.getCommandRegistry().registerCommand(new CropCommand());

        // Register Component Codecs
        this.mghgCropDataComponentType = this.getChunkStoreRegistry()
                .registerComponent(MghgCropData.class, "MGHG_CropData", MghgCropData.CODEC);
        getCodecRegistry(GrowthModifierAsset.CODEC)
                .register("MGHG_CropGrowth", MghgCropGrowthModifierAsset.class, MghgCropGrowthModifierAsset.CODEC);
    }

    @Override
    protected void start() {
        FarmingPlugin farmingPlugin =
                FarmingPlugin.get();

        if (farmingPlugin == null) {
            LOGGER.atWarning().log("FarmingPlugin.get() es null en start(); no se registrará MghgOnFarmBlockAddedSystem.");
            return;
        }

        ComponentType<ChunkStore, FarmingBlock> farmingBlockType =
                farmingPlugin.getFarmingBlockComponentType();

        MghgCropRegistry.reload();
        MghgMutationRules.reload();

        // Farm Block Added System ChunkStore
        // minSize, maxSize, goldChance, rainbowChance
        // chances when farm block is added
        this.getChunkStoreRegistry().registerSystem(
                new MghgOnFarmBlockAddedSystem(
                        farmingBlockType,
                        this.mghgCropDataComponentType
                )
        );

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

        this.getChunkStoreRegistry().registerSystem(
                new MghgMatureCropMutationTickingSystem(
                        farmingBlockType,
                        this.mghgCropDataComponentType
                )
        );


    }


    // Methods

    public ComponentType<ChunkStore, MghgCropData> getMghgCropDataComponentType() {
        return mghgCropDataComponentType;
    }

    public static MagicHyGardenPlugin get() {
        return INSTANCE;
    }
}
