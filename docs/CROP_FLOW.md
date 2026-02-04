MGHG Crop Flow (Implementation Notes)
====================================

Goal
----
Provide a single, predictable pipeline for crop data (size/rarity/climate) and
ensure visuals update immediately when mutation changes, without waiting for
growth stage ticks.

Single Source of Truth (Config)
-------------------------------
Mutation rules now live in the asset folder:
`Server/Farming/Mutations/Mghg_Mutations.json`

Size + seed configuration lives in:
`build/resources/main/Server/Farming/Modifiers/Size.json`
(Keep src/main/resources in sync when you want changes to persist in source.)

Key fields used by runtime systems:
- SizeMin / SizeMax
- InitialRarityGoldChance
- InitialRarityRainbowChance
- MutationRollCooldownSeconds (default cooldown if a rule omits CooldownSeconds)
- DropListOverrideFrom / DropListOverrideTo
- ExtraDropListsAll / ExtraDropListsGold / ExtraDropListsRainbow
Fallback-only fields (used ONLY if the mutation rules asset is empty/unavailable):
- RainWeathers / SnowWeathers / FrozenWeathers
- MutationChanceRain / MutationChanceSnow / MutationChanceFrozen

Runtime behavior:
- Mutation rules are loaded on plugin start via `MghgMutationRules.reload()`.
- Size + seed config is loaded via `MghgCropGrowthModifierAsset.reloadFromDisk()`.
- Hot reload is supported with `/crop reload` (see Debug Commands).

Data Flow Overview
------------------
1) Plant (new crop block)
   - `MghgOnFarmBlockAddedSystem` runs when a FarmingBlock entity is created.
   - It seeds MGHG data using `MghgCropDataSeeder` (size/rarity/climate).
   - Seeder reads values from `Size.json` (via GrowthModifier asset).

2) Growth / Mutation tick
   - `MghgMatureCropMutationTickingSystem` runs every tick on crop entities.
   - It:
     - Resolves the current weather id via `MghgWeatherResolver`.
     - Builds a `MghgMutationContext` (mature/online/weather/adjacent).
     - Applies rule engine (`MghgMutationRules` + `MghgMutationEngine`).
     - Immediately updates visuals via `MghgCropStageSync` (no waiting for next growth stage).
   - Rules are per-slot (CLIMATE/LUNAR/RARITY) with priority + cooldown.

3) Visual sync
   - `MghgCropStageSync` switches the stage set to the correct MGHG variant.
   - It applies the block state directly using the base BlockType, which avoids
     NPEs from BlockType state assets.
   - Fallback: schedules a farming tick if state apply fails.

4) Harvest (StageFinal)
   - `MghgHarvestCropInteraction` produces item drops with metadata from
     `MghgHarvestUtil`.

5) Item metadata preservation
   - `MghgPreserveCropMetaOnBreakSystem` ensures placed crops drop with metadata.
   - `MghgRehydrateCropDataOnPlaceSystem` applies metadata back onto the placed block.

Drops (Override + Extras)
-------------------------
- Base droplists are resolved from the block's Gathering config.
- You can override droplist IDs without editing every block state using:
  - `DropListOverrideFrom` + `DropListOverrideTo` (parallel arrays).
  - Example: map `Drops_Plant_Crop_Lettuce_StageFinal_Harvest` -> `MGHG_Drops_Lettuce_StageFinal_Harvest`.
- You can append extra drops by rarity:
  - `ExtraDropListsAll` applies to every crop.
  - `ExtraDropListsGold` applies only to GOLD.
  - `ExtraDropListsRainbow` applies only to RAINBOW.
- All fields live in `Size.json`, so changes can be reloaded at runtime.

Code Map (Responsibilities)
---------------------------
- MghgCropGrowthModifierAsset
  - Holds config values (Size/Mutation/Weather).
  - Exposes getters for runtime systems.

- MghgCropDataSeeder
  - Single place to seed size/rarity/climate.
  - Used by new plant and replant paths.

- MghgMatureCropMutationTickingSystem
  - Performs climate mutation and immediate visual refresh.

- MghgCropStageSync
  - Applies the correct state set instantly for visuals.

- MghgHarvestUtil
  - Converts crop data to item metadata on harvest.

Adding New Crop Content (Checklist)
-----------------------------------
0) Register the crop in `build/resources/main/Server/Farming/Crops/Mghg_Crops.json`
   - The plugin only touches crops listed here (vanilla is untouched).
1) Block states
   - Add state definitions for each stage:
     mghg_none_stage1, mghg_none_stage2, mghg_none_stagefinal, etc.
   - Add equivalent states for rain/snow/frozen (and rarity if used).

2) Farming stage sets
   - Add stage sets named exactly to match `MghgCropVisualStateResolver`.
   - Example: mghg_none, mghg_rain, mghg_none_dawnlit, mghg_gold_rain.
   - Each stage entry should point to the correct state key.

3) Growth modifiers
   - Ensure the block uses "MGHG_CropGrowth" in ActiveGrowthModifiers.

4) Interactions
   - StageFinal must include MGHG harvest interactions (Primary/Secondary/Use).

5) Item states
   - Add item states matching variant keys (mghg_none, mghg_rain, etc).
   - Keep item states aligned with `MghgCropVisualStateResolver`.

6) Item identification (plugin-specific)
   - MGHG items should include tag: `"MGHG": ["CropItem"]`.
   - Harvest logic only applies metadata to:
     - the primary harvest itemId, or
     - items that support `mghg_*` states.

Debug Commands
--------------
- /crop reload [all|crops|mutations|growth]
  Reloads crop registry, mutation rules, and/or Size.json without a restart.
- /crop add mutation --name=rain|snow|frozen|none
  Forces mutation on the targeted crop and immediately refreshes visuals.
- /crop add lunar --name=dawnlit|dawnbound|amberlit|amberbound|none
  Fuerza la mutacion lunar en el crop objetivo.
- /crop add rarity --name=gold|rainbow|none
  Forces rarity on the targeted crop (use for testing rules).
- /crop set size --value=<int>
  Sets the Size value on the targeted MGHG crop.
- /crop debug rules
  Prints the currently loaded mutation rules summary.
