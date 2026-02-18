ASSET NORMALIZATION (MGHG)

Goal
- Keep item state assets consistent (sounds, BlockType defaults) even when you add lots of mutations.
- Let you define per-mutation visual/sound overrides in one place.

Files
- tools/mghg_asset_normalize.py
  - Generator that merges BlockType defaults into every Item state.
- Server/Farming/Visuals/Mghg_MutationVisuals.json
  - Central place for defaults and per-mutation overrides.

How it works
- For each crop Item asset with a "State" dictionary:
  - Start from the root BlockType (base).
  - Apply Defaults.BlockType.
  - Apply the state’s own BlockType.
  - Apply Overrides[stateKey].BlockType (if present).
  - Write the merged BlockType back into that state.

This prevents missing fields like BlockSoundSetId / InteractionSoundEventId in mutated states.

How to run
1) Run the generator after you add or edit mutation states:
   python tools/mghg_asset_normalize.py

2) It updates both:
   - src/main/resources
   - build/resources/main

Customizing per-mutation overrides
Edit Server/Farming/Visuals/Mghg_MutationVisuals.json:

{
  "Defaults": {
    "BlockType": {
      "BlockSoundSetId": "Plant",
      "InteractionSoundEventId": "SFX_Plant_Break"
    }
  },
  "Overrides": {
    "mghg_gold": {
      "BlockType": {
        "ParticleColor": "#f6d365"
      }
    }
  }
}

Notes
- The generator only touches Item assets with a "State" map.
- It is safe to re-run anytime; it is idempotent.
- If you add new crops, make sure their Item asset has a root BlockType so defaults can merge cleanly.
