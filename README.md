# Hytale Plugin Template

A template for Hytale java plugins. Created by [Up](https://github.com/UpcraftLP), and slightly modified by Kaupenjoe. 

## Project docs
- `docs/FARM_SERVER_SYSTEMS.md`: Active farm systems and runtime behavior.
- `docs/FARM_CONFIG_REFERENCE.md`: Field-by-field reference for all `Server/Farming/*` JSON configs.
- `docs/COMMAND_REFERENCE.md`: `/farm` and `/crop` command reference with examples.
- `docs/MUTATION_RULES.md`: Complete mutation rules guide.
- `docs/PERSISTENCE_PATHS.md`: Runtime persistence and storage paths.

### Configuring the Template
If you for example installed the game in a non-standard location, you will need to tell the project about that.
The recommended way is to create a file at `%USERPROFILE%/.gradle/gradle.properties` to set these properties globally.

```properties
# Set a custom game install location
hytale.install_dir=path/to/Hytale

# Speed up the decompilation process significantly, by only including the core hytale packages.
# Recommended if decompiling the game takes a very long time on your PC.
hytale.decompile_partial=true
```
