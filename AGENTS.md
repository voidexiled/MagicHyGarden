# Repository Guidelines

## Project Structure & Module Organization
- `src/main/java/com/voidexiled/magichygarden/…` contains all plugin code. Key areas: `features/farming` (systems, state, registry, visuals, UI), `commands`, and `utils`.
- `src/main/resources` holds source assets/configs. Important paths:
  - `Server/Farming/Mutations`, `Server/Farming/Crops`, `Server/Farming/Modifiers`
  - `Server/Item/Items`, `Server/Particles`, `Server/Languages`
  - `Common/UI/Custom` for HUD/UI assets.
- `build/resources/main` is what the server loads at runtime. Keep it in sync with `src/main/resources` for persistent changes.
- `AssetsVanilla/` is a local reference copy of vanilla assets (UI, textures, etc.).
- `docs/` contains implementation notes and workflows.
- `run/` contains logs and runtime data (e.g., `run/logs`).

## Build, Test, and Development Commands
- `./gradlew build` — compile the plugin and process resources.
- `./gradlew clean` — clean build outputs.
- `./gradlew test` — run tests if/when they exist.
- Logs: check `run/logs/*_server.log` for runtime issues.

## Coding Style & Naming Conventions
- Java: 4‑space indentation, K&R braces, `UpperCamelCase` for classes, `lowerCamelCase` for methods/fields.
- Prefix MagicHyGarden classes with `Mghg` (e.g., `MghgCropRegistry`).
- Asset/state ids follow existing patterns: `Plant_Crop_*`, `mghg_*` variant keys.
- Prefer small, focused systems; keep shared logic in `features/farming/logic` or `state`.

## Testing Guidelines
- No automated test suite is currently enforced. Validate in-game using:
  - `/crop debug target`, `/crop debug held`, `/crop debug rules`
  - `run/logs` for warnings and errors.
- When adding tests, use Gradle and document how to run them.

## Commit & Pull Request Guidelines
- Recent history uses descriptive commits (e.g., “Fifth Commit … – Funcional …”). Keep messages short, outcome‑focused, and scoped (feature or fix).
- PRs should include:
  - A brief summary of behavior changes
  - Steps to test (commands + expected results)
  - Screenshots for UI/visual changes
  - Links to related issues/tasks if applicable

## Assets & Config Workflow
- Runtime reads from `build/resources/main`. If you edit assets in `src/main/resources`, mirror changes into `build/resources/main`.
- Prefer `/crop reload` to hot‑reload crops/mutations/modifiers during balancing.


## You must ensure that everything is always well documented and specified.
## You can found vanilla assets in `AssetsVanilla/` and you can use them as reference for your work.
## Hytale Modding Developer Documentation in `hytalemodding-docs/content/`